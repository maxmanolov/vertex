package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Conditional replacement for the biome color blenders - instance methods shaped
 * (world, x, y, z) -> int that average a neighborhood of biome colors. When the guard
 * says the fast path is active, the hook computes the single center sample and the
 * blending body never runs; otherwise the body executes untouched. The sample kind is
 * baked into the call site so one hook serves grass-family and foliage-family blenders.
 */
final class CenterSampleOverridePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, int sampleKind,
        String hookOwner, String guardName, String sampleName)
    {
        if (!desc.endsWith(")I") || !desc.startsWith("(L"))
        {
            throw new IllegalStateException("Center-sample target must be (ref,III)I: " + desc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean patched = false;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals(desc))
            {
                InsnList head = new InsnList();
                LabelNode body = new LabelNode();
                head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, guardName, "()Z", false));
                head.add(new JumpInsnNode(IFEQ, body));
                head.add(new VarInsnNode(ALOAD, 1));
                head.add(new VarInsnNode(ILOAD, 2));
                head.add(new VarInsnNode(ILOAD, 3));
                head.add(new VarInsnNode(ILOAD, 4));
                head.add(new IntInsnNode(BIPUSH, sampleKind));
                head.add(new MethodInsnNode(INVOKESTATIC, hookOwner, sampleName,
                    "(Ljava/lang/Object;IIII)I", false));
                head.add(new InsnNode(IRETURN));
                head.add(body);
                candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
                patched = true;
            }
        }

        if (!patched)
        {
            throw new IllegalStateException("Center-sample override found no method " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private CenterSampleOverridePatch()
    {
    }
}
