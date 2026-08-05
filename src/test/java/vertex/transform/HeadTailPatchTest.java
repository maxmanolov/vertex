package vertex.transform;

import org.junit.Before;
import org.junit.Test;
import vertex.TransformerHarness;
import vertex.TransformerHarness.ByteLoader;
import vertex.TransformerHarness.Probe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HeadTailPatchTest
{
    private static final String PROBE = "vertex/TransformerHarness$Probe";

    @Before
    public void reset()
    {
        Probe.reset();
    }

    @Test
    public void headPatchDeliversTheInstanceAndPreservesTheBody() throws Exception
    {
        byte[] bytes = TransformerHarness.voidMethodClass("target0", "run", "()V");
        bytes = HeadInstanceCallPatch.apply(bytes, "run", "()V", PROBE, "head");
        Class<?> cls = new ByteLoader().add("target0", bytes).loadClass("target0");
        Object instance = cls.newInstance();
        cls.getMethod("run").invoke(instance);
        assertEquals(1, Probe.headCalls);
        assertSame(instance, Probe.received);
        assertTrue(cls.getField("ran").getBoolean(null));
    }

    @Test
    public void tailPatchRunsAfterTheBodyOnEveryExit() throws Exception
    {
        byte[] bytes = TransformerHarness.voidMethodClass("target1", "run", "()V");
        bytes = TailCallPatch.apply(bytes, "run", "()V", PROBE, "tail");
        Class<?> cls = new ByteLoader().add("target1", bytes).loadClass("target1");
        cls.getMethod("run").invoke(cls.newInstance());
        assertEquals(1, Probe.tailCalls);
        assertTrue(cls.getField("ran").getBoolean(null));
    }

    @Test(expected = IllegalStateException.class)
    public void tailPatchRefusesMissingTargets() throws Exception
    {
        TailCallPatch.apply(TransformerHarness.voidMethodClass("target2", "run", "()V"), "absent", "()V", PROBE, "tail");
    }
}
