package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Conditional full override for static (DD)I methods (the colorizer shape): when the
 * guard returns true the override supplies the result and the vanilla body never runs;
 * otherwise the body executes untouched.
 */
final class HeadOverridePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String hookOwner, String guardName, String overrideName)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean patched = false;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals("(DD)I"))
            {
                InsnList head = new InsnList();
                LabelNode vanilla = new LabelNode();
                head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, guardName, "()Z", false));
                head.add(new JumpInsnNode(IFEQ, vanilla));
                head.add(new VarInsnNode(DLOAD, 0));
                head.add(new VarInsnNode(DLOAD, 2));
                head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, overrideName, "(DD)I", false));
                head.add(new InsnNode(IRETURN));
                head.add(vanilla);
                candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
                patched = true;
            }
        }

        if (!patched)
        {
            throw new IllegalStateException("Head override found no method " + method + "(DD)I");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private HeadOverridePatch()
    {
    }
}
