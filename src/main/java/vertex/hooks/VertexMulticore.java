package vertex.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;
import vertex.api.ImmediateMarker;
import vertex.multicore.BuildQueue;
import vertex.multicore.BuildWorkers;

/**
 * Dark-launched multi-core chunk building (docs/ROADMAP.md #1), enabled only by
 * -Dvertex.multicore=true. Strategy: vanilla WorldRenderer.updateRenderer is wrapped, not
 * split. On the client thread, eligible rebuilds are intercepted at the method head and
 * submitted to the worker pool. Workers run the untouched vanilla body with a worker-bound
 * Tessellator; the two GL-boundary methods (preRenderBlocks/postRenderBlocks) are guarded
 * so on workers the hook performs only their tessellator half and captures the filled
 * tessellator per pass. The client thread replays captured passes into the display lists
 * with the exact GL sequence extracted from the vanilla boundary methods.
 *
 * Immediate (interactive) rebuilds stay synchronous on the client thread. When the flag is
 * off every hook is a single boolean check.
 */
public final class VertexMulticore
{
    private static final boolean ENABLED = Boolean.getBoolean("vertex.multicore");
    private static final float SECTION_SCALE = 1.000001F;

    private static final ThreadLocal<ChunkBuild> currentBuild = new ThreadLocal<ChunkBuild>();
    private static final ConcurrentLinkedQueue<Object> tessellatorPool = new ConcurrentLinkedQueue<Object>();
    private static final IdentityHashMap<Object, ChunkBuild> inFlight = new IdentityHashMap<Object, ChunkBuild>();

    private static BuildQueue queue;
    private static BuildWorkers workers;
    private static boolean initialized = false;
    // Written from whichever thread fails (workers included) and read on every hook's
    // fast path: must be volatile or the client thread may never observe a worker-side
    // disable.
    private static volatile boolean disabled = false;
    private static boolean tornDown = false;

    private static Constructor<?> tessellatorCtor;
    private static Method tessStartQuads;
    private static Method tessSetTranslation;
    private static Method tessDraw;
    private static Method tessGetVertexState;
    private static Method setupTranslationBridge;
    private static Field wrNeedsUpdate;
    private static Field wrBytesDrawn;
    private static Field wrGlRenderList;
    private static Field wrVertexState;
    private static Field wrTileEntities;
    private static Field wrTileEntityRenderers;
    private static Field wrPosX;
    private static Field wrPosY;
    private static Field wrPosZ;
    private static Field entityPosX;
    private static Field entityPosY;
    private static Field entityPosZ;

    public static final class ChunkBuild extends BuildQueue.Build
    {
        final Object entity;
        final int posX;
        final int posY;
        final int posZ;
        final Object[] passTessellators = new Object[2];
        final int[] passListIds = new int[2];
        final int[] passIndices = new int[2];
        int capturedPasses;
        int completedPasses;
        Object savedTileEntities;
        List<Object> capturedTileEntities;
        List<Object> previousTileEntityRenderers;

        ChunkBuild(Object renderer, int generation, Object entity, int posX, int posY, int posZ)
        {
            super(renderer, 0, generation);
            this.entity = entity;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
        }
    }

