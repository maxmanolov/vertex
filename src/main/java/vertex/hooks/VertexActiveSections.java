package vertex.hooks;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.launchwrapper.LogWrapper;
import org.apache.logging.log4j.Level;
import vertex.Mappings;
import vertex.api.ActiveSection;

/** Client-thread registry of built sections that carry at least one render pass. */
public final class VertexActiveSections
{
    /**
     * Floor for swapping in the compact array. Vanilla's occlusion path opens with an
     * unclamped prefix over the nearest sixteen entries - sortAndRender calls the query
     * helper a(0,16), marks u[0..15], then renders that range, all without consulting
     * u.length; only from the second stride onward does it clamp the bound to
     * arraylength (bma.a(Lsv;ID)I offsets 525-537 and 617-632). Sixteen is therefore the
     * true contract; this keeps a deliberate margin above it.
     */
    private static final int MIN_RENDERERS = 27;
    private static final List<Object> active = new ArrayList<Object>();
    private static final IdentityHashMap<Object, Boolean> members = new IdentityHashMap<Object, Boolean>();
    private static Object renderGlobal;
    private static Class<?> rendererType;
    private static Object[] snapshot;
    private static boolean snapshotDirty;
    private static boolean disabled;

    private static Field rgMinecraft;
    private static Field mcViewEntity;
    private static Field entityX;
    private static Field entityY;
    private static Field entityZ;
    private static double orderX;
    private static double orderY;
    private static double orderZ;
    private static boolean orderKnown;

    /** loadRenderers tail: the new grid starts empty and publishes sections after builds. */
    public static void reset(Object owner)
    {
        if (disabled)
        {
            return;
        }

        try
        {
            Field sorted = field(owner.getClass(), Mappings.RG_SORTED_RENDERERS);
            Object array = sorted.get(owner);

            if (array == null || !array.getClass().isArray())
            {
                throw new IllegalStateException("sorted renderer array is unavailable");
            }

            renderGlobal = owner;
            rendererType = array.getClass().getComponentType();
            rgMinecraft = field(owner.getClass(), Mappings.RG_MC);
            mcViewEntity = field(rgMinecraft.getType(), Mappings.MINECRAFT_RENDER_VIEW_ENTITY);
            Class<?> entity = mcViewEntity.getType();

            while (entity.getSuperclass() != Object.class)
            {
                entity = entity.getSuperclass();
            }

            entityX = field(entity, Mappings.ENTITY_POS_X);
            entityY = field(entity, Mappings.ENTITY_POS_Y);
            entityZ = field(entity, Mappings.ENTITY_POS_Z);
            active.clear();
            members.clear();
            snapshot = emptyArray();
            snapshotDirty = false;
            orderKnown = false;
        }
        catch (Exception e)
        {
            disable("grid reset", e);
        }
    }

    /** Returns the exact game-array type consumed by RenderGlobal, or null for fallback. */
    public static Object[] snapshot(Object owner)
    {
        if (disabled || renderGlobal != owner || rendererType == null)
        {
            return null;
        }

        // sortAndRender updates the nearest 27 renderers without consulting the
        // array length. Keep using the complete vanilla arrays during startup or
        // a world transition until the compact view can satisfy that contract.
        if (active.size() < MIN_RENDERERS)
        {
            return null;
        }

        if (snapshotDirty)
        {
            Object[] rebuilt = (Object[])Array.newInstance(rendererType, active.size());
            active.toArray(rebuilt);
            snapshot = rebuilt;
            snapshotDirty = false;
        }

        return snapshot;
    }

    /** Original updateRenderer tail, or the client-thread worker-install completion. */
    public static void built(Object renderer)
    {
        if (disabled || renderGlobal == null || VertexMulticore.isWorkerBuild()
            || !(renderer instanceof ActiveSection))
        {
            return;
        }

        ActiveSection section = (ActiveSection)renderer;

        try
        {
            if (section.vertex$hasMesh())
            {
                add(renderer, section);
            }
            else
            {
                remove(renderer);
            }
        }
        catch (Exception e)
        {
            disable("build publication", e);
        }
    }

