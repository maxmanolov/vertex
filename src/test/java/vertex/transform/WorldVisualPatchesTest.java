package vertex.transform;

import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** The two Other-page patch primitives: the int-const replacement and the added override. */
public final class WorldVisualPatchesTest
{
    @Test
    public void intConstReplacementRoutesTheValueThroughTheHook()
    {
        byte[] target = TransformerHarness.intConstMethodClass("wv/Ticker", "u", 900);
        byte[] patched = ReplaceIntConstPatch.apply(target, "u", "()I", 900, 1,
            "vertex/TransformerHarness$Probe", "intConst");

        try
        {
            TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
                .add("wv.Ticker", patched);
            TransformerHarness.Probe.intConstValue = 3600;
            assertEquals(3600, ((Integer)loader.loadClass("wv.Ticker")
                .getMethod("u").invoke(null)).intValue());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void intConstSiteCountMismatchFails()
    {
        byte[] target = TransformerHarness.intConstMethodClass("wv/Ticker2", "u", 900);

        try
        {
            ReplaceIntConstPatch.apply(target, "u", "()I", 900, 2,
                "vertex/TransformerHarness$Probe", "intConst");
            fail("expected the site-count assertion to throw");
        }
        catch (IllegalStateException expected)
        {
        }

        try
        {
            ReplaceIntConstPatch.apply(target, "u", "()I", 901, 1,
                "vertex/TransformerHarness$Probe", "intConst");
            fail("expected the missing-constant case to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }

    @Test
    public void addedOverrideCallsSuperThenTheAdjuster() throws Exception
    {
        byte[] parent = TransformerHarness.floatSuperClass("wv/WorldBase");
        byte[] child = TransformerHarness.floatSubClass("wv/WorldSub", "wv/WorldBase");
        byte[] patched = AddFloatOverridePatch.apply(child, "c", "(F)F",
            "vertex/TransformerHarness$Probe", "adjustFloat");

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("wv.WorldBase", parent)
            .add("wv.WorldSub", patched);
        Class<?> subClass = loader.loadClass("wv.WorldSub");
        Object instance = subClass.newInstance();

        TransformerHarness.Probe.floatCalls = 0;
        float result = ((Float)subClass.getMethod("c", float.class)
            .invoke(instance, Float.valueOf(3.0F))).floatValue();

        // super doubles (6), the adjuster adds one (7).
        assertEquals(7.0F, result, 0.0001F);
        assertEquals(1, TransformerHarness.Probe.floatCalls);

        // The base class alone keeps its vanilla behavior.
        Object base = loader.loadClass("wv.WorldBase").newInstance();
        assertEquals(6.0F, ((Float)loader.loadClass("wv.WorldBase").getMethod("c", float.class)
            .invoke(base, Float.valueOf(3.0F))).floatValue(), 0.0001F);
    }

    @Test
    public void addingAnOverrideThatAlreadyExistsFails() throws Exception
    {
        byte[] parent = TransformerHarness.floatSuperClass("wv/WorldBase2");

        try
        {
            AddFloatOverridePatch.apply(parent, "c", "(F)F",
                "vertex/TransformerHarness$Probe", "adjustFloat");
            fail("expected the existing-method case to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
