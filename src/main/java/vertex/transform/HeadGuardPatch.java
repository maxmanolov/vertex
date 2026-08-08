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
 * Boolean head guard for void instance methods: the hook receives "this" (plus optionally
 * the first int or Object argument) and returning true skips the vanilla body entirely.
 * This is the wrap primitive behind multi-core chunk building: the same method body runs
 * unmodified on whichever thread, and the guard decides per-thread what happens instead.
 */
final class HeadGuardPatch implements Opcodes
{
    static final int THIS_ONLY = 0;
    static final int THIS_AND_INT = 1;
    static final int THIS_AND_OBJECT = 2;
    static final int THIS_INT_DOUBLE = 3;

    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName, int shape)
    {
        if (!desc.endsWith(")V"))
        {
            throw new IllegalStateException("Head guard target must return void: " + method + desc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean patched = false;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals(desc))
            {
                InsnList head = new InsnList();
                LabelNode proceed = new LabelNode();
                head.add(new VarInsnNode(ALOAD, 0));
                String hookDesc;

                if (shape == THIS_AND_INT)
                {
                    head.add(new VarInsnNode(ILOAD, 1));
                    hookDesc = "(Ljava/lang/Object;I)Z";
                }
                else if (shape == THIS_AND_OBJECT)
                {
                    head.add(new VarInsnNode(ALOAD, 1));
                    hookDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
                }
                else if (shape == THIS_INT_DOUBLE)
                {
                    head.add(new VarInsnNode(ILOAD, 1));
                    head.add(new VarInsnNode(DLOAD, 2));
                    hookDesc = "(Ljava/lang/Object;ID)Z";
                }
                else
                {
                    hookDesc = "(Ljava/lang/Object;)Z";
                }

                head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, hookDesc, false));
                head.add(new JumpInsnNode(IFEQ, proceed));
                head.add(new InsnNode(RETURN));
                head.add(proceed);
                candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
                patched = true;
            }
        }

        if (!patched)
        {
            throw new IllegalStateException("Head guard found no method " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private HeadGuardPatch()
    {
    }
}
