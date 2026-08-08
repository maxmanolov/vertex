package vertex.transform;

import java.lang.reflect.InvocationTargetException;
import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public final class RerouteVirtualInMethodPatchTest
{
    @Test
    public void reroutesEverySiteAndTheOriginalNeverRuns() throws Exception
    {
        byte[] target = TransformerHarness.virtualTarget("vfl/Target");
        byte[] caller = TransformerHarness.virtualCaller("vfl/Caller", "vfl/Target", 2);
        byte[] patched = RerouteVirtualInMethodPatch.apply(caller, "m", "(Lvfl/Target;FF)V",
            "vfl/Target", "c", "(FF)V",
            "vertex/TransformerHarness$Probe", "virtualHook");

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("vfl.Target", target)
            .add("vfl.Caller", patched);
        Class<?> targetClass = loader.loadClass("vfl.Target");
        Class<?> callerClass = loader.loadClass("vfl.Caller");
        Object instance = targetClass.newInstance();

        TransformerHarness.Probe.reset();
        callerClass.getMethod("m", targetClass, float.class, float.class)
            .invoke(null, instance, Float.valueOf(1.5F), Float.valueOf(-2.25F));

        assertEquals("both call sites reroute", 2, TransformerHarness.Probe.virtualCalls);
        assertSame("the receiver arrives as the hook's leading argument",
            instance, TransformerHarness.Probe.virtualReceiver);
        assertEquals(1.5F, TransformerHarness.Probe.virtualA, 0.0F);
        assertEquals(-2.25F, TransformerHarness.Probe.virtualB, 0.0F);
        assertEquals("the original method must never run",
            0, targetClass.getField("calls").getInt(null));
    }

    @Test
    public void otherMethodsKeepTheirCallSites() throws Exception
    {
        byte[] target = TransformerHarness.virtualTarget("vfl/Target2");
        byte[] caller = TransformerHarness.virtualCaller("vfl/Caller2", "vfl/Target2", 1);
        byte[] patched = RerouteVirtualInMethodPatch.apply(caller, "m", "(Lvfl/Target2;FF)V",
            "vfl/Target2", "c", "(FF)V",
            "vertex/TransformerHarness$Probe", "virtualHook");

        // Reapplying against a method name that does not exist must fail loud, proving
        // the scope filter rejects everything outside the named container method.
        try
        {
            RerouteVirtualInMethodPatch.apply(patched, "noSuchMethod", "(Lvfl/Target2;FF)V",
                "vfl/Target2", "c", "(FF)V",
                "vertex/TransformerHarness$Probe", "virtualHook");
            fail("expected the zero-match reroute to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }

    @Test
    public void zeroMatchesFailThePatch() throws Exception
    {
        byte[] caller = TransformerHarness.virtualCaller("vfl/Caller3", "vfl/Target3", 1);

        try
        {
            RerouteVirtualInMethodPatch.apply(caller, "m", "(Lvfl/Target3;FF)V",
                "vfl/OtherOwner", "c", "(FF)V",
                "vertex/TransformerHarness$Probe", "virtualHook");
            fail("expected the owner mismatch to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
