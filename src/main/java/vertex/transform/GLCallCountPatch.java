package vertex.transform;

import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Sweeps every game class, rerouting glEnable/glDisable/glBindTexture calls through the
 * VertexGLStats counting wrappers (identical descriptors, so the rewrite is a one-word
 * owner/name change per site). Gated by the same cheap constant-pool needle scan approach
 * as the Tessellator redirect: classes that never mention GL11 pay one byte search.
 */
public final class GLCallCountPatch implements Opcodes
{
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String HOOK = "vertex/hooks/VertexGLStats";
    private static final byte[] NEEDLE = needle();
    private static final AtomicInteger sites = new AtomicInteger();

    public static int rewrittenSites()
    {
        return sites.get();
    }

    public static byte[] process(byte[] basicClass)
    {
        if (!contains(basicClass, NEEDLE))
        {
            return basicClass;
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int rewritten = 0;

        for (MethodNode method : cls.methods)
        {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn.getOpcode() == INVOKESTATIC)
                {
                    MethodInsnNode call = (MethodInsnNode)insn;

                    if (call.owner.equals(GL11))
                    {
                        if (call.name.equals("glEnable") && call.desc.equals("(I)V"))
                        {
                            reroute(call, "enable");
                            ++rewritten;
                        }
                        else if (call.name.equals("glDisable") && call.desc.equals("(I)V"))
                        {
                            reroute(call, "disable");
                            ++rewritten;
                        }
                        else if (call.name.equals("glBindTexture") && call.desc.equals("(II)V"))
                        {
                            reroute(call, "bindTexture");
                            ++rewritten;
                        }
                    }
                }
            }
        }

        if (rewritten == 0)
        {
            return basicClass;
        }

        sites.addAndGet(rewritten);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static void reroute(MethodInsnNode call, String hookName)
    {
        call.owner = HOOK;
        call.name = hookName;
    }

    private static boolean contains(byte[] bytes, byte[] needle)
    {
        outer:
        for (int i = 0; i <= bytes.length - needle.length; ++i)
        {
            for (int j = 0; j < needle.length; ++j)
            {
                if (bytes[i + j] != needle[j])
                {
                    continue outer;
                }
            }

            return true;
        }

        return false;
    }

    private static byte[] needle()
    {
        byte[] name = GL11.getBytes();
        byte[] out = new byte[name.length + 2];
        out[0] = (byte)(name.length >> 8);
        out[1] = (byte)name.length;
        System.arraycopy(name, 0, out, 2, name.length);
        return out;
    }

    private GLCallCountPatch()
    {
    }
}
