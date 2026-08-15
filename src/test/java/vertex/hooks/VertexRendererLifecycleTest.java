package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vertex.api.ImmediateMarker;
import vertex.api.MeshHost;
import vertex.multicore.BuildQueue;
import vertex.render.MeshData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Managed section-mesh lifecycle invariants, in the same reflective fake-driven style as
 * VertexMulticoreLifecycleTest: worker-side tessellator recycling after a capture, the
 * zero-pass exemption in the pipeline-mismatch gate (the eternal-requeue regression from
 * the first managed soak), fail-fast on builds that lost their meshes, and the
 * set-backed disable-recovery re-mark.
 */
public class VertexRendererLifecycleTest
{
    public static final class FakeTess
    {
        public boolean x;
        public int resets;

        private void d()
        {
            ++this.resets;
        }

        public void b(double tx, double ty, double tz)
        {
        }
    }

    public static final class FakeRenderer implements ImmediateMarker, MeshHost
    {
        public boolean q;
        public int c;
        public int d;
        public int e;
        public final List<Object> x = new ArrayList<Object>();
        public Object mesh;

        public void vertex$markImmediate()
        {
        }

        public boolean vertex$needsImmediate()
        {
            return false;
        }

        public void vertex$clearImmediate()
        {
        }

        public boolean vertex$isDirty()
        {
            return this.q;
        }

        public void vertex$rebuild(Object viewEntity)
        {
        }

        public void vertex$setupTranslation()
        {
        }

        public Object vertex$mesh()
        {
            return this.mesh;
        }

        public void vertex$setMesh(Object state)
        {
            this.mesh = state;
        }
    }

    /** Field-name stand-in for RenderGlobal: v = worldRenderers, t = the update queue. */
    public static final class FakeRenderGlobal
    {
        public Object[] v;
        public List<Object> t = new ArrayList<Object>();
    }

    private final String[] multicoreSaved = {"tessReset", "tessIsDrawing", "tessSetTranslation",
        "wrNeedsUpdate", "wrTileEntityRenderers", "wrPosX", "wrPosY", "wrPosZ", "queue"};
    private Object[] multicoreSavedValues;
    private Object savedRendererNeedsUpdate;

    @Before
    public void arm() throws Exception
    {
        this.multicoreSavedValues = new Object[this.multicoreSaved.length];

        for (int i = 0; i < this.multicoreSaved.length; ++i)
        {
            this.multicoreSavedValues[i] = getStatic(VertexMulticore.class, this.multicoreSaved[i]);
        }

        setStatic(VertexMulticore.class, "tessReset", FakeTess.class.getDeclaredMethod("d"));
        ((Method)getStatic(VertexMulticore.class, "tessReset")).setAccessible(true);
        Field drawing = FakeTess.class.getDeclaredField("x");
        drawing.setAccessible(true);
        setStatic(VertexMulticore.class, "tessIsDrawing", drawing);
        setStatic(VertexMulticore.class, "tessSetTranslation",
            FakeTess.class.getDeclaredMethod("b", double.class, double.class, double.class));
        setStatic(VertexMulticore.class, "wrNeedsUpdate", FakeRenderer.class.getField("q"));
        setStatic(VertexMulticore.class, "wrTileEntityRenderers", FakeRenderer.class.getField("x"));
        setStatic(VertexMulticore.class, "wrPosX", FakeRenderer.class.getField("c"));
        setStatic(VertexMulticore.class, "wrPosY", FakeRenderer.class.getField("d"));
        setStatic(VertexMulticore.class, "wrPosZ", FakeRenderer.class.getField("e"));
        this.savedRendererNeedsUpdate = getStatic(VertexRenderer.class, "wrNeedsUpdate");
        setStatic(VertexRenderer.class, "wrNeedsUpdate", FakeRenderer.class.getField("q"));
        pool().clear();
    }

    @After
    public void disarm() throws Exception
    {
        for (int i = 0; i < this.multicoreSaved.length; ++i)
        {
            setStatic(VertexMulticore.class, this.multicoreSaved[i], this.multicoreSavedValues[i]);
        }

        setStatic(VertexRenderer.class, "wrNeedsUpdate", this.savedRendererNeedsUpdate);
        pool().clear();
    }

    @Test
    public void workerCaptureRecycleClearsTheSlotSoReleaseCannotDoublePool() throws Exception
    {
        FakeTess tess = new FakeTess();
        tess.x = true;
        VertexMulticore.ChunkBuild build = newBuild(new FakeRenderer());
        tessellators(build)[0] = tess;

        Method release = VertexMulticore.class.getDeclaredMethod("releaseCapturedSlot",
            VertexMulticore.ChunkBuild.class, int.class);
        release.setAccessible(true);
        release.invoke(null, build, Integer.valueOf(0));

        assertEquals("worker-side recycle pools the tessellator", 1, pool().size());
        assertFalse("drawing flag cleared before pooling", tess.x);
        assertNull("slot cleared", tessellators(build)[0]);

        invokeReleaseBuild(build);
        assertEquals("terminal release must not double-pool the recycled slot", 1, pool().size());
        assertEquals("no second reset", 1, tess.resets);
    }

    @Test
    public void zeroPassBuildsInstallUnderEitherPipeline() throws Exception
    {
        // The first managed soak's regression: 4,618 empty sections pinned dirty because
        // the mismatch gate discarded builds that never captured anything. A zero-pass
        // build has no geometry and must apply (clearing the section) regardless of mode.
        FakeRenderer renderer = new FakeRenderer();
        VertexMulticore.ChunkBuild build = newBuild(renderer);
        BuildQueue.Sink sink = (BuildQueue.Sink)getStatic(VertexMulticore.class, "SINK");

        assertTrue("zero-pass build must apply, not discard", sink.apply(build));
        assertFalse("an applied build must not re-dirty its section", renderer.q);
    }

