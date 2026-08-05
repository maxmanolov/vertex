package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Routes an int-returning method's result through a static adjuster at every return site:
 * the adjuster receives (originalResult, arg2, arg3, arg4) where the args are the three
 * int parameters following the method's first reference parameter - the shape of
 * Block.getMixedBrightnessForBlock(world, x, y, z).
 */
final class ReturnValuePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        if (!desc.endsWith(")I"))
        {
            throw new IllegalStateException("Return-value target must return int: " + method + desc);
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
                    if (insn.getOpcode() == IRETURN)
                    {
                        // Instance method (Lref;III)I: this=0, world=1, x=2, y=3, z=4.
                        InsnList adjust = new InsnList();
                        adjust.add(new VarInsnNode(ILOAD, 2));
                        adjust.add(new VarInsnNode(ILOAD, 3));
                        adjust.add(new VarInsnNode(ILOAD, 4));
                        adjust.add(new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, "(IIII)I", false));
                        candidate.instructions.insertBefore(insn, adjust);
                        ++patched;
                    }
                }
            }
        }

        if (patched == 0)
        {
            throw new IllegalStateException("Return-value patch matched no return sites in " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private ReturnValuePatch()
    {
    }
}
