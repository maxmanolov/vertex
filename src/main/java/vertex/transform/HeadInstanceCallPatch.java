package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Inserts a static hook call receiving the instance ("this") at the head of an instance
 * method: hookOwner.hookName(Object). Used for per-frame entry points where hook code
 * needs the live object to resolve reflective handles from (never Class.forName - see
 * docs/ARCHITECTURE.md on the class-loader split).
 */
final class HeadInstanceCallPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean patched = false;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals(desc))
            {
                InsnList head = new InsnList();
                head.add(new VarInsnNode(ALOAD, 0));
                head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, "(Ljava/lang/Object;)V", false));
                candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
                patched = true;
            }
        }

        if (!patched)
        {
            throw new IllegalStateException("Head-call patch found no method " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private HeadInstanceCallPatch()
    {
    }
}
