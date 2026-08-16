package vertex.transform;

import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The boolean-returning THIS_OBJECT_III head-guard shape used by the block dispatch. */
public final class HeadGuardBooleanTest
{
    @Test
    public void guardReceivesAllArgumentsAndFalseKeepsTheBody() throws Exception
    {
        byte[] target = TransformerHarness.boolDispatchClass("hg/Dispatch");
        byte[] patched = HeadGuardPatch.apply(target, "m", "(Ljava/lang/Object;III)Z",
            "vertex/TransformerHarness$Probe", "triGuard", HeadGuardPatch.THIS_OBJECT_III);

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("hg.Dispatch", patched);
        Class<?> cls = loader.loadClass("hg.Dispatch");
        Object instance = cls.newInstance();
        Object block = new Object();
        java.lang.reflect.Method m = cls.getMethod("m", Object.class, int.class, int.class, int.class);

        TransformerHarness.Probe.reset();
        TransformerHarness.Probe.triGuardCalls = 0;
        TransformerHarness.Probe.triGuardValue = false;
        boolean result = ((Boolean)m.invoke(instance, block, 3, 64, -7)).booleanValue();

        assertTrue("body ran and returned its own value", result);
        assertEquals(1, TransformerHarness.Probe.triGuardCalls);
        assertEquals(1, cls.getField("calls").getInt(null));
        assertSame(instance, TransformerHarness.Probe.received);
        assertSame(block, TransformerHarness.Probe.triGuardBlock);
        assertEquals(3, TransformerHarness.Probe.triGuardX);
        assertEquals(64, TransformerHarness.Probe.triGuardY);
        assertEquals(-7, TransformerHarness.Probe.triGuardZ);
    }

    @Test
    public void trueSkipsTheBodyAndReturnsFalse() throws Exception
    {
        byte[] target = TransformerHarness.boolDispatchClass("hg/Dispatch2");
        byte[] patched = HeadGuardPatch.apply(target, "m", "(Ljava/lang/Object;III)Z",
            "vertex/TransformerHarness$Probe", "triGuard", HeadGuardPatch.THIS_OBJECT_III);

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("hg.Dispatch2", patched);
        Class<?> cls = loader.loadClass("hg.Dispatch2");
        Object instance = cls.newInstance();
        java.lang.reflect.Method m = cls.getMethod("m", Object.class, int.class, int.class, int.class);

        TransformerHarness.Probe.triGuardValue = true;

        try
        {
            boolean result = ((Boolean)m.invoke(instance, new Object(), 0, 0, 0)).booleanValue();
            assertFalse("skip returns the drew-nothing convention", result);
            assertEquals("the body must not run", 0, cls.getField("calls").getInt(null));
        }
        finally
        {
            TransformerHarness.Probe.triGuardValue = false;
        }
    }
}
