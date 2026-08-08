package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;
import vertex.api.ImmediateMarker;
import vertex.render.DisplayListBackend;
import vertex.render.MeshData;
import vertex.render.RenderBackend;

/**
 * Orchestrator of the managed section-mesh pipeline (docs/RENDERER.md), selected by the
 * "renderer" config key (or -Dvertex.renderer=), resolved once at class load like the
 * multicore flag - the transformer weaves the managed hooks only when MODE is not legacy,
 * so the default configuration runs byte-identical vanilla-shaped code.
 *
 * Data flow when managed: every chunk-section rebuild - worker builds via the multicore
 * capture, synchronous client rebuilds and translucent resorts via the client capture
 * here - produces backend-neutral {@link MeshData}, and the one install path hands it to
 * the live {@link RenderBackend} on the client thread. Workers never touch GL; the
 * backend never validates game state (staleness is gated before install by the multicore
 * stamp/generation checks, and the client capture is same-thread synchronous).
 *
 * Failure policy: first failure anywhere disables the managed pipeline for the session,
 * releases backend resources, and re-marks every section dirty so the vanilla path
 * rebuilds the world's display lists - degraded for a few seconds, never down.
 */
public final class VertexRenderer
{
    public static final int LEGACY = 0;
    public static final int DISPLAY_LIST = 1;
    public static final int VBO = 2;
    public static final int ARENA = 3;

    public static final int MODE = resolveMode();
    /** Weave gate; also the zero-cost fast-path check on legacy sessions. */
    public static final boolean MANAGED = MODE != LEGACY;

    private static volatile boolean disabled = false;
    private static volatile boolean remarkRequested = false;
    private static boolean initialized = false;
    private static boolean submitReady = false;
    private static RenderBackend backend;
    private static final MeshData.Extractor EXTRACTOR = new MeshData.Extractor();

    // WorldRenderer handles (resolvable from any thread, synchronized init).
    private static Field wrGlRenderList;
    private static Field wrBytesDrawn;
    private static Field wrVertexState;
    private static Field wrSkipRenderPass;
    private static Field wrNeedsUpdate;
    private static Field wrPosX;
    private static Field wrPosY;
    private static Field wrPosZ;

    // Tessellator handles.
    private static Method tessStartQuads;
    private static Method tessSetTranslation;
    private static Method tessGetVertexState;
    private static Method tessReset;
    private static Field tessIsDrawing;

    // Submission handles (resolved from the live RenderGlobal on first submit).
    private static Field rgGlRenderLists;
    private static Field rgMc;
    private static Field mcEntityRenderer;
    private static Field mcViewEntity;
    private static Method erEnableLightmap;
    private static Method erDisableLightmap;
    private static Field entityPosX;
    private static Field entityPosY;
    private static Field entityPosZ;
    private static Field entityPrevX;
    private static Field entityPrevY;
    private static Field entityPrevZ;

    // Client-capture state: strictly client thread, one section build at a time.
    private static Object clientEntity;
    private static Object captureRenderer;
    private static Object captureTessellator;

    static int parseMode(String raw)
    {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);

        if (value.equals("displaylist") || value.equals("dl"))
        {
            return DISPLAY_LIST;
        }

        if (value.equals("vbo"))
        {
            return VBO;
        }

        if (value.equals("arena"))
        {
            return ARENA;
        }

        if (!value.isEmpty() && !value.equals("legacy"))
        {
            LogWrapper.warning("[Vertex] Unknown renderer '" + raw + "', staying on legacy");
        }

