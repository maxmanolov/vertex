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
        public static int enterCalls;
        public static int exitCalls;
        public static int lastPhase = -1;
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

        public static void enter(int phase)
        {
            ++enterCalls;
            lastPhase = phase;
        }

        public static void exit(int phase)
        {
            ++exitCalls;
            lastPhase = phase;
        }

        public static boolean guardResult;
        public static int guardInt;
        public static double guardDouble;

        public static boolean guardIntDouble(Object instance, int i, double d)
        {
            received = instance;
            guardInt = i;
            guardDouble = d;
            return guardResult;
        }

        public static int virtualCalls;
        public static Object virtualReceiver;
        public static float virtualA;
        public static float virtualB;

        public static void virtualHook(Object receiver, float a, float b)
        {
            ++virtualCalls;
            virtualReceiver = receiver;
            virtualA = a;
            virtualB = b;
        }

        public static int objectCalls;
        public static Object objectReceiver;
        public static Object objectValue;

        /** Erased-descriptor hook: reference parameters arrive widened to Object. */
        public static void objectHook(Object receiver, Object value)
        {
            ++objectCalls;
            objectReceiver = receiver;
            objectValue = value;
        }

        public static int doubleCalls;

        /** Return-adjust hook: doubles whatever the vanilla method produced. */
        public static double adjustDouble(double value)
        {
            ++doubleCalls;
            return value * 2.0D;
        }

        public static void reset()
        {
            headCalls = 0;
            tailCalls = 0;
            enterCalls = 0;
            exitCalls = 0;
            lastPhase = -1;
            received = null;
            virtualCalls = 0;
            virtualReceiver = null;
            virtualA = 0.0F;
            virtualB = 0.0F;
            objectCalls = 0;
            objectReceiver = null;
            objectValue = null;
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
     * A class with: public static int compute(int x) { if (x < 0) return -1; return x * 2; }
     * Two return sites and a non-void return type, for bracket-patch coverage.
     */
    public static byte[] intMethodClass(String internalName, String methodName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor compute = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, methodName, "(I)I", null, null);
        compute.visitCode();
        org.objectweb.asm.Label positive = new org.objectweb.asm.Label();
        compute.visitVarInsn(ILOAD, 0);
        compute.visitJumpInsn(IFGE, positive);
        compute.visitInsn(ICONST_M1);
        compute.visitInsn(IRETURN);
        compute.visitLabel(positive);
        compute.visitVarInsn(ILOAD, 0);
        compute.visitInsn(ICONST_2);
        compute.visitInsn(IMUL);
        compute.visitInsn(IRETURN);
        compute.visitMaxs(0, 0);
        compute.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * A class with: public void c(FF)V bumping a static call counter and recording both
     * floats; the reroute target stand-in shaped like an obfuscated instance method.
     */
    public static byte[] virtualTarget(String internalName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "calls", "I", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "ax", "F", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "ay", "F", null, null).visitEnd();
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        MethodVisitor c = cw.visitMethod(ACC_PUBLIC, "c", "(FF)V", null, null);
        c.visitCode();
        c.visitFieldInsn(GETSTATIC, internalName, "calls", "I");
        c.visitInsn(ICONST_1);
        c.visitInsn(IADD);
        c.visitFieldInsn(PUTSTATIC, internalName, "calls", "I");
        c.visitVarInsn(FLOAD, 1);
        c.visitFieldInsn(PUTSTATIC, internalName, "ax", "F");
        c.visitVarInsn(FLOAD, 2);
        c.visitFieldInsn(PUTSTATIC, internalName, "ay", "F");
        c.visitInsn(RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * A caller with: public static void m(T, float, float) containing the requested
     * number of INVOKEVIRTUAL T.c(FF)V sites, mirroring updateCameraAndRender's pair of
     * mouse-look call sites.
     */
    public static byte[] virtualCaller(String internalName, String targetName, int sites)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "m",
            "(L" + targetName + ";FF)V", null, null);
        m.visitCode();

        for (int i = 0; i < sites; ++i)
        {
            m.visitVarInsn(ALOAD, 0);
            m.visitVarInsn(FLOAD, 1);
            m.visitVarInsn(FLOAD, 2);
            m.visitMethodInsn(INVOKEVIRTUAL, targetName, "c", "(FF)V", false);
        }

        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** A class with public static double <methodName>() returning the given constant. */
    public static byte[] doubleMethodClass(String internalName, String methodName, double value)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, methodName, "()D", null, null);
        m.visitCode();
        m.visitLdcInsn(Double.valueOf(value));
        m.visitInsn(DRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Target with public void c(Ljava/lang/String;)V recording its argument. */
    public static byte[] virtualObjectTarget(String internalName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "calls", "I", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "arg", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        MethodVisitor c = cw.visitMethod(ACC_PUBLIC, "c", "(Ljava/lang/String;)V", null, null);
        c.visitCode();
        c.visitFieldInsn(GETSTATIC, internalName, "calls", "I");
        c.visitInsn(ICONST_1);
        c.visitInsn(IADD);
        c.visitFieldInsn(PUTSTATIC, internalName, "calls", "I");
        c.visitVarInsn(ALOAD, 1);
        c.visitFieldInsn(PUTSTATIC, internalName, "arg", "Ljava/lang/String;");
        c.visitInsn(RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** A caller with public static void m(T, String) holding one T.c(String) site. */
    public static byte[] virtualObjectCaller(String internalName, String targetName)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "m",
            "(L" + targetName + ";Ljava/lang/String;)V", null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0);
        m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKEVIRTUAL, targetName, "c", "(Ljava/lang/String;)V", false);
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
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

    /**
     * Mimics WorldRenderer's cached-tessellator pattern: a class with its own
     * private static <tess> A assigned in <clinit> from the tessellator's field, and a
     * static readCache()Ljava/lang/Object; returning the cached field.
     */
    public static byte[] cachedFieldConsumer(String internalName, String tessName, String cacheField)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE | ACC_STATIC, cacheField, "L" + tessName + ";", null, null).visitEnd();
        MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitFieldInsn(GETSTATIC, tessName, "a", "L" + tessName + ";");
        clinit.visitFieldInsn(PUTSTATIC, internalName, cacheField, "L" + tessName + ";");
        clinit.visitInsn(RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();
        MethodVisitor read = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "readCache", "()Ljava/lang/Object;", null, null);
        read.visitCode();
        read.visitFieldInsn(GETSTATIC, internalName, cacheField, "L" + tessName + ";");
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
