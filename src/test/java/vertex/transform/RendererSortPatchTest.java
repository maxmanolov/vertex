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

public final class RendererSortPatchTest implements Opcodes
{
    @Test
    public void redirectsOnlyTheVerifiedSortMethod()
    {
        byte[] patched = RerouteStaticInMethodPatch.apply(stub(),
            Mappings.RG_LOAD_RENDERERS, Mappings.RG_LOAD_RENDERERS_DESC,
            "java/util/Arrays", "sort", "([Ljava/lang/Object;Ljava/util/Comparator;)V",
            "vertex/hooks/VertexRenderOrder", "sort");
        ClassNode cls = new ClassNode();
        new ClassReader(patched).accept(cls, 0);
        int hooks = 0;
        int untouched = 0;

        for (MethodNode method : cls.methods)
        {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn instanceof MethodInsnNode)
                {
                    MethodInsnNode call = (MethodInsnNode)insn;

                    if (call.owner.equals("vertex/hooks/VertexRenderOrder"))
                    {
                        ++hooks;
                    }
                    else if (call.owner.equals("java/util/Arrays") && call.name.equals("sort"))
                    {
                        ++untouched;
                    }
                }
            }
        }

        assertEquals(1, hooks);
        assertEquals(1, untouched);
    }

    @Test
    public void worldRendererPatchAddsPrimitiveDistanceKeyBridges()
    {
        ClassNode cls = new ClassNode();
        new ClassReader(WorldRendererPatch.apply(worldRendererStub())).accept(cls, 0);
        int keyFields = 0;
        int bridgeMethods = 0;

        for (org.objectweb.asm.tree.FieldNode field : cls.fields)
        {
            if (field.name.equals(Mappings.ADDED_SORT_KEY_FIELD) && field.desc.equals("D"))
            {
                ++keyFields;
            }
        }

        for (MethodNode method : cls.methods)
        {
            if (method.name.equals("vertex$sortKey") || method.name.equals("vertex$setSortKey")
                || method.name.equals("vertex$centerX") || method.name.equals("vertex$centerY")
                || method.name.equals("vertex$centerZ"))
            {
                ++bridgeMethods;
            }
        }

        assertEquals(1, keyFields);
        assertEquals(5, bridgeMethods);
    }

    private static byte[] stub()
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, Mappings.RENDER_GLOBAL, null, "java/lang/Object", null);
        sortMethod(cw, Mappings.RG_LOAD_RENDERERS, Mappings.RG_LOAD_RENDERERS_DESC);
        sortMethod(cw, "other", "()V");
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] worldRendererStub()
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, Mappings.WORLD_RENDERER, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC, Mappings.WR_NEEDS_UPDATE, "Z", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC, Mappings.WR_CENTER_X, "I", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC, Mappings.WR_CENTER_Y, "I", null, null).visitEnd();
        cw.visitField(ACC_PUBLIC, Mappings.WR_CENTER_Z, "I", null, null).visitEnd();
        emptyMethod(cw, Mappings.WR_SET_POSITION, Mappings.WR_SET_POSITION_DESC);
        emptyMethod(cw, Mappings.WR_UPDATE_RENDERER, Mappings.WR_UPDATE_RENDERER_DESC);
        emptyMethod(cw, Mappings.WR_SETUP_GL_TRANSLATION, "()V");
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void sortMethod(ClassWriter cw, String name, String desc)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, name, desc, null, null);
        mv.visitCode();
        mv.visitInsn(ICONST_0);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        mv.visitInsn(ACONST_NULL);
        mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "sort",
            "([Ljava/lang/Object;Ljava/util/Comparator;)V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emptyMethod(ClassWriter cw, String name, String desc)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, name, desc, null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
