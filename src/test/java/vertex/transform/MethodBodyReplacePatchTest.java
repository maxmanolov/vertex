package vertex.transform;

import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class MethodBodyReplacePatchTest
{
    @Test
    public void replacedBodyDelegatesAndTheOriginalComputationIsGone() throws Exception
    {
        byte[] target = TransformerHarness.floatStaticMethodClass("mb/Trig", "a");
        byte[] patched = MethodBodyReplacePatch.apply(target, "a", "(F)F",
            "vertex/TransformerHarness$Probe", "adjustFloat");

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("mb.Trig", patched);
        TransformerHarness.Probe.floatCalls = 0;
        float result = ((Float)loader.loadClass("mb.Trig").getMethod("a", float.class)
            .invoke(null, Float.valueOf(3.0F))).floatValue();

        // The original doubled; the replacement adds one instead: 4, not 6 and not 7.
        assertEquals(4.0F, result, 0.0001F);
        assertEquals(1, TransformerHarness.Probe.floatCalls);
    }

    @Test
    public void missingMethodFails()
    {
        byte[] target = TransformerHarness.floatStaticMethodClass("mb/Trig2", "a");

        try
        {
            MethodBodyReplacePatch.apply(target, "z", "(F)F",
                "vertex/TransformerHarness$Probe", "adjustFloat");
            fail("expected the missing-method case to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
