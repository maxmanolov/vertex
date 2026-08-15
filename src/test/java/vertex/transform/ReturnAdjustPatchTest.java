package vertex.transform;

import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class ReturnAdjustPatchTest
{
    @Test
    public void doubleReturnsRouteThroughTheAdjuster() throws Exception
    {
        byte[] target = TransformerHarness.doubleMethodClass("ra/Target", "k", 0.03125D);
        byte[] patched = ReturnAdjustPatch.apply(target, "k", "()D",
            "vertex/TransformerHarness$Probe", "adjustDouble");

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("ra.Target", patched);
        Class<?> targetClass = loader.loadClass("ra.Target");

        TransformerHarness.Probe.doubleCalls = 0;
        double result = ((Double)targetClass.getMethod("k").invoke(null)).doubleValue();

        assertEquals("the adjuster sees the vanilla value and decides the result",
            0.0625D, result, 0.0D);
        assertEquals(1, TransformerHarness.Probe.doubleCalls);
    }

    @Test
    public void zeroMatchesAndReferenceReturnsFail() throws Exception
    {
        byte[] target = TransformerHarness.doubleMethodClass("ra/Target2", "k", 1.0D);

        try
        {
            ReturnAdjustPatch.apply(target, "missing", "()D",
                "vertex/TransformerHarness$Probe", "adjustDouble");
            fail("expected the zero-match patch to throw");
        }
        catch (IllegalStateException expected)
        {
        }

        try
        {
            ReturnAdjustPatch.apply(target, "k", "()Ljava/lang/String;",
                "vertex/TransformerHarness$Probe", "adjustDouble");
            fail("expected the reference return to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
