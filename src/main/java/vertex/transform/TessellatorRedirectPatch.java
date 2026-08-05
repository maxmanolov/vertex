package vertex.transform;

import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import vertex.Mappings;

/**
 * Rewrites every read of the global Tessellator.instance (bmh.a, public static final)
 * into VertexTessellator.get(), and tail-patches Tessellator's own static initializer to
 * hand the constructed instance to VertexTessellator.bind(). This is the load-bearing
 * prerequisite for multi-core chunk building: once every consumer resolves the shared
 * tessellator through one owned lookup, that lookup can become per-thread.
 *
 * The field is final and only written by bmh.<clinit>, so reads are the entire coupling
 * surface. Tessellator's own internal reads are left native (same object by definition;
 * revisited in phase 2). A cheap constant-pool byte scan gates the full parse so the
 * ~2000 classes that never mention bmh pay one array search at load.
 */
public final class TessellatorRedirectPatch implements Opcodes
{
    private static final byte[] NEEDLE = buildNeedle();
    private static final AtomicInteger sites = new AtomicInteger();
    private static final AtomicInteger classes = new AtomicInteger();

    public static int rewrittenSites()
    {
        return sites.get();
    }

    /** Applies to every non-Vertex class; returns the input untouched when irrelevant. */
    public static byte[] process(String name, byte[] basicClass)
    {
        if (Mappings.TESSELLATOR.equals(name))
        {
            return bindInClinit(basicClass);
        }

        if (!containsNeedle(basicClass))
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
                if (insn.getOpcode() == GETSTATIC)
                {
                    FieldInsnNode field = (FieldInsnNode)insn;

                    if (field.owner.equals(Mappings.TESSELLATOR) && field.name.equals(Mappings.TESSELLATOR_INSTANCE))
                    {
                        // The original GETSTATIC is kept and discarded: reading the field is
                        // what triggers Tessellator.<clinit> (which calls bind). A bare
                        // INVOKESTATIC has no such side effect and returned null for any
                        // consumer that ran before the class initialized.
                        InsnList replacement = new InsnList();
                        replacement.add(new InsnNode(POP));
                        replacement.add(new MethodInsnNode(INVOKESTATIC, "vertex/hooks/VertexTessellator", "get", "()Ljava/lang/Object;", false));
                        replacement.add(new TypeInsnNode(CHECKCAST, Mappings.TESSELLATOR));
                        AbstractInsnNode next = insn.getNext();
                        method.instructions.insert(insn, replacement);
                        insn = next.getPrevious();
                        ++rewritten;
                    }
                }
                else if (insn.getOpcode() == PUTSTATIC)
                {
                    FieldInsnNode field = (FieldInsnNode)insn;

                    if (field.owner.equals(Mappings.TESSELLATOR) && field.name.equals(Mappings.TESSELLATOR_INSTANCE))
                    {
                        // The field is final; a foreign write means the class file lies. Refuse.
                        throw new IllegalStateException("Unexpected external write to Tessellator.instance in " + name);
                    }
                }
            }
        }

        if (rewritten == 0)
        {
            return basicClass;
        }

        sites.addAndGet(rewritten);
        classes.incrementAndGet();
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] bindInClinit(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean patched = false;

        for (MethodNode method : cls.methods)
        {
            if (method.name.equals("<clinit>"))
            {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
                {
                    if (insn.getOpcode() == RETURN)
                    {
                        InsnList tail = new InsnList();
                        tail.add(new FieldInsnNode(GETSTATIC, Mappings.TESSELLATOR, Mappings.TESSELLATOR_INSTANCE, "L" + Mappings.TESSELLATOR + ";"));
                        tail.add(new MethodInsnNode(INVOKESTATIC, "vertex/hooks/VertexTessellator", "bind", "(Ljava/lang/Object;)V", false));
                        method.instructions.insertBefore(insn, tail);
                        patched = true;
                    }
                }
            }
        }

        if (!patched)
        {
            throw new IllegalStateException("Tessellator has no <clinit> to patch");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static boolean containsNeedle(byte[] bytes)
    {
        outer:
        for (int i = 0; i <= bytes.length - NEEDLE.length; ++i)
        {
            for (int j = 0; j < NEEDLE.length; ++j)
            {
                if (bytes[i + j] != NEEDLE[j])
                {
                    continue outer;
                }
            }

            return true;
        }

        return false;
    }

    private static byte[] buildNeedle()
    {
        // CONSTANT_Utf8 payload for the class name: 2-byte big-endian length, then the text.
        byte[] name = Mappings.TESSELLATOR.getBytes();
        byte[] needle = new byte[name.length + 2];
        needle[0] = (byte)(name.length >> 8);
        needle[1] = (byte)name.length;
        System.arraycopy(name, 0, needle, 2, name.length);
        return needle;
    }

    private TessellatorRedirectPatch()
    {
    }
}
