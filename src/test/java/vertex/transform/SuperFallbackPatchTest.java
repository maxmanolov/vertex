package vertex.transform;

import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class SuperFallbackPatchTest
{
    @Test
    public void guardOffFallsThroughToSuperAndOnKeepsTheOverride() throws Exception
    {
        byte[] parent = TransformerHarness.intTriSuperClass("sf/Base", "b");
        byte[] child = TransformerHarness.intTriSubClass("sf/Swamp", "sf/Base", "b");
        byte[] patched = SuperFallbackPatch.apply(child, "b", "(III)I",
            "vertex/TransformerHarness$Probe", "guard");

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("sf.Base", parent)
            .add("sf.Swamp", patched);
        Class<?> swampClass = loader.loadClass("sf.Swamp");
        Object swamp = swampClass.newInstance();
        java.lang.reflect.Method method = swampClass.getMethod("b", int.class, int.class, int.class);

        TransformerHarness.Probe.guardValue = true;
        assertEquals("guard on keeps the special case", 2,
            ((Integer)method.invoke(swamp, 0, 0, 0)).intValue());

        TransformerHarness.Probe.guardValue = false;
        assertEquals("guard off falls through to the base result", 1,
            ((Integer)method.invoke(swamp, 0, 0, 0)).intValue());
    }

    @Test
    public void missingMethodFails()
    {
        byte[] child = TransformerHarness.intTriSubClass("sf/Swamp2", "sf/Base2", "b");

        try
        {
            SuperFallbackPatch.apply(child, "z", "(III)I",
                "vertex/TransformerHarness$Probe", "guard");
            fail("expected the missing-method case to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
