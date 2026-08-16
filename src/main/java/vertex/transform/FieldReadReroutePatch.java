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
 * (one reference consumed, one value produced), so the rewrite composes with any
 * surrounding control flow. Primitive fields call a (Ljava/lang/Object;)prim hook;
 * reference fields call a (Ljava/lang/Object;)Ljava/lang/Object; hook followed by a
 * checkcast back to the declared field type. This is the decoupling primitive for
 * vanilla behavior that keys off a shared field (fancyGraphics, the font texture
 * location) which Vertex overrides per consumer.
 */
final class FieldReadReroutePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String fieldOwner, String fieldName, String fieldDesc,
        String hookOwner, String hookName)
    {
        boolean reference = fieldDesc.startsWith("L") || fieldDesc.startsWith("[");
        String castType = !reference ? null
            : fieldDesc.startsWith("L") ? fieldDesc.substring(1, fieldDesc.length() - 1) : fieldDesc;

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        String hookDesc = reference ? "(Ljava/lang/Object;)Ljava/lang/Object;"
            : "(Ljava/lang/Object;)" + fieldDesc;
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
                        MethodInsnNode call =
                            new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, hookDesc, false);

                        if (reference)
                        {
                            method.instructions.insertBefore(insn, call);
                            method.instructions.set(insn,
                                new org.objectweb.asm.tree.TypeInsnNode(CHECKCAST, castType));
                        }
                        else
                        {
                            method.instructions.set(insn, call);
                        }

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
