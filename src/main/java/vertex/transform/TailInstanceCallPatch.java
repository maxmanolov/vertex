package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Like {@link TailCallPatch}, but the hook receives the instance: before every RETURN of a
 * void instance method, inserts ALOAD 0 + a static hook call taking Object. Used where the
 * hook must observe the finished object state - injecting extra buttons after a screen's
 * initGui has built its own button list.
 */
final class TailInstanceCallPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        if (!desc.endsWith(")V"))
        {
            throw new IllegalStateException("Tail-instance target must return void: " + method + desc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int patched = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals(desc))
            {
                for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; insn = insn.getNext())
                {
                    if (insn.getOpcode() == RETURN)
                    {
                        candidate.instructions.insertBefore(insn, new VarInsnNode(ALOAD, 0));
                        candidate.instructions.insertBefore(insn,
                            new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, "(Ljava/lang/Object;)V", false));
                        ++patched;
                    }
                }
            }
        }

        if (patched == 0)
        {
            throw new IllegalStateException("Tail-instance patch matched no return sites in " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private TailInstanceCallPatch()
    {
    }
}
