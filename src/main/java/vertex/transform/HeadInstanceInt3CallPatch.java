package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Injects a static (Object,int,int,int) hook at an instance method head. */
final class HeadInstanceInt3CallPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        if (!desc.equals("(III)V"))
        {
            throw new IllegalStateException("Three-int head hook requires (III)V: " + method + desc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int patched = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals(desc))
            {
                InsnList head = new InsnList();
                head.add(new VarInsnNode(ALOAD, 0));
                head.add(new VarInsnNode(ILOAD, 1));
                head.add(new VarInsnNode(ILOAD, 2));
                head.add(new VarInsnNode(ILOAD, 3));
                head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, "(Ljava/lang/Object;III)V", false));
                candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
                ++patched;
            }
        }

        if (patched != 1)
        {
            throw new IllegalStateException("Three-int head hook matched " + patched + " methods for " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private HeadInstanceInt3CallPatch()
    {
    }
}
