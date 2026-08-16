package vertex.transform;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import vertex.TransformerHarness;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/** The class-wide field-read reroute, primitive and reference shapes. */
public final class FieldReadReroutePatchTest implements Opcodes
{
    /**
     * A holder with `public boolean flag` and `public String label`, plus readers
     * returning each through a plain GETFIELD.
     */
    private static byte[] holder(String internalName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC, "flag", "Z", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC, "label", "Ljava/lang/String;", null, null).visitEnd();

        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor readFlag = cw.visitMethod(ACC_PUBLIC, "readFlag", "()Z", null, null);
        readFlag.visitCode();
        readFlag.visitVarInsn(ALOAD, 0);
        readFlag.visitFieldInsn(GETFIELD, internalName, "flag", "Z");
        readFlag.visitInsn(IRETURN);
        readFlag.visitMaxs(0, 0);
        readFlag.visitEnd();

        MethodVisitor readLabel = cw.visitMethod(ACC_PUBLIC, "readLabel", "()Ljava/lang/String;", null, null);
        readLabel.visitCode();
        readLabel.visitVarInsn(ALOAD, 0);
        readLabel.visitFieldInsn(GETFIELD, internalName, "label", "Ljava/lang/String;");
        readLabel.visitInsn(ARETURN);
        readLabel.visitMaxs(0, 0);
        readLabel.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public static Object lastOwner;
    public static boolean boolAnswer;
    public static String refAnswer;

    public static boolean boolHook(Object owner)
    {
        lastOwner = owner;
        return boolAnswer;
    }

    public static Object refHook(Object owner)
    {
        lastOwner = owner;
        return refAnswer;
    }

    @Test
    public void primitiveReadsRouteThroughTheHook() throws Exception
    {
        byte[] patched = FieldReadReroutePatch.apply(holder("fr/HolderP"),
            "fr/HolderP", "flag", "Z",
            "vertex/transform/FieldReadReroutePatchTest", "boolHook");
        Class<?> cls = new TransformerHarness.ByteLoader().add("fr.HolderP", patched)
            .loadClass("fr.HolderP");
        Object instance = cls.getDeclaredConstructor().newInstance();

        cls.getField("flag").setBoolean(instance, false);
        boolAnswer = true;
        lastOwner = null;

        assertEquals(Boolean.TRUE, cls.getMethod("readFlag").invoke(instance));
        assertSame("the hook receives the field holder", instance, lastOwner);
    }

    @Test
    public void referenceReadsRouteThroughTheHookWithTheCastRestored() throws Exception
    {
        byte[] patched = FieldReadReroutePatch.apply(holder("fr/HolderR"),
            "fr/HolderR", "label", "Ljava/lang/String;",
            "vertex/transform/FieldReadReroutePatchTest", "refHook");
        Class<?> cls = new TransformerHarness.ByteLoader().add("fr.HolderR", patched)
            .loadClass("fr.HolderR");
        Object instance = cls.getDeclaredConstructor().newInstance();

        cls.getField("label").set(instance, "vanilla");
        refAnswer = "override";
        lastOwner = null;

        // The reader returns the hook's value (checkcast intact), not the field's.
        assertEquals("override", cls.getMethod("readLabel").invoke(instance));
        assertSame(instance, lastOwner);
        assertEquals("the real field stays untouched", "vanilla", cls.getField("label").get(instance));

        // Null passes through the checkcast unharmed.
        refAnswer = null;
        assertEquals(null, cls.getMethod("readLabel").invoke(instance));
    }

    @Test
    public void zeroMatchesFail()
    {
        try
        {
            FieldReadReroutePatch.apply(holder("fr/HolderZ"),
                "fr/Other", "flag", "Z",
                "vertex/transform/FieldReadReroutePatchTest", "boolHook");
            fail("expected the owner mismatch to throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }
}