    /** setPosition head: retire only when the renderer is actually changing sections. */
    public static void beforeReposition(Object renderer, int x, int y, int z)
    {
        if (disabled || !(renderer instanceof ActiveSection))
        {
            return;
        }

        ActiveSection section = (ActiveSection)renderer;

        if (section.vertex$centerX() != x + 8 || section.vertex$centerY() != y + 8
            || section.vertex$centerZ() != z + 8)
        {
            remove(renderer);
        }
    }

    /**
     * Post-sort notification from {@link VertexRenderOrder#sort}, which owns the single
     * Arrays.sort anchor in the renderer. Only a sort of the published snapshot changes
     * the registry order; the grid-load sort of vanilla's own array is a no-op here.
     */
    public static void onSorted(Object[] values)
    {
        if (!disabled && values == snapshot && renderGlobal != null)
        {
            active.clear();
            Collections.addAll(active, values);
            recordCamera();
        }
    }

    private static void add(Object renderer, ActiveSection section) throws IllegalAccessException
    {
        if (members.containsKey(renderer))
        {
            return;
        }

        double[] camera = camera();

        if (camera == null)
        {
            active.add(renderer);
        }
        else
        {
            if (!orderKnown || distanceSquared(camera[0], camera[1], camera[2], orderX, orderY, orderZ) > 1.0D)
            {
                orderX = camera[0];
                orderY = camera[1];
                orderZ = camera[2];
                orderKnown = true;
                Collections.sort(active, new DistanceComparator(orderX, orderY, orderZ));
            }

            double distance = distance(section, orderX, orderY, orderZ);
            int low = 0;
            int high = active.size();

            while (low < high)
            {
                int middle = (low + high) >>> 1;
                double middleDistance = distance((ActiveSection)active.get(middle), orderX, orderY, orderZ);

                if (middleDistance <= distance)
                {
                    low = middle + 1;
                }
                else
                {
                    high = middle;
                }
            }

            active.add(low, renderer);
        }

        members.put(renderer, Boolean.TRUE);
        snapshotDirty = true;
    }

    private static void remove(Object renderer)
    {
        if (members.remove(renderer) == null)
        {
            return;
        }

        for (int i = 0; i < active.size(); ++i)
        {
            if (active.get(i) == renderer)
            {
                active.remove(i);
                snapshotDirty = true;
                return;
            }
        }
    }

    private static void recordCamera()
    {
        try
        {
            double[] camera = camera();

            if (camera != null)
            {
                orderX = camera[0];
                orderY = camera[1];
                orderZ = camera[2];
                orderKnown = true;
            }
        }
        catch (Exception ignored)
        {
            orderKnown = false;
        }
    }

    private static double[] camera() throws IllegalAccessException
    {
        Object minecraft = rgMinecraft.get(renderGlobal);
        Object view = minecraft == null ? null : mcViewEntity.get(minecraft);

        if (view == null)
        {
            return null;
        }

        return new double[] {entityX.getDouble(view), entityY.getDouble(view), entityZ.getDouble(view)};
    }

    private static double distance(ActiveSection section, double x, double y, double z)
    {
        return distanceSquared(section.vertex$centerX(), section.vertex$centerY(), section.vertex$centerZ(), x, y, z);
    }

    private static double distanceSquared(double ax, double ay, double az, double bx, double by, double bz)
    {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Object[] emptyArray()
    {
        return (Object[])Array.newInstance(rendererType, 0);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException
    {
        Class<?> type = owner;

        while (type != null)
        {
            try
            {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            }
            catch (NoSuchFieldException ignored)
            {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static void disable(String phase, Exception error)
    {
        if (!disabled)
        {
            disabled = true;
            active.clear();
            members.clear();
            snapshot = null;
            LogWrapper.log(Level.WARN, error,
                "[Vertex] Active-section registry disabled during %s; using the full renderer grid", phase);
        }
    }

    private static final class DistanceComparator implements Comparator<Object>
    {
        private final double x;
        private final double y;
        private final double z;

        DistanceComparator(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int compare(Object left, Object right)
        {
            return Double.compare(distance((ActiveSection)left, x, y, z),
                distance((ActiveSection)right, x, y, z));
        }
    }

    private VertexActiveSections()
    {
    }
}
