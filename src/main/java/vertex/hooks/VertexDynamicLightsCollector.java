package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;
import vertex.lights.DynamicSourceTracker;

/**
 * Publishes dynamic light sources each frame: every player's held item whose block form
 * emits light becomes a source at the player's position. Movement, appearance and
 * disappearance re-mark the affected chunk sections through the gate-free promote path,
 * bounded by the tracker's remark cap and the existing per-frame consumption cap.
 * Dropped items and burning entities are a documented follow-up. Self-disables on any
 * failure, like every hook.
 */
public final class VertexDynamicLightsCollector
{
    private static final DynamicSourceTracker tracker = new DynamicSourceTracker();
    private static boolean initialized = false;
    private static boolean disabled = false;
    private static int tickCounter = 0;

    private static Field theWorld;
    private static Field renderGlobal;
    private static Field playerEntities;
    private static Method getHeldItem;
    private static Method getItem;
    private static Method getBlockFromItem;
    private static Method getLightValue;
    private static Field posX;
    private static Field posY;
    private static Field posZ;

    public static void tick(Object minecraft)
    {
        if (disabled || !VertexConfig.enabled("dynamicLights"))
        {
            return;
        }

        if (++tickCounter % 4 != 0)
        {
            return;
        }

        try
        {
            if (!initialized)
            {
                initialize(minecraft);
            }

            Object world = theWorld.get(minecraft);

            if (world == null)
            {
                publish(minecraft, new int[0]);
                return;
            }

            List<?> players = (List<?>)playerEntities.get(world);
            List<int[]> sources = new ArrayList<int[]>();

            for (Object player : players)
            {
                Object held = getHeldItem.invoke(player);

                if (held != null)
                {
                    Object item = getItem.invoke(held);

                    if (item != null)
                    {
                        Object block = getBlockFromItem.invoke(null, item);

                        if (block != null)
                        {
                            int level = ((Integer)getLightValue.invoke(block)).intValue();

                            if (level > 0)
                            {
                                sources.add(new int[] {
                                    (int)Math.floor(posX.getDouble(player)),
                                    (int)Math.floor(posY.getDouble(player)),
                                    (int)Math.floor(posZ.getDouble(player)),
                                    level});
                            }
                        }
                    }
                }
            }

            int[] snapshot = new int[sources.size() * 4];

            for (int i = 0; i < sources.size(); ++i)
            {
                System.arraycopy(sources.get(i), 0, snapshot, i * 4, 4);
            }

            publish(minecraft, snapshot);
        }
        catch (Exception e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Dynamic light collector disabled after failure");
            e.printStackTrace();
        }
    }

    private static void publish(Object minecraft, int[] snapshot) throws Exception
    {
        VertexDynamicLights.publish(snapshot);
        int[] remarks = tracker.update(snapshot);

        if (remarks.length > 0)
        {
            Object rg = renderGlobal.get(minecraft);

            if (rg != null)
            {
                for (int i = 0; i < remarks.length; i += 3)
                {
                    VertexHooks.promoteForTest(rg, remarks[i], remarks[i + 1], remarks[i + 2]);
                }
            }
        }
    }

    private static void initialize(Object minecraft) throws Exception
    {
        Class<?> mc = minecraft.getClass();
        theWorld = accessible(mc, Mappings.MC_THE_WORLD);
        renderGlobal = accessible(mc, Mappings.MC_RENDER_GLOBAL);
        Class<?> world = theWorld.getType();
        playerEntities = accessible(world, Mappings.WORLD_PLAYER_ENTITIES);
        Class<?> livingBase = Class.forName(Mappings.ENTITY_LIVING_BASE, false, minecraft.getClass().getClassLoader());
        getHeldItem = livingBase.getMethod(Mappings.ELB_GET_HELD_ITEM);
        Class<?> itemStack = getHeldItem.getReturnType();
        getItem = itemStack.getMethod(Mappings.STACK_GET_ITEM);
        Class<?> block = Class.forName(Mappings.BLOCK, false, minecraft.getClass().getClassLoader());
        getBlockFromItem = block.getMethod(Mappings.BLOCK_FROM_ITEM, getItem.getReturnType());
        getLightValue = block.getMethod(Mappings.BLOCK_GET_LIGHT_VALUE);
        Class<?> entityRoot = livingBase;

        while (entityRoot.getSuperclass() != Object.class)
        {
            entityRoot = entityRoot.getSuperclass();
        }

        posX = accessible(entityRoot, Mappings.ENTITY_POS_X);
        posY = accessible(entityRoot, Mappings.ENTITY_POS_Y);
        posZ = accessible(entityRoot, Mappings.ENTITY_POS_Z);
        initialized = true;
        LogWrapper.info("[Vertex] Dynamic light collector armed");
    }

    /** Hierarchy-walking field lookup: runtime types are often subclasses (WorldClient vs World). */
    private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException
    {
        for (Class<?> cls = owner; cls != Object.class && cls != null; cls = cls.getSuperclass())
        {
            try
            {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException next)
            {
            }
        }

        throw new NoSuchFieldException(name + " in hierarchy of " + owner.getName());
    }

    private VertexDynamicLightsCollector()
    {
    }
}
