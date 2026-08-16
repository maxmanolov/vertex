package vertex.transform;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/**
 * Wraps RenderGlobal.renderClouds with a short-lived display-list cache. A hit returns
 * at the head after replay; a miss compiles and executes the unchanged method, then
 * closes the list at each original return. Original return sites are collected before
 * adding the hit return so the replay path can never close a list it did not open.
 */
final class CloudCachePatch implements Opcodes
{
    private static final String HOOK = "vertex/hooks/VertexCloudCache";

    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int matches = 0;
        int exits = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (!candidate.name.equals(Mappings.RG_RENDER_CLOUDS)
                || !candidate.desc.equals(Mappings.RG_RENDER_CLOUDS_DESC))
            {
                continue;
            }

            ++matches;
            List<AbstractInsnNode> returns = new ArrayList<AbstractInsnNode>();

            for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn.getOpcode() == RETURN)
                {
                    returns.add(insn);
                }
            }

            for (AbstractInsnNode insn : returns)
            {
                InsnList finish = new InsnList();
                finish.add(new VarInsnNode(ALOAD, 0));
                finish.add(new VarInsnNode(FLOAD, 1));
                finish.add(new MethodInsnNode(INVOKESTATIC, HOOK, "finish", "(Ljava/lang/Object;F)V", false));
                candidate.instructions.insertBefore(insn, finish);
                ++exits;
            }

            LabelNode miss = new LabelNode();
            InsnList head = new InsnList();
            head.add(new VarInsnNode(ALOAD, 0));
            head.add(new VarInsnNode(FLOAD, 1));
            head.add(new MethodInsnNode(INVOKESTATIC, HOOK, "replay", "(Ljava/lang/Object;F)Z", false));
            head.add(new JumpInsnNode(IFEQ, miss));
            head.add(new InsnNode(RETURN));
            head.add(miss);
            candidate.instructions.insertBefore(candidate.instructions.getFirst(), head);
        }

        if (matches != 1 || exits == 0)
        {
            throw new IllegalStateException("Cloud cache patch expected one "
                + Mappings.RG_RENDER_CLOUDS + Mappings.RG_RENDER_CLOUDS_DESC
                + " method with return sites; found methods=" + matches + " returns=" + exits);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private CloudCachePatch()
    {
    }
}
