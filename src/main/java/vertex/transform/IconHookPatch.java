package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/**
 * Routes RenderBlocks' world-aware getBlockIcon(Block, IBlockAccess, x, y, z, side)
 * result through VertexIcons.adjust at every return site. Instance method: this=0,
 * block=1, world=2, x=3, y=4, z=5, side=6.
 */
final class IconHookPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int patched = 0;

        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(Mappings.RB_GET_BLOCK_ICON) && method.desc.equals(Mappings.RB_GET_BLOCK_ICON_DESC))
            {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
                {
                    if (insn.getOpcode() == ARETURN)
                    {
                        InsnList adjust = new InsnList();
                        adjust.add(new VarInsnNode(ALOAD, 1));
                        adjust.add(new VarInsnNode(ALOAD, 2));
                        adjust.add(new VarInsnNode(ILOAD, 3));
                        adjust.add(new VarInsnNode(ILOAD, 4));
                        adjust.add(new VarInsnNode(ILOAD, 5));
                        adjust.add(new VarInsnNode(ILOAD, 6));
                        adjust.add(new MethodInsnNode(INVOKESTATIC, "vertex/hooks/VertexIcons", "adjust",
                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;IIII)Ljava/lang/Object;", false));
                        adjust.add(new TypeInsnNode(CHECKCAST, Mappings.IICON));
                        method.instructions.insertBefore(insn, adjust);
                        ++patched;
                    }
                }
            }
        }

        if (patched == 0)
        {
            throw new IllegalStateException("Icon hook matched no return sites");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private IconHookPatch()
    {
    }
}