        return LEGACY;
    }

    private static int resolveMode()
    {
        String property = System.getProperty("vertex.renderer");
        int mode = parseMode(property != null ? property : VertexConfig.value("renderer", "legacy"));

        if (mode != LEGACY)
        {
            LogWrapper.info("[Vertex] Managed renderer requested: mode=" + mode);
        }

        return mode;
    }

    public static boolean managed()
    {
        return MANAGED && !disabled;
    }

    /** Stashed at the head of updateRenderer / updateRendererSort on the client thread. */
    public static void noteClientEntity(Object entity)
    {
        if (managed())
        {
            clientEntity = entity;
        }
    }

    /**
     * Client half of the preRenderBlocks guard (workers take the multicore branch before
     * this is consulted). True = the tessellator half ran here and the vanilla GL half
     * must not: no display list opens, no matrix pushes.
     */
    public static boolean clientPre(Object renderer, int pass)
    {
        if (captureRenderer != null)
        {
            // A previous body threw between pre and post; drop that capture cleanly.
            abandonCapture();
        }

        if (!managed() || !(renderer instanceof ImmediateMarker))
        {
            return false;
        }

        try
        {
            if (!ready(renderer))
            {
                return false;
            }

            Object tessellator = VertexTessellator.get();
            tessStartQuads.invoke(tessellator);
            tessSetTranslation.invoke(tessellator,
                Double.valueOf(-wrPosX.getInt(renderer)),
                Double.valueOf(-wrPosY.getInt(renderer)),
                Double.valueOf(-wrPosZ.getInt(renderer)));
            captureRenderer = renderer;
            captureTessellator = tessellator;
            return true;
        }
        catch (Exception e)
        {
            disable("clientPre", e);
            return false;
        }
    }

    /**
     * Client half of the postRenderBlocks guard. An open capture is ALWAYS consumed here,
     * even after a disable in between: the vanilla GL half would otherwise pop a matrix
     * it never pushed and end a list it never opened.
     */
    public static boolean clientPost(Object renderer, int pass)
    {
        if (captureRenderer == null)
        {
            return false;
        }

        if (captureRenderer != renderer)
        {
            abandonCapture();
            return false;
        }

        Object tessellator = captureTessellator;
        captureRenderer = null;
        captureTessellator = null;

        try
        {
            if (!disabled)
            {
                Object state = null;

                if (pass == 1 && !((boolean[])wrSkipRenderPass.get(renderer))[1] && clientEntity != null)
                {
                    state = vertexState(tessellator,
                        (float)entityX(clientEntity), (float)entityY(clientEntity), (float)entityZ(clientEntity));
                }

                MeshData mesh = EXTRACTOR.extract(tessellator);
                install(renderer, pass, mesh, state);
            }
        }
        catch (Exception e)
        {
            disable("clientPost", e);
        }
        finally
        {
            sanitize(tessellator);
        }

        return true;
    }

    /**
     * Worker thread: one finished pass, extracted off the worker-bound tessellator while
     * the pass data is hot. The camera for the translucent sort is the submit-time
     * snapshot carried by the build (vanilla uses the raw entity position the same way).
     */
    public static void workerCapture(VertexMulticore.ChunkBuild build, Object renderer, Object tessellator, int pass, int slot) throws Exception
    {
        if (!ready(renderer))
        {
            throw new IllegalStateException("renderer handles unavailable");
        }

        Object state = null;

        if (pass == 1 && !((boolean[])wrSkipRenderPass.get(renderer))[1])
        {
            state = vertexState(tessellator, build.cameraX, build.cameraY, build.cameraZ);
        }

        build.meshes[slot] = EXTRACTOR.extract(tessellator);
        build.vertexStates[slot] = state;
        build.managedCapture = true;
    }

    /** Client thread, from the multicore sink, after the stamp/generation gate passed. */
    public static void installWorkerBuild(VertexMulticore.ChunkBuild build) throws Exception
    {
        for (int slot = 0; slot < build.completedPasses; ++slot)
        {
            MeshData mesh = build.meshes[slot];

            if (mesh == null)
            {
                throw new IllegalStateException("managed build missing mesh for slot " + slot);
            }

            install(build.renderer, build.passIndices[slot], mesh, build.vertexStates[slot]);
            build.meshes[slot] = null;
            build.vertexStates[slot] = null;
        }
    }

    /** The one install path: backend upload plus the renderer bookkeeping vanilla keeps. */
    private static void install(Object renderer, int pass, MeshData mesh, Object state) throws Exception
    {
        RenderBackend live = backend();
        live.upload(renderer, pass, mesh,
            wrPosX.getInt(renderer), wrPosY.getInt(renderer), wrPosZ.getInt(renderer),
            wrGlRenderList.getInt(renderer));
        wrBytesDrawn.setInt(renderer, wrBytesDrawn.getInt(renderer) + mesh.data.length * 4);

        if (pass == 1)
        {
            // The vanilla body nulls vertexState before the pass loop, so null is the
            // correct value for a rebuild whose translucent pass stayed empty.
            wrVertexState.set(renderer, state);
        }
    }

    /**
     * Head guard on renderAllRenderLists: true = the backend drew this pass between the
     * lightmap enable/disable bracket, exactly where vanilla's glCallLists batches ran.
     * False (legacy, display-list backend, or after a disable) = vanilla submission.
     */
    public static boolean interceptSubmit(Object renderGlobal, int pass, double partialTicks)
    {
        if (!managed() || backend == null || !backend.ownsSubmission())
        {
            return false;
        }

        try
        {
            submitReady(renderGlobal);
            Object minecraft = rgMc.get(renderGlobal);
            Object entityRenderer = mcEntityRenderer.get(minecraft);
            Object view = mcViewEntity.get(minecraft);

            if (view == null)
            {
                return false;
            }

            double camX = interp(entityPrevX.getDouble(view), entityPosX.getDouble(view), partialTicks);
            double camY = interp(entityPrevY.getDouble(view), entityPosY.getDouble(view), partialTicks);
            double camZ = interp(entityPrevZ.getDouble(view), entityPosZ.getDouble(view), partialTicks);
            erEnableLightmap.invoke(entityRenderer, Double.valueOf(partialTicks));

            try
            {
                backend.drawVisible((List<?>)rgGlRenderLists.get(renderGlobal), pass, camX, camY, camZ);
            }
            finally
            {
                erDisableLightmap.invoke(entityRenderer, Double.valueOf(partialTicks));
            }

            return true;
        }
        catch (Exception e)
        {
            disable("submit", e);
            return false;
        }
    }

    /** loadRenderers fired (world change, render-distance change, F3+A): new grid. */
    public static void onGridReset()
    {
        clientEntity = null;

        if (backend != null)
        {
            try
            {
                backend.reset();
            }
            catch (Exception e)
            {
                disable("reset", e);
            }
        }
    }

    /**
     * Client-thread maintenance, once per frame from consumeImmediates: executes the
     * re-mark a cross-thread disable requested, so recovery GL work and queue mutation
     * stay on the client thread.
     */
    public static void clientTick(Object renderGlobal)
    {
        if (remarkRequested)
        {
            remarkRequested = false;
            markAllSectionsDirty(renderGlobal);

            if (backend != null)
            {
                try
                {
                    backend.reset();
                }
                catch (Exception ignored)
                {
                    // Already in the failure path; resources die with the session.
                }
            }
        }

        RenderBackend live = backend;

        if (live != null && !disabled)
        {
            // Arena compaction: sections resident in a draining block re-mark here so
            // the queue mutation stays on the client thread with the other mark paths.
            List<Object> deferred = live.drainDeferredRemarks();

            if (!deferred.isEmpty())
            {
                markSectionsDirty(renderGlobal, deferred);
            }
        }
    }

    /** Diagnostics fields for the per-minute stats line; empty when not managed. */
    public static String drainReport()
    {
        RenderBackend live = backend;

        if (!MANAGED || live == null)
        {
            return "";
        }

        long[] c = live.drainCounters();
        StringBuilder out = new StringBuilder();
        out.append(" renderer=").append(disabled ? live.name() + "(disabled)" : live.name());
        out.append(" meshUploads=").append(c[0]);
        out.append(" meshUploadKB=").append(c[1] / 1024L);
        out.append(" meshUploadMs=").append(c[2] / 1_000_000L);

        if (live.ownsSubmission())
        {
            out.append(" meshDrawn=").append(c[3]);
            out.append(" meshDrawCalls=").append(c[4]);
            out.append(" meshDrawMs=").append(c[5] / 1_000_000L);
            out.append(" meshBufMB=").append(live.bufferBytes() / (1024L * 1024L));
        }

        out.append(live.extraReport());
        return out.toString();
    }

    private static RenderBackend backend()
    {
        if (backend == null)
        {
            backend = createBackend();
            LogWrapper.info("[Vertex] Managed renderer active: " + backend.name());
        }

        return backend;
    }

    private static RenderBackend createBackend()
    {
        if (MODE == ARENA)
        {
            try
            {
                return new vertex.render.ArenaBackend();
            }
            catch (Exception unavailable)
            {
                LogWrapper.warning("[Vertex] Arena backend unavailable (" + unavailable.getMessage()
                    + "); trying per-section VBOs");
            }
        }

        if (MODE == VBO || MODE == ARENA)
        {
            try
            {
                return new vertex.render.VboBackend();
            }
            catch (Exception unavailable)
            {
                // Pre-GL1.5 hardware: the managed pipeline still works, just without
                // buffer objects. Same meshes, display-list representation.
                LogWrapper.warning("[Vertex] VBO backend unavailable (" + unavailable.getMessage()
                    + "); using the display-list backend");
            }
        }

        return new DisplayListBackend();
    }

    private static double interp(double prev, double now, double partial)
    {
        return prev + (now - prev) * partial;
    }

    private static Object vertexState(Object tessellator, float camX, float camY, float camZ)
    {
        try
        {
            return tessGetVertexState.invoke(tessellator,
                Float.valueOf(camX), Float.valueOf(camY), Float.valueOf(camZ));
        }
        catch (InvocationTargetException emptyPass)
        {
            // Vanilla getVertexState sizes a PriorityQueue by quad count and rejects
            // zero; a started translucent pass can legitimately tessellate no quads.
            return null;
        }
        catch (Exception emptyPass)
        {
            return null;
        }
    }

    private static void sanitize(Object tessellator)
    {
        try
        {
            tessReset.invoke(tessellator);
            tessIsDrawing.setBoolean(tessellator, false);
            tessSetTranslation.invoke(tessellator, Double.valueOf(0.0D), Double.valueOf(0.0D), Double.valueOf(0.0D));
        }
        catch (Exception e)
        {
            LogWrapper.warning("[Vertex] Could not sanitize a capture tessellator: " + e);
        }
    }

    private static void abandonCapture()
    {
        Object tessellator = captureTessellator;
        captureRenderer = null;
        captureTessellator = null;

        if (tessellator != null)
        {
            sanitize(tessellator);
        }
    }

    private static double entityX(Object entity) throws Exception
    {
        return entityPosX.getDouble(entity);
    }

    private static double entityY(Object entity) throws Exception
    {
        return entityPosY.getDouble(entity);
    }

    private static double entityZ(Object entity) throws Exception
    {
        return entityPosZ.getDouble(entity);
    }

    /**
     * Disable-path recovery: every section re-marks dirty and re-queues, so the vanilla
     * renderer rebuilds the display lists the managed backend owned. Near sections drain
     * first through vanilla's distance-sorted update pass; the world heals in seconds.
     */
    private static void markAllSectionsDirty(Object renderGlobal)
    {
        try
        {
            Field worldRenderers = accessible(renderGlobal.getClass(), Mappings.RG_WORLD_RENDERERS);
            Object[] grid = (Object[])worldRenderers.get(renderGlobal);

            if (grid == null)
            {
                return;
            }

            int marked = markSectionsDirty(renderGlobal, java.util.Arrays.asList(grid));
            LogWrapper.info("[Vertex] Managed renderer fallback: re-marked " + marked + " sections for vanilla rebuild");
        }
        catch (Exception e)
        {
            LogWrapper.severe("[Vertex] Fallback re-mark failed; stale sections heal as they are touched");
            e.printStackTrace();
        }
    }

    /**
     * Re-mark the given sections dirty and queue them, set-backed (a 17k-section grid
     * against an ArrayList contains() would be quadratic). Shared by the disable
     * fallback (whole grid) and arena compaction (one block's residents).
     */
    private static int markSectionsDirty(Object renderGlobal, Iterable<Object> sections)
    {
        try
        {
            Field toUpdateField = accessible(renderGlobal.getClass(), Mappings.RG_WORLD_RENDERERS_TO_UPDATE);
            @SuppressWarnings("unchecked")
            List<Object> toUpdate = (List<Object>)toUpdateField.get(renderGlobal);

            if (toUpdate == null)
            {
                return 0;
            }

            IdentityHashMap<Object, Boolean> queued = new IdentityHashMap<Object, Boolean>();

            for (Object entry : toUpdate)
            {
                queued.put(entry, Boolean.TRUE);
            }

            int marked = 0;

            for (Object renderer : sections)
            {
                if (renderer instanceof ImmediateMarker && wrNeedsUpdate != null)
                {
                    wrNeedsUpdate.setBoolean(renderer, true);

                    if (queued.put(renderer, Boolean.TRUE) == null)
                    {
                        toUpdate.add(renderer);
                    }

                    ++marked;
                }
            }

            return marked;
        }
        catch (Exception e)
        {
            disable("markSectionsDirty", e);
            return 0;
        }
    }

    private static synchronized boolean ready(Object renderer) throws Exception
    {
        if (initialized)
        {
            return true;
        }

        Class<?> wr = renderer.getClass();
        wrGlRenderList = accessible(wr, Mappings.WR_GL_RENDER_LIST);
        wrBytesDrawn = accessible(wr, Mappings.WR_BYTES_DRAWN);
        wrVertexState = accessible(wr, Mappings.WR_VERTEX_STATE);
        wrSkipRenderPass = accessible(wr, Mappings.WR_SKIP_RENDER_PASS);
        wrNeedsUpdate = accessible(wr, Mappings.WR_NEEDS_UPDATE);
        wrPosX = accessible(wr, Mappings.WR_POS_X);
        wrPosY = accessible(wr, Mappings.WR_POS_Y);
        wrPosZ = accessible(wr, Mappings.WR_POS_Z);
        Object tessellator = VertexTessellator.get();
        Class<?> tess = tessellator.getClass();
        tessStartQuads = tess.getMethod(Mappings.TESS_START_QUADS);
        tessSetTranslation = tess.getMethod(Mappings.TESS_SET_TRANSLATION, Double.TYPE, Double.TYPE, Double.TYPE);
        tessGetVertexState = tess.getMethod(Mappings.TESS_GET_VERTEX_STATE, Float.TYPE, Float.TYPE, Float.TYPE);
        tessReset = tess.getDeclaredMethod(Mappings.TESS_RESET);
        tessReset.setAccessible(true);
        tessIsDrawing = accessible(tess, Mappings.TESS_IS_DRAWING);
        initialized = true;
        LogWrapper.info("[Vertex] Section-mesh pipeline initialized");
        return true;
    }

    private static synchronized void submitReady(Object renderGlobal) throws Exception
    {
        if (submitReady)
        {
            return;
        }

        Class<?> rg = renderGlobal.getClass();
        rgGlRenderLists = accessible(rg, Mappings.RG_GL_RENDER_LISTS);
        rgMc = accessible(rg, Mappings.RG_MC);
        Class<?> mc = rgMc.getType();
        mcEntityRenderer = accessible(mc, Mappings.MC_ENTITY_RENDERER);
        mcViewEntity = accessible(mc, Mappings.MINECRAFT_RENDER_VIEW_ENTITY);
        Class<?> er = mcEntityRenderer.getType();
        erEnableLightmap = er.getDeclaredMethod(Mappings.ER_ENABLE_LIGHTMAP, Double.TYPE);
        erEnableLightmap.setAccessible(true);
        erDisableLightmap = er.getDeclaredMethod(Mappings.ER_DISABLE_LIGHTMAP, Double.TYPE);
        erDisableLightmap.setAccessible(true);
        Class<?> entity = mcViewEntity.getType();

        while (entity.getSuperclass() != Object.class)
        {
            entity = entity.getSuperclass();
        }

        entityPosX = accessible(entity, Mappings.ENTITY_POS_X);
        entityPosY = accessible(entity, Mappings.ENTITY_POS_Y);
        entityPosZ = accessible(entity, Mappings.ENTITY_POS_Z);
        entityPrevX = accessible(entity, Mappings.ENTITY_PREV_POS_X);
        entityPrevY = accessible(entity, Mappings.ENTITY_PREV_POS_Y);
        entityPrevZ = accessible(entity, Mappings.ENTITY_PREV_POS_Z);
        submitReady = true;
    }

    private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static void disable(String where, Exception e)
    {
        if (!disabled)
        {
            disabled = true;
            remarkRequested = true;
            LogWrapper.severe("[Vertex] Managed renderer disabled after failure in " + where + "; falling back to vanilla display lists");
            e.printStackTrace();
        }
    }

    private VertexRenderer()
    {
    }
}
