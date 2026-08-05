package vertex;

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Test-side tooling: synthesizes minimal class files shaped like the vanilla targets,
 * and loads patched bytes into an isolated class loader so tests execute the transformed
 * bytecode directly. No Minecraft classes are involved anywhere.
 */
public final class TransformerHarness implements Opcodes
{
    /** Records hook invocations from executed patched bytecode. */
    public static final class Probe
    {
        public static int headCalls;
        public static int tailCalls;
        public static Object received;

        public static void head(Object instance)
        {
            ++headCalls;
            received = instance;
        }

        public static void tail()
        {
            ++tailCalls;
        }

        public static void reset()
        {
            headCalls = 0;
            tailCalls = 0;
            received = null;
        }
    }

    /** Class loader that defines supplied bytes and delegates everything else. */
    public static final class ByteLoader extends ClassLoader
    {
        private final Map<String, byte[]> classes = new HashMap<String, byte[]>();

        public ByteLoader()
        {
            super(TransformerHarness.class.getClassLoader());
        }

        public ByteLoader add(String binaryName, byte[] bytes)
        {
            this.classes.put(binaryName, bytes);
            return this;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException
        {
            byte[] bytes = this.classes.get(name);

            if (bytes == null)
            {
                throw new ClassNotFoundException(name);
            }

            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    /**
     * A class with: public static boolean ran; public void run()V setting ran=true;
     * used as the target for head/tail/skip patches.
     */
    public static byte[] voidMethodClass(String internalName, String methodName, String desc)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "ran", "Z", null, null).visitEnd();
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        MethodVisitor run = cw.visitMethod(ACC_PUBLIC, methodName, desc, null, null);
        run.visitCode();
        run.visitInsn(ICONST_1);
        run.visitFieldInsn(PUTSTATIC, internalName, "ran", "Z");
        run.visitInsn(RETURN);
        run.visitMaxs(0, 0);
        run.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * A stand-in Tessellator: public static final <self> a, assigned in <clinit>;
     * plus a consumer class whose static read()Ljava/lang/Object; returns FakeTess.a.
     */
    public static byte[] fakeTessellator(String internalName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "a", "L" + internalName + ";", null, null).visitEnd();
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitTypeInsn(NEW, internalName);
        clinit.visitInsn(DUP);
        clinit.visitMethodInsn(INVOKESPECIAL, internalName, "<init>", "()V", false);
        clinit.visitFieldInsn(PUTSTATIC, internalName, "a", "L" + internalName + ";");
        clinit.visitInsn(RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static byte[] tessellatorConsumer(String internalName, String tessName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor read = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "read", "()Ljava/lang/Object;", null, null);
        read.visitCode();
        read.visitFieldInsn(GETSTATIC, tessName, "a", "L" + tessName + ";");
        read.visitInsn(ARETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private TransformerHarness()
    {
    }
}
