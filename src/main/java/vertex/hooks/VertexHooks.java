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
     * Vanilla 1.7.10 hard-allocates RenderGlobal storage for radii 2..16, but loads an
     * arbitrary integer from options.txt. A game directory shared with a newer release
     * can therefore crash renderer initialization before the first chunk arrives.
     */
    public static int clampLegacyRenderDistance(int requested)
    {
        int clamped = Math.max(2, Math.min(16, requested));

        if (clamped != requested)
        {
            LogWrapper.warning("[Vertex] Render distance " + requested + " is outside Minecraft 1.7.10's supported range; using " + clamped);
        }

        return clamped;
    }

    /**
     * Runs after every EntityRenderer.setupFog exit. With fog=false, only linear
     * (distance) fog is disabled; density fog from lava, water, or blindness uses
     * EXP/EXP2 modes and is deliberately preserved. With fog on, a non-default
     * fogStart fraction re-anchors where the linear band begins (vanilla uses
     * 0.25 * end for terrain passes; the sky pass barely shows the difference).
     */
    public static void afterFogSetup()
    {
        if (org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE) != org.lwjgl.opengl.GL11.GL_LINEAR)
        {
            return;
        }

        if (!VertexConfig.enabled("fog"))
        {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_FOG);
            return;
        }

        float fraction = fogStartFraction(VertexConfig.value("fogStart", "default"));

        if (fraction >= 0.0F)
        {
            float end = org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_FOG_END);
            org.lwjgl.opengl.GL11.glFogf(org.lwjgl.opengl.GL11.GL_FOG_START, end * fraction);
        }
    }

    /** Parses the fogStart key: 0.2/0.4/0.6/0.8, anything else means vanilla (-1). */
    static float fogStartFraction(String raw)
    {
        if (raw == null)
        {
            return -1.0F;
        }

        String trimmed = raw.trim();
        return trimmed.equals("0.2") ? 0.2F : trimmed.equals("0.4") ? 0.4F
            : trimmed.equals("0.6") ? 0.6F : trimmed.equals("0.8") ? 0.8F : -1.0F;
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

    /** Stationary detection for the Dynamic Updates boost. */
    private static double lastViewerX;
    private static double lastViewerY;
    private static double lastViewerZ;
    private static int stationaryFrames = 0;

    /** True once the view entity has held one block position for about a second. */
    public static boolean playerStationary()
    {
        return stationaryFrames > 60;
    }

    public static void consumeImmediates(Object renderGlobal, Object viewEntity)
    {
        if (!ready(renderGlobal))
        {
            return;
        }

        trackStationary(viewEntity);
        VertexStats.tick();
        VertexSkyBridge.publish(renderGlobal);
        VertexMulticore.drainFinished();

        // Menu flips of mesh-baked settings re-mark here, on the client thread, in
        // every renderer mode (managed clientTick only runs when MANAGED).
        if (VertexRenderer.consumeSettingsRemark())
        {
            VertexRenderer.remarkAllSections(renderGlobal);
        }

        if (VertexRenderProfiler.ACTIVE)
        {
            VertexRenderProfiler.frame(renderGlobal);
        }

        if (VertexRenderer.MANAGED)
        {
            VertexRenderer.clientTick(renderGlobal);
        }

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
                        // A renderer being built on a worker must not run the vanilla body
                        // here concurrently (kyrofx #35): keep the flag and the queue entry;
                        // this consumer picks it up after the worker result drains.
                        if (VertexMulticore.isInFlight(entry))
                        {
                            continue;
                        }

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

    private static void trackStationary(Object viewEntity)
    {
        try
        {
            if (viewEntity == null || entityPosX == null)
            {
                stationaryFrames = 0;
                return;
            }

            double x = entityPosX.getDouble(viewEntity);
            double y = entityPosY.getDouble(viewEntity);
            double z = entityPosZ.getDouble(viewEntity);
            // Tight positional tolerance: looking around while standing still counts,
            // walking resets within a frame or two.
            boolean still = Math.abs(x - lastViewerX) < 0.05D
                && Math.abs(y - lastViewerY) < 0.05D && Math.abs(z - lastViewerZ) < 0.05D;
            stationaryFrames = still ? stationaryFrames + 1 : 0;
            lastViewerX = x;
            lastViewerY = y;
            lastViewerZ = z;
        }
        catch (Exception e)
        {
            stationaryFrames = 0;
        }
    }

    /** Frame capture: live (non-null) build-queue entries, or -1 when unavailable. */
    public static int pendingUpdates(Object renderGlobal)
    {
        try
        {
            if (!ready(renderGlobal))
            {
                return -1;
            }

            // The vanilla queue retains nulled slots AND clean entries it never removes;
            // only entries that are actually dirty represent pending work.
            int live = 0;

            for (Object entry : (List<?>)worldRenderersToUpdate.get(renderGlobal))
            {
                if (entry instanceof ImmediateMarker && ((ImmediateMarker)entry).vertex$isDirty())
                {
                    ++live;
                }
            }

            return live;
        }
        catch (Exception e)
        {
            return -1;
        }
    }

    /** Test-harness entry: gate-free section promotion for synthetic rebuild load. */

    static void promoteForTest(Object renderGlobal, int x, int y, int z) throws Exception
    {
        if (ready(renderGlobal))
        {
            promote(renderGlobal, x, y, z);
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

    /** Shared with other hooks: the Minecraft instance held by a RenderGlobal. */
    static Object minecraftOf(Object renderGlobal) throws Exception
    {
        return ready(renderGlobal) ? mcInstance.get(renderGlobal) : null;
    }

    /** Shared with other hooks: the client world, or null outside a world. */
    static Object worldOf(Object minecraft) throws Exception
    {
        if (minecraft == null)
        {
            return null;
        }

        java.lang.reflect.Field field = minecraft.getClass().getDeclaredField(Mappings.MC_THE_WORLD);
        field.setAccessible(true);
        return field.get(minecraft);
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