    /** Head guard on WorldRenderer.updateRenderer: true = skip the vanilla body. */
    public static boolean interceptUpdate(Object renderer, Object entity)
    {
        if (!ENABLED)
        {
            return false;
        }

        // The worker-context check must precede the disabled check: a worker already
        // inside a build when disable trips would otherwise run the vanilla body without
        // the tile-entity list swap - exactly the cross-thread race the swap prevents.
        ChunkBuild building = currentBuild.get();

        if (building != null)
        {
            // Worker thread entering the body: swap the shared tile-entity list for a
            // capture list so the vanilla reconciliation cannot race the render thread.
            try
            {
                // Restore the dirty flag the client's loop cleared right after submit:
                // the vanilla body's own head check is `if (needsUpdate)`, and without
                // this it no-ops - every worker build was empty display lists, found by
                // the first capture that actually LOOKED at multicore's output (an empty
                // sky where a grass field belonged). The body re-clears it itself.
                wrNeedsUpdate.setBoolean(renderer, true);
                building.savedTileEntities = wrTileEntities.get(renderer);
                building.capturedTileEntities = new ArrayList<Object>();
                building.previousTileEntityRenderers = new ArrayList<Object>((List<?>)wrTileEntityRenderers.get(renderer));
                wrTileEntities.set(renderer, building.capturedTileEntities);
            }
            catch (Exception e)
            {
                disable("worker prep", e);
            }

            return false;
        }

        if (disabled || !(renderer instanceof ImmediateMarker) || !ready(renderer))
        {
            return false;
        }

        ImmediateMarker marker = (ImmediateMarker)renderer;

        // The in-flight check must precede the immediate exemption: an immediate rebuild
        // on the client while a worker runs the same vanilla body corrupts renderer state
        // (kyrofx #35). An in-flight renderer skips the client body regardless; its
        // immediate flag survives and is consumed after the worker result drains.
        if (inFlight.containsKey(renderer))
        {
            return true;
        }

        if (marker.vertex$needsImmediate() || !marker.vertex$isDirty())
        {
            return false;
        }

        if (queue.pendingCount() >= workers.size() * 2)
        {
            // Backpressure: let vanilla build synchronously this frame.
            return false;
        }

        try
        {
            ChunkBuild build = new ChunkBuild(renderer, queue.generation(), entity,
                wrPosX.getInt(renderer), wrPosY.getInt(renderer), wrPosZ.getInt(renderer));
            inFlight.put(renderer, build);
            queue.submit(build);
            return true;
        }
        catch (Exception e)
        {
            disable("submit", e);
            return false;
        }
    }

    /** Pending build-queue depth for diagnostics; 0 when multicore is off. */
    public static int pendingDepth()
    {
        return ENABLED && !disabled && queue != null ? queue.pendingCount() : 0;
    }

    /** True while a worker build for this renderer is queued or running (client thread). */
    public static boolean isInFlight(Object renderer)
    {
        return ENABLED && !disabled && inFlight.containsKey(renderer);
    }

    /** Head guard on preRenderBlocks(listId): on workers, do the tessellator half only. */
    public static boolean interceptPreRender(Object renderer, int listId)
    {
        ChunkBuild build = currentBuild.get();

        if (build == null)
        {
            return false;
        }

        try
        {
            // Vanilla starts passes lazily with a locally computed list id, in strict pass
            // order; the id is recorded verbatim per slot and the true pass index arrives
            // via postRenderBlocks' own argument. No assumptions about list allocation.
            Object tessellator = borrowTessellator();
            VertexTessellator.bindThreadInstance(tessellator);
            int slot = build.capturedPasses++;
            build.passTessellators[slot] = tessellator;
            build.passListIds[slot] = listId;

            if (BUILD_AUDIT)
            {
                LogWrapper.info("[VertexAudit] pre build=" + build.posX + "," + build.posY + "," + build.posZ
                    + " slot=" + slot + " tess=" + System.identityHashCode(tessellator) + " listId=" + listId);
            }
            tessStartQuads.invoke(tessellator);
            tessSetTranslation.invoke(tessellator, Double.valueOf(-build.posX), Double.valueOf(-build.posY), Double.valueOf(-build.posZ));
        }
        catch (Exception e)
        {
            build.failed = true;
            disable("preRender capture", e);
        }

        return true;
    }

    /** Head guard on postRenderBlocks(pass, entity): on workers, keep the filled tessellator. */
    public static boolean interceptPostRender(Object renderer, int pass)
    {
        ChunkBuild build = currentBuild.get();

        if (build == null)
        {
            return false;
        }

        if (build.completedPasses < build.capturedPasses)
        {
            build.passIndices[build.completedPasses++] = pass;
        }

        return true;
    }

    /** Called from the worker loop around the vanilla body. */
    public static void runBuild(BuildQueue.Build build) throws Exception
    {
        if (disabled)
        {
            // The pipeline is coming down; don't touch the live renderer anymore.
            build.failed = true;
            return;
        }

        ChunkBuild chunkBuild = (ChunkBuild)build;
        currentBuild.set(chunkBuild);

        try
        {
            ((ImmediateMarker)chunkBuild.renderer).vertex$rebuild(chunkBuild.entity);
        }
        finally
        {
            currentBuild.remove();
            VertexTessellator.bindThreadInstance(null);

            if (chunkBuild.savedTileEntities != null)
            {
                wrTileEntities.set(chunkBuild.renderer, chunkBuild.savedTileEntities);
            }
        }
    }