    @Test
    public void contentBuildsFromTheWrongPipelineDiscardAndRequeue() throws Exception
    {
        // managedCapture=true while the pipeline runs vanilla - since the arena
        // promotion the ambient mode is managed, so this direction is exactly the
        // post-disable window: force it, and the meshes have nowhere to install, so
        // the build discards and the section re-marks for a vanilla rebuild.
        FakeRenderer renderer = new FakeRenderer();
        VertexMulticore.ChunkBuild build = newBuild(renderer);
        build.managedCapture = true;
        setInt(build, "capturedPasses", 1);
        BuildQueue.Sink sink = (BuildQueue.Sink)getStatic(VertexMulticore.class, "SINK");
        setStatic(VertexRenderer.class, "disabled", Boolean.TRUE);

        try
        {
            assertFalse("mismatched content build must not apply", sink.apply(build));
            assertTrue("discard must re-dirty the section", renderer.q);
        }
        finally
        {
            setStatic(VertexRenderer.class, "disabled", Boolean.FALSE);
        }
    }

    @Test
    public void installRejectsABuildWhoseMeshesWereLost() throws Exception
    {
        VertexMulticore.ChunkBuild build = newBuild(new FakeRenderer());
        build.managedCapture = true;
        setInt(build, "completedPasses", 1);

        try
        {
            VertexRenderer.installWorkerBuild(build);
            fail("a managed build without meshes must fail fast");
        }
        catch (IllegalStateException expected)
        {
        }
    }

    @Test
    public void meshSlotsClearOnTerminalRelease() throws Exception
    {
        VertexMulticore.ChunkBuild build = newBuild(new FakeRenderer());
        build.meshes[0] = new MeshData(new int[0], 0, 7, true, true, true, false);
        build.vertexStates[0] = new Object();
        invokeReleaseBuild(build);
        assertNull("meshes must not outlive the build", build.meshes[0]);
        assertNull(build.vertexStates[0]);
    }

    @Test
    public void disableRecoveryRemarksEverySectionWithoutDuplicatingQueueEntries() throws Exception
    {
        FakeRenderGlobal renderGlobal = new FakeRenderGlobal();
        FakeRenderer[] grid = new FakeRenderer[64];

        for (int i = 0; i < grid.length; ++i)
        {
            grid[i] = new FakeRenderer();
        }

        renderGlobal.v = grid;
        // One section already queued: the re-mark must not add it twice.
        renderGlobal.t.add(grid[7]);

        Method remark = VertexRenderer.class.getDeclaredMethod("markAllSectionsDirty", Object.class);
        remark.setAccessible(true);
        remark.invoke(null, renderGlobal);

        assertEquals("every section queued exactly once", grid.length, renderGlobal.t.size());
        assertSame(grid[7], renderGlobal.t.get(0));

        for (FakeRenderer renderer : grid)
        {
            assertTrue("every section re-marked dirty", renderer.q);
        }
    }

    @Test
    public void settingsRemarkConsumesAtMostOncePerRequest() throws Exception
    {
        // Drain any request left over from another test before asserting.
        VertexRenderer.consumeSettingsRemark();
        assertFalse("no request pending initially", VertexRenderer.consumeSettingsRemark());

        VertexRenderer.requestSettingsRemark();
        assertTrue("a request consumes once", VertexRenderer.consumeSettingsRemark());
        assertFalse("and only once", VertexRenderer.consumeSettingsRemark());
    }

    @Test
    public void settingsRemarkMarksEverySectionWithoutBackendInvolvement() throws Exception
    {
        FakeRenderGlobal renderGlobal = new FakeRenderGlobal();
        FakeRenderer[] grid = new FakeRenderer[16];

        for (int i = 0; i < grid.length; ++i)
        {
            grid[i] = new FakeRenderer();
        }

        renderGlobal.v = grid;
        VertexRenderer.remarkAllSections(renderGlobal);

        assertEquals("every section queued", grid.length, renderGlobal.t.size());

        for (FakeRenderer renderer : grid)
        {
            assertTrue("every section re-marked dirty", renderer.q);
        }
    }

    private static VertexMulticore.ChunkBuild newBuild(Object renderer) throws Exception
    {
        java.lang.reflect.Constructor<VertexMulticore.ChunkBuild> ctor =
            VertexMulticore.ChunkBuild.class.getDeclaredConstructor(
                Object.class, int.class, int.class, Object.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(renderer, 0, 0, null, 0, 0, 0);
    }

    private static Object[] tessellators(VertexMulticore.ChunkBuild build) throws Exception
    {
        Field field = VertexMulticore.ChunkBuild.class.getDeclaredField("passTessellators");
        field.setAccessible(true);
        return (Object[])field.get(build);
    }

    private static void invokeReleaseBuild(VertexMulticore.ChunkBuild build) throws Exception
    {
        Method method = VertexMulticore.class.getDeclaredMethod("releaseBuild", VertexMulticore.ChunkBuild.class);
        method.setAccessible(true);
        method.invoke(null, build);
    }

    private static void setInt(Object target, String name, int value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentLinkedQueue<Object> pool() throws Exception
    {
        return (ConcurrentLinkedQueue<Object>)getStatic(VertexMulticore.class, "tessellatorPool");
    }

    private static Object getStatic(Class<?> owner, String name) throws Exception
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStatic(Class<?> owner, String name, Object value) throws Exception
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
