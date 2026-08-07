package vertex.transform;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vertex.Mappings;
import vertex.hooks.VertexHooks;

public class RenderGlobalPatchTest implements Opcodes
{
    @Test
    public void clampsRenderDistanceToLegacyAllocationRange()
    {
        assertEquals(2, VertexHooks.clampLegacyRenderDistance(-1));
        assertEquals(2, VertexHooks.clampLegacyRenderDistance(2));
        assertEquals(8, VertexHooks.clampLegacyRenderDistance(8));
        assertEquals(16, VertexHooks.clampLegacyRenderDistance(16));
        assertEquals(16, VertexHooks.clampLegacyRenderDistance(23));
    }

    @Test
    public void injectsClampAtLoadRenderersHead()
    {
        ClassNode cls = new ClassNode();
        new ClassReader(RenderGlobalPatch.apply(renderGlobalStub())).accept(cls, 0);
        MethodNode load = null;

        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(Mappings.RG_LOAD_RENDERERS) && method.desc.equals(Mappings.RG_LOAD_RENDERERS_DESC))
            {
                load = method;
                break;
            }
        }

        int clampCalls = 0;

        for (AbstractInsnNode insn = load.instructions.getFirst(); insn != null; insn = insn.getNext())
        {
            if (insn instanceof MethodInsnNode)
            {
                MethodInsnNode call = (MethodInsnNode)insn;

                if (call.owner.equals("vertex/hooks/VertexHooks") && call.name.equals("clampLegacyRenderDistance")
                    && call.desc.equals("(I)I"))
                {
                    ++clampCalls;
                }
            }
        }

        assertEquals(1, clampCalls);
    }

    private static byte[] renderGlobalStub()
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, Mappings.RENDER_GLOBAL, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE, Mappings.RG_MC, "L" + Mappings.MINECRAFT + ";", null, null).visitEnd();
        empty(cw, Mappings.RG_MARK_BLOCK_FOR_UPDATE, Mappings.RG_MARK_BLOCK_FOR_UPDATE_DESC, RETURN);
        empty(cw, Mappings.RG_MARK_BLOCKS_FOR_UPDATE, Mappings.RG_MARK_BLOCKS_FOR_UPDATE_DESC, RETURN);
        empty(cw, Mappings.RG_LOAD_RENDERERS, Mappings.RG_LOAD_RENDERERS_DESC, RETURN);

        MethodVisitor update = cw.visitMethod(ACC_PUBLIC, Mappings.RG_UPDATE_RENDERERS, Mappings.RG_UPDATE_RENDERERS_DESC, null, null);
        update.visitCode();
        update.visitInsn(ICONST_0);
        update.visitInsn(IRETURN);
        update.visitMaxs(0, 0);
        update.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void empty(ClassWriter cw, String name, String desc, int returnOpcode)
    {
        MethodVisitor method = cw.visitMethod(ACC_PUBLIC, name, desc, null, null);
        method.visitCode();
        method.visitInsn(returnOpcode);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