    /**
     * loadRenderers rebuilt the whole grid: every renderer object is replaced and every
     * display-list id reallocated. In-flight builds reference the OLD objects, whose
     * stamps still match, so without this their replays compile into list ids the new
     * grid assigned to other renderers - the fragment defect's mechanism: a few sections
     * accumulated many builds' geometry (7.4MB in one list) while 34/60 near-surface
     * sections went empty. The CheatBreaker-era design invalidated on loadRenderers;
     * the wrap port was missing this hook.
     */
    /** Adapter for the head hook on RenderGlobal.loadRenderers (instance arg unused). */
    public static void onRenderersReloadedHook(Object renderGlobal)
    {
        onRenderersReloaded();
    }

    public static void onRenderersReloaded()
    {
        if (!ENABLED || disabled)
        {
            return;
        }

        if (BUILD_AUDIT)
        {
            LogWrapper.info("[VertexAudit] loadRenderers fired, queue=" + (queue != null ? "live gen->" + (queue.generation() + 1) : "null"));
        }

        if (queue == null)
        {
            return;
        }

        queue.invalidateGeneration();
        inFlight.clear();
    }

    /** Client thread, once per frame: apply finished builds into display lists. */
    public static void drainFinished()
    {
        if (!ENABLED || queue == null)
        {
            return;
        }

        if (disabled)
        {
            teardown();
            return;
        }

        queue.drain(SINK, 4);
    }

    /**
     * One-time client-thread teardown after a self-disable: without it the pipeline kept
     * everything alive for the rest of the process - workers polling forever, the finished
     * queue retaining renderers, entities, captured tile-entity lists and their 2MB pass
     * Tessellators, inFlight pinning every submitted renderer (#73). clearAll routes the
     * backlog through the discard path so affected renderers are re-marked dirty and
     * rebuilt by the vanilla path.
     */
    private static void teardown()
    {
        if (tornDown)
        {
            return;
        }

        tornDown = true;
        queue.close();

        if (workers != null)
        {
            workers.shutdown();
        }

        queue.clearAll(SINK);
        inFlight.clear();
        tessellatorPool.clear();
        // Drop the statics so a straggler worker's late complete() (join timed out) can't
        // keep its build reachable; the workers hold their own queue reference.
        queue = null;
        workers = null;
        LogWrapper.info("[Vertex] Multi-core pipeline torn down after disable");
    }

    private static final BuildQueue.Sink SINK = new BuildQueue.Sink()
    {
        public boolean apply(BuildQueue.Build build)
        {
            ChunkBuild chunkBuild = (ChunkBuild)build;
            inFlight.remove(chunkBuild.renderer);

            try
            {
                if (wrPosX.getInt(chunkBuild.renderer) != chunkBuild.posX
                    || wrPosY.getInt(chunkBuild.renderer) != chunkBuild.posY
                    || wrPosZ.getInt(chunkBuild.renderer) != chunkBuild.posZ)
                {
                    discard(build);
                    return false;
                }

                replay(chunkBuild);
                return true;
            }
            catch (Exception e)
            {
                disable("replay", e);
                // The section's display list may hold half-replayed geometry; re-dirty it
                // so the vanilla path rebuilds it cleanly after the pipeline tears down.
                discard(build);
                return false;
            }
        }

        public void discard(BuildQueue.Build build)
        {
            ChunkBuild chunkBuild = (ChunkBuild)build;
            inFlight.remove(chunkBuild.renderer);

            try
            {
                if (chunkBuild.renderer instanceof ImmediateMarker)
                {
                    wrNeedsUpdate.setBoolean(chunkBuild.renderer, true);
                }
            }
            catch (Exception e)
            {
                disable("discard", e);
            }
        }
    };

