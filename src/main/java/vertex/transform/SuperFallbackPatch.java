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
 * Guarded super-delegation for (III)I overrides: when the guard hook returns false the
 * method returns super's result and the subclass body never runs. This is the "turn a
 * biome's special-case color off" shape - the base class computes the standard colormap
 * result, so falling through composes with every other colormap interception.
 */
final class SuperFallbackPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String guardName)
    {
        if (!"(III)I".equals(desc))
        {
            throw new IllegalStateException("Super fallback only supports (III)I: " + desc);
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
                head.add(new JumpInsnNode(IFNE, body));
                head.add(new VarInsnNode(ALOAD, 0));
                head.add(new VarInsnNode(ILOAD, 1));
                head.add(new VarInsnNode(ILOAD, 2));
                head.add(new VarInsnNode(ILOAD, 3));
                head.add(new MethodInsnNode(INVOKESPECIAL, cls.superName, method, desc, false));
                head.add(new InsnNode(IRETURN));
                head.add(body);
                candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
                patched = true;
            }
        }

        if (!patched)
        {
            throw new IllegalStateException("Super fallback found no method " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private SuperFallbackPatch()
    {
    }
}
