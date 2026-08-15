package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Routes a method's primitive result through a static (T)T adjuster at every return
 * site. Unlike {@link ReturnValuePatch} (which is shaped around the brightness hook and
 * forwards extra arguments), this is the minimal pass-through form: the adjuster sees
 * only the vanilla result and decides what the caller receives.
 */
final class ReturnAdjustPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        String ret = desc.substring(desc.indexOf(')') + 1);
        int opcode = returnOpcode(ret);
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int patched = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals(desc))
            {
                for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; insn = insn.getNext())
                {
                    if (insn.getOpcode() == opcode)
                    {
                        candidate.instructions.insertBefore(insn, new MethodInsnNode(
                            INVOKESTATIC, hookOwner, hookName, "(" + ret + ")" + ret, false));
                        ++patched;
                    }
                }
            }
        }

        if (patched == 0)
        {
            throw new IllegalStateException("Return-adjust patch matched no return sites in " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static int returnOpcode(String ret)
    {
        if ("I".equals(ret) || "Z".equals(ret) || "B".equals(ret) || "S".equals(ret) || "C".equals(ret))
        {
            return IRETURN;
        }

        if ("F".equals(ret))
        {
            return FRETURN;
        }

        if ("D".equals(ret))
        {
            return DRETURN;
        }

        if ("J".equals(ret))
        {
            return LRETURN;
        }

        throw new IllegalStateException("Return-adjust target must return a primitive: " + ret);
    }

    private ReturnAdjustPatch()
    {
    }
}
