package vertex.hooks;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;
import vertex.api.ImmediateMarker;

/**
 * Runtime side of the render-priority optimization. A block change within the local
 * player's interaction reach promotes its containing chunk section (plus face-adjacent
 * sections when the block sits on a section boundary) to an immediate rebuild that runs
 * ahead of vanilla's distance-sorted, per-frame-budgeted update pass. Everything else
 * stays on the vanilla path, so server-driven churn cannot bypass throttling.
 *
 * RenderGlobal internals are reached through a handful of cached reflective handles
 * (a few reads per block change / frame); the per-renderer state uses the injected
 * {@link ImmediateMarker} interface and costs no reflection at all.
 */
public final class VertexHooks
{
    /** Reach gate: only changes within 8 blocks of the view entity are interactive. */
    private static final double REACH_SQ = 64.0D;

    /** Hard per-frame cap; a corner block promotes at most 4 sections, which all fit. */
    private static final int MAX_IMMEDIATE_PER_FRAME = 4;

    private static boolean initialized = false;
    private static boolean disabled = false;

    private static Field mcInstance;
    private static Field renderViewEntity;
    private static Field entityPosX;
    private static Field entityPosY;
    private static Field entityPosZ;
    private static Field worldRenderers;
    private static Field worldRenderersToUpdate;
    private static Field renderChunksWide;
    private static Field renderChunksTall;
    private static Field renderChunksDeep;