    /** The exact GL sequence of the vanilla boundary methods, replayed on the client. */
    private static void replay(ChunkBuild build) throws Exception
    {
        Object renderer = build.renderer;

        for (int slot = 0; slot < build.capturedPasses; ++slot)
        {
            Object tessellator = build.passTessellators[slot];
            // An exception between glNewList and glEndList would leave the list open -
            // every subsequent GL call in the session compiles into it instead of
            // executing - and an unmatched glPushMatrix corrupts the stack. The reflective
            // calls in the middle CAN throw (the empty-translucent-pass crash arrived
            // exactly here), so each GL bracket closes in a finally, tracked separately.
            boolean listOpen = false;
            boolean matrixPushed = false;
            boolean slotReplayed = false;

            try
            {
                // preRenderBlocks' int argument is the PASS INDEX, not a list id (verified
                // in blo.a(Lsv;)V bytecode: it is called with the pass loop counter). The
                // original replay compiled every section into GL lists 0 and 1 - the whole
                // fragment defect. The real target is the renderer's own list plus pass.
                GL11.glNewList(wrGlRenderList.getInt(renderer) + build.passListIds[slot], GL11.GL_COMPILE);
                listOpen = true;
                GL11.glPushMatrix();
                matrixPushed = true;
                setupTranslationBridge.invoke(renderer);
                GL11.glTranslatef(-8.0F, -8.0F, -8.0F);
                GL11.glScalef(SECTION_SCALE, SECTION_SCALE, SECTION_SCALE);
                GL11.glTranslatef(8.0F, 8.0F, 8.0F);

                if (build.passIndices[slot] == 1 && build.entity != null)
                {
                    try
                    {
                        resolveEntityFields(build.entity);
                        Object state = tessGetVertexState.invoke(tessellator,
                            Float.valueOf((float)entityPosX.getDouble(build.entity)),
                            Float.valueOf((float)entityPosY.getDouble(build.entity)),
                            Float.valueOf((float)entityPosZ.getDouble(build.entity)));
                        wrVertexState.set(renderer, state);
                    }
                    catch (Exception emptyPass)
                    {
                        // Vanilla getVertexState sizes a PriorityQueue by quad count and
                        // throws on zero: a translucent pass CAN start and tessellate no
                        // quads. No quads means nothing to sort - a null state is correct.
                        wrVertexState.set(renderer, null);
                    }
                }

                int drawn = ((Integer)tessDraw.invoke(tessellator)).intValue();
                wrBytesDrawn.setInt(renderer, wrBytesDrawn.getInt(renderer) + drawn);

                if (BUILD_AUDIT)
                {
                    LogWrapper.info("[VertexAudit] replay build=" + build.posX + "," + build.posY + "," + build.posZ
                        + " slot=" + slot + " listId=" + build.passListIds[slot] + " bytes=" + drawn + " gen=" + build.generation);
                }

                slotReplayed = true;
            }
            finally
            {
                if (matrixPushed)
                {
                    GL11.glPopMatrix();
                }

                if (listOpen)
                {
                    GL11.glEndList();
                }

                // A tessellator whose draw threw may still be mid-tessellation; pooling it
                // would hand "Already tesselating!" to the next borrower. Only clean ones
                // return to the pool - a failed one is dropped for GC. The reset gets its
                // own catch so a throw here can never mask the original exception.
                if (slotReplayed)
                {
                    try
                    {
                        tessSetTranslation.invoke(tessellator, Double.valueOf(0.0D), Double.valueOf(0.0D), Double.valueOf(0.0D));
                        recycleTessellator(tessellator);
                    }
                    catch (Exception resetFailed)
                    {
                        LogWrapper.warning("[Vertex] Dropped a pass tessellator whose reset failed: " + resetFailed);
                    }
                }
            }
        }

        // Tile-entity reconciliation deferred from the worker: removals are the renderers
        // the rebuild dropped; additions are what the vanilla body put in the capture list.
        List<Object> global = (List<Object>)build.savedTileEntities;

        if (global != null)
        {
            HashSet<Object> removed = new HashSet<Object>(build.previousTileEntityRenderers);
            removed.removeAll((List<?>)wrTileEntityRenderers.get(renderer));
            global.removeAll(removed);
            global.addAll(build.capturedTileEntities);
        }

        // Deliberately do NOT clear needsUpdate here. The vanilla body already cleared it
        // when the worker build started; if it is true again now, a block changed after the
        // snapshot and this replay is already stale - clearing the mark would discard that
        // newer change and leave stale geometry until an unrelated event re-marks the
        // section (kyrofx #34). The re-mark also re-queued the renderer, so leaving the
        // flag alone lets the normal path rebuild with fresh data.
        VertexStats.rebuild();
    }

