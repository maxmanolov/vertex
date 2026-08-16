package vertex.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

public final class ActiveSectionPatchTest implements Opcodes
{
    @Test
    public void addsDirectWorldRendererStateBridges()
    {
        ClassWriter source = new ClassWriter(0);
        source.visit(V1_6, ACC_PUBLIC, Mappings.WORLD_RENDERER, null, "java/lang/Object", null);
        source.visitField(ACC_PUBLIC, Mappings.WR_SKIP_RENDER_PASS, "[Z", null, null).visitEnd();
        source.visitField(ACC_PUBLIC, Mappings.WR_CENTER_X, "I", null, null).visitEnd();
        source.visitField(ACC_PUBLIC, Mappings.WR_CENTER_Y, "I", null, null).visitEnd();
        source.visitField(ACC_PUBLIC, Mappings.WR_CENTER_Z, "I", null, null).visitEnd();
        source.visitEnd();

        ClassNode cls = new ClassNode();
        new ClassReader(ActiveSectionPatch.apply(source.toByteArray())).accept(cls, 0);
        assertTrue(cls.interfaces.contains("vertex/api/ActiveSection"));
        assertTrue(hasMethod(cls, "vertex$hasMesh", "()Z"));
        assertTrue(hasMethod(cls, "vertex$centerX", "()I"));
        assertTrue(hasMethod(cls, "vertex$centerY", "()I"));
        assertTrue(hasMethod(cls, "vertex$centerZ", "()I"));
    }

    @Test
    public void routesOnlyRenderConsumerArraysAndAddsReset()
    {
        ClassNode cls = new ClassNode();
        new ClassReader(ActiveRenderArrayPatch.apply(renderGlobalStub())).accept(cls, 0);
        int sortedBridges = 0;
        int gridBridges = 0;
        int resets = 0;

        for (MethodNode method : cls.methods)
        {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn instanceof MethodInsnNode)
                {
                    MethodInsnNode call = (MethodInsnNode)insn;

                    if (call.owner.equals(Mappings.RENDER_GLOBAL) && call.name.equals("vertex$activeSorted"))
                    {
                        ++sortedBridges;
                    }
                    else if (call.owner.equals(Mappings.RENDER_GLOBAL) && call.name.equals("vertex$activeGrid"))
                    {
                        ++gridBridges;
                    }
                    else if (call.owner.equals("vertex/hooks/VertexActiveSections") && call.name.equals("reset"))
                    {
                        ++resets;
                    }
                }
            }
        }

        assertEquals(3, sortedBridges);
        assertEquals(1, gridBridges);
        assertEquals(1, resets);
    }

    private static boolean hasMethod(ClassNode cls, String name, String desc)
    {
        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(name) && method.desc.equals(desc))
            {
                return true;
            }
        }

        return false;
    }

    private static byte[] renderGlobalStub()
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, Mappings.RENDER_GLOBAL, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE, Mappings.RG_SORTED_RENDERERS, "[Lblo;", null, null).visitEnd();
        cw.visitField(ACC_PRIVATE, Mappings.RG_WORLD_RENDERERS, "[Lblo;", null, null).visitEnd();
        empty(cw, Mappings.RG_LOAD_RENDERERS, Mappings.RG_LOAD_RENDERERS_DESC, RETURN, null);
        arrayMethod(cw, Mappings.RG_SORT_AND_RENDER, Mappings.RG_SORT_AND_RENDER_DESC,
            Mappings.RG_SORTED_RENDERERS, IRETURN);
        arrayMethod(cw, "a", "(II)V", Mappings.RG_SORTED_RENDERERS, RETURN);
        arrayMethod(cw, "a", "(IIID)I", Mappings.RG_SORTED_RENDERERS, IRETURN);
        arrayMethod(cw, Mappings.RG_CLIP_FRUSTUM, Mappings.RG_CLIP_FRUSTUM_DESC,
            Mappings.RG_WORLD_RENDERERS, RETURN);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void arrayMethod(ClassWriter cw, String name, String desc, String field, int returnOpcode)
    {
        MethodVisitor method = cw.visitMethod(ACC_PUBLIC, name, desc, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, Mappings.RENDER_GLOBAL, field, "[Lblo;");
        method.visitInsn(ARRAYLENGTH);

        if (returnOpcode == RETURN)
        {
            method.visitInsn(POP);
        }

        method.visitInsn(returnOpcode);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void empty(ClassWriter cw, String name, String desc, int opcode, Object ignored)
    {
        MethodVisitor method = cw.visitMethod(ACC_PUBLIC, name, desc, null, null);
        method.visitCode();
        method.visitInsn(opcode);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
