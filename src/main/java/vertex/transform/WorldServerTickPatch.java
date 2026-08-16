package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/** Installs the scheduled-tick index and narrows the chunk-query iterator. */
final class WorldServerTickPatch implements Opcodes
{
    private static final String HOOK = "vertex/hooks/VertexScheduledTicks";

    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int lifecycleReturns = 0;
        int queryAnchors = 0;

        for (MethodNode method : cls.methods)
        {
            boolean lifecycle = method.name.equals("<init>")
                || method.name.equals(Mappings.WS_INITIALIZE) && method.desc.equals(Mappings.WS_INITIALIZE_DESC);

            if (lifecycle)
            {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
                {
                    if (insn.getOpcode() == RETURN)
                    {
                        InsnList tail = new InsnList();
                        tail.add(new VarInsnNode(ALOAD, 0));
                        tail.add(new MethodInsnNode(INVOKESTATIC, HOOK, "install", "(Ljava/lang/Object;)V", false));
                        method.instructions.insertBefore(insn, tail);
                        ++lifecycleReturns;
                    }
                }
            }

            if (method.name.equals(Mappings.WS_GET_PENDING_TICKS)
                && method.desc.equals(Mappings.WS_GET_PENDING_TICKS_DESC))
            {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
                {
                    if (!(insn instanceof MethodInsnNode))
                    {
                        continue;
                    }

                    MethodInsnNode call = (MethodInsnNode)insn;
                    AbstractInsnNode fieldInsn = call.getPrevious();
                    AbstractInsnNode loadInsn = fieldInsn == null ? null : fieldInsn.getPrevious();

                    if (call.getOpcode() == INVOKEVIRTUAL && call.owner.equals("java/util/TreeSet")
                        && call.name.equals("iterator") && call.desc.equals("()Ljava/util/Iterator;")
                        && fieldInsn instanceof FieldInsnNode && fieldInsn.getOpcode() == GETFIELD
                        && ((FieldInsnNode)fieldInsn).owner.equals(cls.name)
                        && ((FieldInsnNode)fieldInsn).name.equals(Mappings.WS_PENDING_TICK_TREE)
                        && ((FieldInsnNode)fieldInsn).desc.equals("Ljava/util/TreeSet;")
                        && loadInsn instanceof VarInsnNode && loadInsn.getOpcode() == ALOAD
                        && ((VarInsnNode)loadInsn).var == 0)
                    {
                        InsnList replacement = new InsnList();
                        replacement.add(new VarInsnNode(ALOAD, 0));
                        replacement.add(new VarInsnNode(ALOAD, 1));
                        replacement.add(new MethodInsnNode(INVOKESTATIC, HOOK, "candidateIterator",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Iterator;", false));
                        method.instructions.insertBefore(loadInsn, replacement);
                        method.instructions.remove(loadInsn);
                        method.instructions.remove(fieldInsn);
                        method.instructions.remove(call);
                        ++queryAnchors;
                        break;
                    }
                }
            }
        }

        if (lifecycleReturns < 2 || queryAnchors != 1)
        {
            throw new IllegalStateException("WorldServer scheduled-tick patch incomplete: lifecycleReturns="
                + lifecycleReturns + " queryAnchors=" + queryAnchors);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private WorldServerTickPatch()
    {
    }
}