    private static final boolean BUILD_AUDIT = Boolean.getBoolean("vertex.test.buildAudit");

    private static Object borrowTessellator() throws Exception
    {
        Object pooled = tessellatorPool.poll();
        Object result = pooled != null ? pooled : tessellatorCtor.newInstance(Integer.valueOf(2097152));

        if (BUILD_AUDIT)
        {
            ChunkBuild owner = currentBuild.get();
            LogWrapper.info("[VertexAudit] borrow tess=" + System.identityHashCode(result)
                + " pooled=" + (pooled != null)
                + " build=" + (owner != null ? owner.posX + "," + owner.posY + "," + owner.posZ : "none")
                + " thread=" + Thread.currentThread().getName());
        }

        return result;
    }

    private static void recycleTessellator(Object tessellator)
    {
        if (BUILD_AUDIT)
        {
            LogWrapper.info("[VertexAudit] recycle tess=" + System.identityHashCode(tessellator));
        }

        if (tessellatorPool.size() < 16)
        {
            tessellatorPool.add(tessellator);
        }
    }

    private static synchronized boolean ready(Object renderer)
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
            Class<?> wr = renderer.getClass();
            wrNeedsUpdate = accessible(wr, Mappings.WR_NEEDS_UPDATE);
            wrBytesDrawn = accessible(wr, Mappings.WR_BYTES_DRAWN);
            wrGlRenderList = accessible(wr, "z");
            wrVertexState = accessible(wr, Mappings.WR_VERTEX_STATE);
            wrTileEntities = accessible(wr, Mappings.WR_TILE_ENTITIES);
            wrTileEntityRenderers = accessible(wr, Mappings.WR_TILE_ENTITY_RENDERERS);
            wrPosX = accessible(wr, Mappings.WR_POS_X);
            wrPosY = accessible(wr, Mappings.WR_POS_Y);
            wrPosZ = accessible(wr, Mappings.WR_POS_Z);
            setupTranslationBridge = wr.getMethod("vertex$setupTranslation");
            Object mainTessellator = VertexTessellator.get();
            Class<?> tess = mainTessellator.getClass();
            tessellatorCtor = tess.getDeclaredConstructor(Integer.TYPE);
            tessellatorCtor.setAccessible(true);
            tessStartQuads = tess.getMethod(Mappings.TESS_START_QUADS);
            tessSetTranslation = tess.getMethod(Mappings.TESS_SET_TRANSLATION, Double.TYPE, Double.TYPE, Double.TYPE);
            tessDraw = tess.getMethod(Mappings.TESS_DRAW);
            tessGetVertexState = tess.getMethod(Mappings.TESS_GET_VERTEX_STATE, Float.TYPE, Float.TYPE, Float.TYPE);
            queue = new BuildQueue();
            workers = new BuildWorkers(queue, new BuildWorkers.Task()
            {
                public void build(BuildQueue.Build build) throws Exception
                {
                    runBuild(build);
                }
            }, "VertexChunkBuild");
            initialized = true;
            LogWrapper.info("[Vertex] Multi-core chunk building enabled (" + workers.size() + " workers)");
            return true;
        }
        catch (Exception e)
        {
            disable("init", e);
            return false;
        }
    }

    /** Entity position handles are resolved lazily from the first entity seen. */
    private static void resolveEntityFields(Object entity) throws NoSuchFieldException
    {
        if (entityPosX == null && entity != null)
        {
            Class<?> root = entity.getClass();

            while (root.getSuperclass() != Object.class)
            {
                root = root.getSuperclass();
            }

            entityPosX = accessible(root, Mappings.ENTITY_POS_X);
            entityPosY = accessible(root, Mappings.ENTITY_POS_Y);
            entityPosZ = accessible(root, Mappings.ENTITY_POS_Z);
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
            LogWrapper.severe("[Vertex] Multi-core disabled after failure in " + where);
            e.printStackTrace();
        }
    }

    private VertexMulticore()
    {
    }
}
