package vertex;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.Assert.assertTrue;

/**
 * Java 8 runtime compatibility gate for NIO buffer usage. JDK 9 added covariant
 * overrides of the Buffer state methods (clear/flip/position/limit/...) on the typed
 * buffers; compiling on a newer JDK with only -source/-target 8 emits descriptors like
 * IntBuffer.clear()Ljava/nio/IntBuffer; that do not exist on the Java 8 runtime the
 * 1.7.10 client ships with - renderer=vbo and renderer=arena crashed with
 * NoSuchMethodError from exactly this on a newer-JDK build. The source routes those
 * calls through java.nio.Buffer casts and the build passes --release 8 on JDK 9+;
 * this test inspects the actually-compiled bytecode of every NIO-touching class so a
 * regression fails the suite on the machine that would produce the broken jar.
 */
public class NioBufferCompatTest
{
    /** Every production class that touches java.nio buffers. */
    private static final String[] CLASSES = {
        "vertex.render.Staging",
        "vertex.render.ArenaBackend",
        "vertex.render.VboBackend",
        "vertex.render.DisplayListBackend",
        "vertex.render.MeshData",
        "vertex.render.ArenaBatchPlan",
        "vertex.hooks.VertexFrameCapture",
    };

    private static final Set<String> STATE_METHODS = new HashSet<String>(Arrays.asList(
        "clear", "flip", "rewind", "mark", "reset", "position", "limit"));

    @Test
    public void compiledBytecodeUsesOnlyJava8BufferDescriptors() throws Exception
    {
        final List<String> violations = new ArrayList<String>();

        for (final String className : CLASSES)
        {
            InputStream in = getClass().getClassLoader()
                .getResourceAsStream(className.replace('.', '/') + ".class");
            assertTrue("class file present for " + className, in != null);

            try
            {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5)
                {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
                    {
                        return new MethodVisitor(Opcodes.ASM5)
                        {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface)
                            {
                                if (owner.startsWith("java/nio/") && owner.endsWith("Buffer")
                                    && STATE_METHODS.contains(name)
                                    && !desc.endsWith(")Ljava/nio/Buffer;"))
                                {
                                    violations.add(className + " calls " + owner + "." + name + desc);
                                }
                            }
                        };
                    }
                }, 0);
            }
            finally
            {
                in.close();
            }
        }

        assertTrue("Java-8-incompatible buffer descriptors (route the call through a "
            + "java.nio.Buffer cast, and keep --release 8 in the build): " + violations,
            violations.isEmpty());
    }
}