    /**
     * Runs after every EntityRenderer.setupFog exit. With fog=false, only linear
     * (distance) fog is disabled; density fog from lava, water, or blindness uses
     * EXP/EXP2 modes and is deliberately preserved.
     */
    public static void afterFogSetup()
    {
        if (!VertexConfig.enabled("fog") && org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE) == org.lwjgl.opengl.GL11.GL_LINEAR)
        {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_FOG);
        }
    }

    public static void blockChanged(Object renderGlobal, int x, int y, int z)
    {
        if (!VertexConfig.enabled("interactiveRenderPriority") || !ready(renderGlobal))
        {
            return;
        }

        try
        {
            Object viewer = viewEntity(renderGlobal);

            if (viewer == null)
            {
                return;
            }

            double dx = entityPosX.getDouble(viewer) - ((double)x + 0.5D);
            double dy = entityPosY.getDouble(viewer) - ((double)y + 0.5D);
            double dz = entityPosZ.getDouble(viewer) - ((double)z + 0.5D);

            if (dx * dx + dy * dy + dz * dz > REACH_SQ)
            {
                return;
            }

            promote(renderGlobal, x, y, z);
            // A block on a section boundary exposes or hides faces of blocks in the
            // face-adjacent section; promote those too or the boundary shows a hole.
            // Diagonal sections share no face and stay on the throttled path.
            int subX = x & 15;
            int subY = y & 15;
            int subZ = z & 15;

            if (subX == 0)
            {
                promote(renderGlobal, x - 1, y, z);
            }
            else if (subX == 15)
            {
                promote(renderGlobal, x + 1, y, z);
            }

            if (subY == 0 && y > 0)
            {
                promote(renderGlobal, x, y - 1, z);
            }
            else if (subY == 15 && y < 255)
            {
                promote(renderGlobal, x, y + 1, z);
            }

            if (subZ == 0)
            {
                promote(renderGlobal, x, y, z - 1);
            }
            else if (subZ == 15)
            {
                promote(renderGlobal, x, y, z + 1);
            }
        }
        catch (Exception e)
        {
            disable("blockChanged", e);
        }
    }

    public static void consumeImmediates(Object renderGlobal, Object viewEntity)
    {
        if (!ready(renderGlobal))
        {
            return;
        }

        VertexStats.tick();

        try
        {
            List<?> queue = (List<?>)worldRenderersToUpdate.get(renderGlobal);
            int consumed = 0;
            Iterator<?> it = queue.iterator();

            while (it.hasNext() && consumed < MAX_IMMEDIATE_PER_FRAME)
            {
                Object entry = it.next();

                if (entry instanceof ImmediateMarker)
                {
                    ImmediateMarker marker = (ImmediateMarker)entry;

                    if (marker.vertex$needsImmediate())
                    {
                        if (marker.vertex$isDirty())
                        {
                            marker.vertex$rebuild(viewEntity);
                            VertexStats.rebuild();
                            ++consumed;
                        }

                        marker.vertex$clearImmediate();
                        it.remove();
                    }
                }
            }
        }
        catch (Exception e)
        {
            disable("consumeImmediates", e);
        }
    }

    private static void promote(Object renderGlobal, int x, int y, int z) throws Exception
    {
        Object[] grid = (Object[])worldRenderers.get(renderGlobal);

        if (grid == null)
        {
            return;
        }

        int wide = renderChunksWide.getInt(renderGlobal);
        int tall = renderChunksTall.getInt(renderGlobal);
        int deep = renderChunksDeep.getInt(renderGlobal);
        // Same toroidal bucketing as RenderGlobal.markBlocksForUpdate: arithmetic shift
        // is floor division by 16, then wrap into the renderer grid.
        int gx = wrap(x >> 4, wide);
        int gy = wrap(y >> 4, tall);
        int gz = wrap(z >> 4, deep);
        Object renderer = grid[(gz * tall + gy) * wide + gx];

        if (renderer instanceof ImmediateMarker)
        {
            ((ImmediateMarker)renderer).vertex$markImmediate();
            VertexStats.promotion();
            List<Object> queue = (List<Object>)worldRenderersToUpdate.get(renderGlobal);

            if (!queue.contains(renderer))
            {
                queue.add(renderer);
            }
        }
    }

    private static int wrap(int value, int size)
    {
        int wrapped = value % size;
        return wrapped < 0 ? wrapped + size : wrapped;
    }

    private static Object viewEntity(Object renderGlobal) throws Exception
    {
        Object minecraft = mcInstance.get(renderGlobal);
        return minecraft == null ? null : renderViewEntity.get(minecraft);
    }

    /**
     * Resolves every reflective handle from the live RenderGlobal instance. Launch adds the
     * tweaker's package to the class-loader exclusions, so this class is defined by the app
     * class loader - a Class.forName here would load a second, untransformed copy of the
     * obfuscated classes whose Fields reject the game's LaunchClassLoader instances.
     */
    private static synchronized boolean ready(Object renderGlobalInstance)
    {
        if (disabled)
        {
            return false;
        }

        if (initialized)
        {
            return true;
        }

        try
        {
            Class<?> renderGlobal = renderGlobalInstance.getClass();
            mcInstance = accessible(renderGlobal, Mappings.RG_MC);
            worldRenderers = accessible(renderGlobal, Mappings.RG_WORLD_RENDERERS);
            worldRenderersToUpdate = accessible(renderGlobal, Mappings.RG_WORLD_RENDERERS_TO_UPDATE);
            renderChunksWide = accessible(renderGlobal, Mappings.RG_RENDER_CHUNKS_WIDE);
            renderChunksTall = accessible(renderGlobal, Mappings.RG_RENDER_CHUNKS_TALL);
            renderChunksDeep = accessible(renderGlobal, Mappings.RG_RENDER_CHUNKS_DEEP);
            renderViewEntity = accessible(mcInstance.getType(), Mappings.MINECRAFT_RENDER_VIEW_ENTITY);
            // Entity declares the position fields; obfuscated names repeat per class, so walk
            // up from EntityLivingBase to the class directly under Object before resolving.
            Class<?> entity = renderViewEntity.getType();

            while (entity.getSuperclass() != Object.class)
            {
                entity = entity.getSuperclass();
            }

            entityPosX = accessible(entity, Mappings.ENTITY_POS_X);
            entityPosY = accessible(entity, Mappings.ENTITY_POS_Y);
            entityPosZ = accessible(entity, Mappings.ENTITY_POS_Z);

            if (entityPosX.getType() != double.class)
            {
                throw new IllegalStateException("Entity position field has unexpected type " + entityPosX.getType());
            }

            initialized = true;
            LogWrapper.info("[Vertex] Render-priority hooks initialized");
            return true;
        }
        catch (Exception e)
        {
            disable("init", e);
            return false;
        }
    }

    private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void disable(String where, Exception e)
    {
        if (!disabled)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Disabling render-priority hooks after failure in " + where);
            e.printStackTrace();
        }
    }

    private VertexHooks()
    {
    }
}
