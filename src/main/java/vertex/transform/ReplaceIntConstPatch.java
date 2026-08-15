package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Replaces a SIPUSH/BIPUSH constant inside one method with a static ()I hook call, so a
 * baked-in magic number (the integrated server's 900-tick autosave interval) becomes a
 * runtime decision. The expected site count is asserted: a vanilla jar where the
 * constant appears more or fewer times than evidenced fails the patch loudly instead of
 * changing an unintended site.
 */
final class ReplaceIntConstPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, int constant,
        int expectedSites, String hookOwner, String hookName)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int replaced = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (!candidate.name.equals(method) || !candidate.desc.equals(desc))
            {
                continue;
            }

            for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; )
            {
                AbstractInsnNode next = insn.getNext();

                if ((insn.getOpcode() == SIPUSH || insn.getOpcode() == BIPUSH)
                    && ((IntInsnNode) insn).operand == constant)
                {
                    candidate.instructions.set(insn,
                        new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, "()I", false));
                    ++replaced;
                }

                insn = next;
            }
        }

        if (replaced != expectedSites)
        {
            throw new IllegalStateException("Int-const patch expected " + expectedSites
                + " site(s) of " + constant + " in " + method + desc + ", found " + replaced);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private ReplaceIntConstPatch()
    {
    }
}
