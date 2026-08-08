package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vertex.api.ImmediateMarker;
import vertex.multicore.BuildQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic lifecycle invariants for the multicore pipeline (#69 audit): exactly one
 * terminal disposition per borrowed tessellator, no double release, no pooling of a
 * tessellator that resists sanitization, and idempotent teardown. The fake tessellator
 * mirrors the obfuscated member names (reset=d(), isDrawing=x, setTranslation=b(DDD)),
 * bytecode-verified against the 1.7.10 client in the #69 investigation.
 */
public class VertexMulticoreLifecycleTest
{
    public static final class FakeTess
    {
        public boolean x;
        public int resets;
        public boolean failReset;

        private void d()
        {
            ++this.resets;

            if (this.failReset)
            {
                throw new IllegalStateException("reset failed");
            }
        }

        public void b(double tx, double ty, double tz)
        {
        }
    }

    public static final class FakeRenderer implements ImmediateMarker
    {
        public boolean q;
        public final List<Object> x = new ArrayList<Object>();

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
    }

    public static final class FakeRenderGlobal
    {
        public final List<Object> t = new ArrayList<Object>();
    }

    private Object savedQueue;
    private Object savedNeedsUpdate;
    private Object savedTileEntityRenderers;
    private Object savedRenderGlobalRef;
    private Object savedRenderersToUpdateField;

    @Before
    public void arm() throws Exception
    {
        set("tessReset", FakeTess.class.getDeclaredMethod("d"));
        ((Method) get("tessReset")).setAccessible(true);
        Field x = FakeTess.class.getDeclaredField("x");
        x.setAccessible(true);
        set("tessIsDrawing", x);
        set("tessSetTranslation", FakeTess.class.getDeclaredMethod("b", double.class, double.class, double.class));
        savedQueue = get("queue");
        savedNeedsUpdate = get("wrNeedsUpdate");
        savedTileEntityRenderers = get("wrTileEntityRenderers");
        savedRenderGlobalRef = get("renderGlobalRef");
        savedRenderersToUpdateField = get("renderersToUpdateField");
        set("wrNeedsUpdate", FakeRenderer.class.getField("q"));
        set("wrTileEntityRenderers", FakeRenderer.class.getField("x"));
        pool().clear();
        inFlight().clear();
    }

    @After
    public void disarm() throws Exception
    {
        set("tessReset", null);
        set("tessIsDrawing", null);
        set("tessSetTranslation", null);
        set("queue", savedQueue);
        set("wrNeedsUpdate", savedNeedsUpdate);
        set("wrTileEntityRenderers", savedTileEntityRenderers);
        set("renderGlobalRef", savedRenderGlobalRef);
        set("renderersToUpdateField", savedRenderersToUpdateField);
        set("workers", null);
        Field tornDown = VertexMulticore.class.getDeclaredField("tornDown");
        tornDown.setAccessible(true);
        tornDown.setBoolean(null, false);
        Field disabled = VertexMulticore.class.getDeclaredField("disabled");
        disabled.setAccessible(true);
        disabled.setBoolean(null, false);
        pool().clear();
        inFlight().clear();
    }

    @Test
    public void releaseBuildSanitizesPoolsAndIsIdempotent() throws Exception
    {
        FakeTess first = new FakeTess();
        first.x = true;
        FakeTess second = new FakeTess();
        second.x = true;
        VertexMulticore.ChunkBuild build = newBuild();
        tessellators(build)[0] = first;
        tessellators(build)[1] = second;

        releaseBuild(build);
        assertEquals("both tessellators pooled", 2, pool().size());
        assertFalse("drawing flag cleared", first.x);
        assertFalse(second.x);
        assertEquals("buffer reset exactly once", 1, first.resets);
        assertEquals(1, second.resets);
        assertNull("slots cleared so nothing can double-pool", tessellators(build)[0]);

        releaseBuild(build);
        assertEquals("second release must be a no-op", 2, pool().size());
        assertEquals("no double reset", 1, first.resets);
    }

    @Test
    public void releaseBuildDropsATessellatorThatResistsSanitization() throws Exception
    {
        FakeTess poisoned = new FakeTess();
        poisoned.x = true;
        poisoned.failReset = true;
        FakeTess clean = new FakeTess();
        VertexMulticore.ChunkBuild build = newBuild();
        tessellators(build)[0] = poisoned;
        tessellators(build)[1] = clean;

        releaseBuild(build);
        assertEquals("only the provably clean tessellator is pooled", 1, pool().size());
        assertTrue("the poisoned one is dropped, never pooled", pool().peek() == clean);
    }

    @Test
    public void teardownRunsOnceReleasesTheBacklogAndClearsAllState() throws Exception
    {
        BuildQueue queue = new BuildQueue();
        set("queue", queue);
        set("workers", null);
        FakeTess captured = new FakeTess();
        captured.x = true;
        VertexMulticore.ChunkBuild finished = newBuild();
        tessellators(finished)[0] = captured;
        queue.complete(finished);
        inFlight().put(new Object(), finished);
        pool().add(new FakeTess());

        Method teardown = VertexMulticore.class.getDeclaredMethod("teardown");
        teardown.setAccessible(true);
        teardown.invoke(null);

        assertEquals("finished backlog released through the discard path", false, captured.x);
        assertEquals(1, captured.resets);
        assertTrue("inFlight cleared", inFlight().isEmpty());
        assertTrue("pool cleared", pool().isEmpty());
        assertNull("queue dropped for GC", get("queue"));

        // Second teardown must be a pure no-op even with the queue already null.
        teardown.invoke(null);
        assertNull(get("queue"));
    }

    @Test
    public void discardRestoresTheTileEntityListFromBeforeTheWorkerBuild() throws Exception
    {
        Object previous = new Object();
        Object uncommitted = new Object();
        FakeRenderer renderer = new FakeRenderer();
        renderer.x.add(uncommitted);
        VertexMulticore.ChunkBuild build = newBuild(renderer);
        build.previousTileEntityRenderers = new ArrayList<Object>();
        build.previousTileEntityRenderers.add(previous);

        BuildQueue.Sink sink = (BuildQueue.Sink)get("SINK");
        sink.discard(build, false);

        assertEquals(1, renderer.x.size());
        assertTrue(renderer.x.get(0) == previous);
        assertTrue("the vanilla path must rebuild the discarded section", renderer.q);
    }

    @Test
    public void obsoleteBuildDoesNotEnterTheReplacementGrid() throws Exception
    {
        FakeRenderer renderer = new FakeRenderer();
        VertexMulticore.ChunkBuild build = newBuild(renderer);
        FakeRenderGlobal renderGlobal = new FakeRenderGlobal();
        set("renderGlobalRef", new java.lang.ref.WeakReference<Object>(renderGlobal));
        set("renderersToUpdateField", null);

        BuildQueue.Sink sink = (BuildQueue.Sink)get("SINK");
        sink.discard(build, true);

        assertFalse("a retired renderer must not be marked dirty", renderer.q);
        assertTrue("a retired renderer must not enter the new update list",
            renderGlobal.t.isEmpty());
    }

    private static VertexMulticore.ChunkBuild newBuild() throws Exception
    {
        return newBuild(new Object());
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
        return (Object[]) field.get(build);
    }

    private static void releaseBuild(VertexMulticore.ChunkBuild build) throws Exception
    {
        Method method = VertexMulticore.class.getDeclaredMethod("releaseBuild", VertexMulticore.ChunkBuild.class);
        method.setAccessible(true);
        method.invoke(null, build);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentLinkedQueue<Object> pool() throws Exception
    {
        return (ConcurrentLinkedQueue<Object>) get("tessellatorPool");
    }

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<Object, Object> inFlight() throws Exception
    {
        return (IdentityHashMap<Object, Object>) get("inFlight");
    }

    private static Object get(String name) throws Exception
    {
        Field field = VertexMulticore.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void set(String name, Object value) throws Exception
    {
        Field field = VertexMulticore.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
