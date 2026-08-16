package vertex.transform;

import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public final class CenterSampleOverridePatchTest
{
    @Test
    public void guardOffRunsTheBlendAndOnReturnsTheCenterSampleWithItsKind() throws Exception
    {
        byte[] target = TransformerHarness.blendMethodClass("cs/Blender");
        byte[] patched = CenterSampleOverridePatch.apply(target, "d", "(Ljava/lang/Object;III)I", 1,
            "vertex/TransformerHarness$Probe", "centerGuard", "centerSample");

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("cs.Blender", patched);
        Class<?> cls = loader.loadClass("cs.Blender");
        Object instance = cls.newInstance();
        Object world = new Object();
        java.lang.reflect.Method d = cls.getMethod("d", Object.class, int.class, int.class, int.class);

        TransformerHarness.Probe.centerGuardValue = false;
        assertEquals("guard off runs the vanilla blend", 7,
            ((Integer)d.invoke(instance, world, 1, 2, 3)).intValue());
        assertEquals(1, cls.getField("calls").getInt(null));

        TransformerHarness.Probe.centerGuardValue = true;

        try
        {
            TransformerHarness.Probe.centerKind = -1;
            assertEquals("guard on returns the center sample", 42,
                ((Integer)d.invoke(instance, world, 1, 2, 3)).intValue());
            assertEquals("the body must not run again", 1, cls.getField("calls").getInt(null));
            assertEquals("the baked sample kind arrives", 1, TransformerHarness.Probe.centerKind);
            assertSame(world, TransformerHarness.Probe.centerWorld);
        }
        finally
        {
            TransformerHarness.Probe.centerGuardValue = false;
        }
    }

    @Test
    public void missingMethodFails()
    {
        byte[] target = TransformerHarness.blendMethodClass("cs/Blender2");

        try
        {
            CenterSampleOverridePatch.apply(target, "z", "(Ljava/lang/Object;III)I", 0,
                "vertex/TransformerHarness$Probe", "centerGuard", "centerSample");
            fail("expected the missing-method case to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
