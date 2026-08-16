package vertex.transform;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The static-field write reroute used by the fancy-grass decouple. */
public final class StaticFieldWriteReroutePatchTest implements Opcodes
{
    /** A holder with `public static boolean b` and a writer method assigning true. */
    private static byte[] holder(String internalName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "b", "Z", null, null).visitEnd();
        MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "w", "()V", null, null);
        m.visitCode();
        m.visitInsn(ICONST_1);
        m.visitFieldInsn(PUTSTATIC, internalName, "b", "Z");
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static boolean lastValue;
    public static int hookCalls;

    /** Hook standing in for applyFancyGrass: consumes the value, never writes b. */
    public static void sink(boolean value)
    {
        lastValue = value;
        ++hookCalls;
    }

    @Test
    public void theWriteRoutesThroughTheHookAndTheFieldStaysUntouched() throws Exception
    {
        byte[] patched = StaticFieldWriteReroutePatch.apply(holder("sw/Holder"), "w", "()V",
            "sw/Holder", "b", "Z",
            "vertex/transform/StaticFieldWriteReroutePatchTest", "sink");

        TransformerHarness.ByteLoader loader = new TransformerHarness.ByteLoader()
            .add("sw.Holder", patched);
        Class<?> cls = loader.loadClass("sw.Holder");

        hookCalls = 0;
        lastValue = false;
        cls.getMethod("w").invoke(null);

        assertEquals(1, hookCalls);
        assertTrue("the stack value arrives at the hook", lastValue);
        assertFalse("the original field write must not happen", cls.getField("b").getBoolean(null));
    }

    @Test
    public void zeroMatchesFail()
    {
        try
        {
            StaticFieldWriteReroutePatch.apply(holder("sw/Holder2"), "w", "()V",
                "sw/Other", "b", "Z",
                "vertex/transform/StaticFieldWriteReroutePatchTest", "sink");
            fail("expected the owner mismatch to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
