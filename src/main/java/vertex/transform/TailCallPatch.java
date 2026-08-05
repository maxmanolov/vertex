package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Inserts a static no-arg hook call before every RETURN of a void target method, so the
 * hook observes the method's completed work on all exit paths (multi-return vanilla
 * methods included).
 */
final class TailCallPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        if (!desc.endsWith(")V"))
        {
            throw new IllegalStateException("Tail-call target must return void: " + method + desc);
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
                        candidate.instructions.insertBefore(insn, new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, "()V", false));
                        ++patched;
                    }
                }
            }
        }

        if (patched == 0)
        {
            throw new IllegalStateException("Tail-call patch matched no return sites in " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private TailCallPatch()
    {
    }
}
