package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Timing bracket: injects hookOwner.enter(phase) at the head of a method and
 * hookOwner.exit(phase) before every return instruction, tagging both with a small
 * constant phase id so one hook class can time several methods. Works on any return
 * type: the injected calls push and consume only their own int, so the value already
 * on the stack at a return site is untouched.
 *
 * Exceptions escape without an exit call by design; the hook treats a re-entered
 * phase as a dropped sample rather than paying for try/finally weaving on a
 * profiling-only path.
 */
final class BracketPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, int phase)
    {
        if (phase < 0 || phase > 127)
        {
            throw new IllegalStateException("Bracket phase id must fit a BIPUSH: " + phase);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean entered = false;
        int exits = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (!candidate.name.equals(method) || !candidate.desc.equals(desc))
            {
                continue;
            }

            InsnList head = new InsnList();
            head.add(new IntInsnNode(BIPUSH, phase));
            head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, "enter", "(I)V", false));
            candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
            entered = true;

            for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                int opcode = insn.getOpcode();

                if (opcode >= IRETURN && opcode <= RETURN)
                {
                    InsnList exit = new InsnList();
                    exit.add(new IntInsnNode(BIPUSH, phase));
                    exit.add(new MethodInsnNode(INVOKESTATIC, hookOwner, "exit", "(I)V", false));
                    candidate.instructions.insertBefore(insn, exit);
                    ++exits;
                }
            }
        }

        if (!entered || exits == 0)
        {
            throw new IllegalStateException("Bracket patch matched no method or no return sites for " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private BracketPatch()
    {
    }
}
