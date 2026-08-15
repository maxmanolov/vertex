package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites every GETFIELD owner.name:desc in the patched class into a static hook call
 * that receives the field's holder and decides the value: the stack shape is identical
 * (one reference consumed, one primitive produced), so the rewrite composes with any
 * surrounding control flow. This is the decoupling primitive for vanilla behavior that
 * keys off a shared settings field (fancyGraphics) which Vertex overrides per consumer.
 */
final class FieldReadReroutePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String fieldOwner, String fieldName, String fieldDesc,
        String hookOwner, String hookName)
    {
        if (fieldDesc.startsWith("L") || fieldDesc.startsWith("["))
        {
            throw new IllegalStateException("Field-read reroute supports primitive fields only: " + fieldDesc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        String hookDesc = "(Ljava/lang/Object;)" + fieldDesc;
        int rerouted = 0;

        for (MethodNode method : cls.methods)
        {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; )
            {
                AbstractInsnNode next = insn.getNext();

                if (insn.getOpcode() == GETFIELD)
                {
                    FieldInsnNode read = (FieldInsnNode) insn;

                    if (read.owner.equals(fieldOwner) && read.name.equals(fieldName)
                        && read.desc.equals(fieldDesc))
                    {
                        method.instructions.set(insn,
                            new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, hookDesc, false));
                        ++rerouted;
                    }
                }

                insn = next;
            }
        }

        if (rerouted == 0)
        {
            throw new IllegalStateException("Field-read reroute matched no " + fieldOwner + "."
                + fieldName + " reads");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private FieldReadReroutePatch()
    {
    }
}
