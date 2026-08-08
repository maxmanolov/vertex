package vertex.transform;

import org.junit.Before;
import org.junit.Test;
import vertex.TransformerHarness;
import vertex.TransformerHarness.ByteLoader;
import vertex.TransformerHarness.Probe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BracketPatchTest
{
    private static final String PROBE = "vertex/TransformerHarness$Probe";

    @Before
    public void reset()
    {
        Probe.reset();
    }

    @Test
    public void bracketPairsAroundEveryReturnAndPreservesTheValue() throws Exception
    {
        byte[] bytes = TransformerHarness.intMethodClass("bracket0", "compute");
        bytes = BracketPatch.apply(bytes, "compute", "(I)I", PROBE, 7);
        Class<?> cls = new ByteLoader().add("bracket0", bytes).loadClass("bracket0");
        assertEquals(10, cls.getMethod("compute", int.class).invoke(null, 5));
        assertEquals(-1, cls.getMethod("compute", int.class).invoke(null, -3));
        assertEquals(2, Probe.enterCalls);
        assertEquals(2, Probe.exitCalls);
        assertEquals(7, Probe.lastPhase);
    }

    @Test
    public void bracketPreservesVoidBodies() throws Exception
    {
        byte[] bytes = TransformerHarness.voidMethodClass("bracket1", "run", "()V");
        bytes = BracketPatch.apply(bytes, "run", "()V", PROBE, 3);
        Class<?> cls = new ByteLoader().add("bracket1", bytes).loadClass("bracket1");
        cls.getMethod("run").invoke(cls.newInstance());
        assertTrue(cls.getField("ran").getBoolean(null));
        assertEquals(1, Probe.enterCalls);
        assertEquals(1, Probe.exitCalls);
    }

    @Test(expected = IllegalStateException.class)
    public void bracketRefusesMissingTargets() throws Exception
    {
        BracketPatch.apply(TransformerHarness.voidMethodClass("bracket2", "run", "()V"), "absent", "()V", PROBE, 1);
    }
}
