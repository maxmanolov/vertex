package vertex.transform;

import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/** Routes render-only array reads through the compact active-section snapshot. */
final class ActiveRenderArrayPatch implements Opcodes
{
    private static final String HOOK = "vertex/hooks/VertexActiveSections";
    private static final String ACTIVE_SORTED = "vertex$activeSorted";
    private static final String ACTIVE_GRID = "vertex$activeGrid";

    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        verifyField(cls, Mappings.RG_SORTED_RENDERERS);
        verifyField(cls, Mappings.RG_WORLD_RENDERERS);
        cls.methods.add(bridge(cls.name, ACTIVE_SORTED, Mappings.RG_SORTED_RENDERERS));
        cls.methods.add(bridge(cls.name, ACTIVE_GRID, Mappings.RG_WORLD_RENDERERS));

        Set<String> sortedMethods = new HashSet<String>();
        sortedMethods.add(Mappings.RG_SORT_AND_RENDER + Mappings.RG_SORT_AND_RENDER_DESC);
        sortedMethods.add("a(II)V");
        sortedMethods.add("a(IIID)I");
        Set<String> patchedSortedMethods = new HashSet<String>();
        int sortedReads = 0;
        int gridReads = 0;
        int resets = 0;
        boolean clipMethod = false;
        boolean loadMethod = false;

        for (MethodNode method : cls.methods)
        {
            String key = method.name + method.desc;
            boolean sortedTarget = sortedMethods.contains(key);
            boolean gridTarget = method.name.equals(Mappings.RG_CLIP_FRUSTUM)
                && method.desc.equals(Mappings.RG_CLIP_FRUSTUM_DESC);
            int methodSortedReads = 0;
            int methodGridReads = 0;

            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; )
            {
                AbstractInsnNode next = insn.getNext();

                if (insn instanceof FieldInsnNode && insn.getOpcode() == GETFIELD)
                {
                    FieldInsnNode field = (FieldInsnNode)insn;

                    if (sortedTarget && field.owner.equals(cls.name)
                        && field.name.equals(Mappings.RG_SORTED_RENDERERS) && field.desc.equals("[Lblo;"))
                    {
                        method.instructions.set(field,
                            new MethodInsnNode(INVOKEVIRTUAL, cls.name, ACTIVE_SORTED, "()[Lblo;", false));
                        ++sortedReads;
                        ++methodSortedReads;
                    }
                    else if (gridTarget && field.owner.equals(cls.name)
                        && field.name.equals(Mappings.RG_WORLD_RENDERERS) && field.desc.equals("[Lblo;"))
                    {
                        method.instructions.set(field,
                            new MethodInsnNode(INVOKEVIRTUAL, cls.name, ACTIVE_GRID, "()[Lblo;", false));
                        ++gridReads;
                        ++methodGridReads;
                    }
                }

                insn = next;
            }

            if (sortedTarget)
            {
                if (methodSortedReads == 0)
                {
                    throw new IllegalStateException("Active sorted-array anchor is missing: " + key);
                }

                patchedSortedMethods.add(key);
            }

            if (gridTarget)
            {
                if (methodGridReads == 0)
                {
                    throw new IllegalStateException("Active frustum-array anchor is missing: " + key);
                }

                clipMethod = true;
            }

            if (method.name.equals(Mappings.RG_LOAD_RENDERERS) && method.desc.equals(Mappings.RG_LOAD_RENDERERS_DESC))
            {
                loadMethod = true;

                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
                {
                    if (insn.getOpcode() == RETURN)
                    {
                        InsnList tail = new InsnList();
                        tail.add(new VarInsnNode(ALOAD, 0));
                        tail.add(new MethodInsnNode(INVOKESTATIC, HOOK, "reset", "(Ljava/lang/Object;)V", false));
                        method.instructions.insertBefore(insn, tail);
                        ++resets;
                    }
                }
            }
        }

        if (!patchedSortedMethods.equals(sortedMethods) || !clipMethod || !loadMethod
            || sortedReads == 0 || gridReads == 0 || resets == 0)
        {
            throw new IllegalStateException("Active render-array patch incomplete: sortedReads=" + sortedReads
                + " gridReads=" + gridReads + " resets=" + resets);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode bridge(String owner, String methodName, String fallbackField)
    {
        MethodNode method = new MethodNode(ACC_PUBLIC, methodName, "()[Lblo;", null, null);
        LabelNode fallback = new LabelNode(new Label());
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, HOOK, "snapshot",
            "(Ljava/lang/Object;)[Ljava/lang/Object;", false));
        method.instructions.add(new InsnNode(DUP));
        method.instructions.add(new JumpInsnNode(IFNULL, fallback));
        method.instructions.add(new TypeInsnNode(CHECKCAST, "[Lblo;"));
        method.instructions.add(new InsnNode(ARETURN));
        method.instructions.add(fallback);
        method.instructions.add(new InsnNode(POP));
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, owner, fallbackField, "[Lblo;"));
        method.instructions.add(new InsnNode(ARETURN));
        return method;
    }

    private static void verifyField(ClassNode cls, String name)
    {
        for (FieldNode field : cls.fields)
        {
            if (field.name.equals(name) && field.desc.equals("[Lblo;"))
            {
                return;
            }
        }

        throw new IllegalStateException("RenderGlobal renderer array is missing: " + cls.name + "." + name);
    }

    private ActiveRenderArrayPatch()
    {
    }
}
