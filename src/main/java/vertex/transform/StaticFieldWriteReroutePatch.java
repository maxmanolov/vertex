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
 * Rewrites PUTSTATIC owner.name:desc inside one container method into a static hook
 * call consuming the same stack value: the hook decides what actually lands in the
 * field (reflectively) - the decouple primitive for vanilla's per-frame derived
 * globals like RenderBlocks' fancy-grass flag.
 */
final class StaticFieldWriteReroutePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc,
        String fieldOwner, String fieldName, String fieldDesc,
        String hookOwner, String hookName)
    {
        if (fieldDesc.startsWith("L") || fieldDesc.startsWith("["))
        {
            throw new IllegalStateException("Static write reroute supports primitive fields only: " + fieldDesc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        String hookDesc = "(" + fieldDesc + ")V";
        int rerouted = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (!candidate.name.equals(method) || !candidate.desc.equals(desc))
            {
                continue;
            }

            for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; )
            {
                AbstractInsnNode next = insn.getNext();

                if (insn.getOpcode() == PUTSTATIC)
                {
                    FieldInsnNode write = (FieldInsnNode) insn;

                    if (write.owner.equals(fieldOwner) && write.name.equals(fieldName)
                        && write.desc.equals(fieldDesc))
                    {
                        candidate.instructions.set(insn,
                            new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, hookDesc, false));
                        ++rerouted;
                    }
                }

                insn = next;
            }
        }

        if (rerouted == 0)
        {
            throw new IllegalStateException("Static write reroute matched no " + fieldOwner + "."
                + fieldName + " writes in " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private StaticFieldWriteReroutePatch()
    {
    }
}
