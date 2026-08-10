package vertex.transform;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class EntityBrightnessPatchTest implements Opcodes
{
    @Test
    public void adjustsOnlyTheVerifiedRenderManagerCallSite()
    {
        byte[] patched = EntityBrightnessPatch.apply(renderManagerClass(true));
        ClassNode cls = new ClassNode();
        new ClassReader(patched).accept(cls, 0);
        int targetCalls = 0;
        int hooksInTarget = 0;
        int hooksOutsideTarget = 0;

        for (MethodNode method : cls.methods)
        {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext())
            {
                if (!(insn instanceof MethodInsnNode))
                {
                    continue;
                }

                MethodInsnNode call = (MethodInsnNode) insn;

                if (call.owner.equals(Mappings.ENTITY)
                    && call.name.equals(Mappings.ENTITY_GET_BRIGHTNESS_FOR_RENDER)
                    && call.desc.equals(Mappings.ENTITY_GET_BRIGHTNESS_FOR_RENDER_DESC)
                    && method.name.equals(Mappings.RM_RENDER_ENTITY_STATIC))
                {
                    AbstractInsnNode next = insn.getNext();
                    assertEquals(INVOKESTATIC, next.getOpcode());
                    MethodInsnNode hook = (MethodInsnNode) next;
                    assertEquals("vertex/hooks/VertexFullbright", hook.owner);
                    assertEquals("adjustEntityBrightness", hook.name);
                    assertEquals("(I)I", hook.desc);
                    ++targetCalls;
                }
                else if (call.owner.equals("vertex/hooks/VertexFullbright")
                    && call.name.equals("adjustEntityBrightness"))
                {
                    if (method.name.equals(Mappings.RM_RENDER_ENTITY_STATIC)
                        && method.desc.equals(Mappings.RM_RENDER_ENTITY_STATIC_DESC))
                    {
                        ++hooksInTarget;
                    }
                    else
                    {
                        ++hooksOutsideTarget;
                    }
                }
            }
        }

        assertEquals(1, targetCalls);
        assertEquals(1, hooksInTarget);
        assertEquals(0, hooksOutsideTarget);
    }

    @Test
    public void rejectsAChangedBrightnessCallAnchor()
    {
        try
        {
            EntityBrightnessPatch.apply(renderManagerClass(false));
            fail("expected the missing entity-brightness call to fail");
        }
        catch (IllegalStateException expected)
        {
        }
    }

    private static byte[] renderManagerClass(boolean includeTarget)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, Mappings.RENDER_MANAGER, null, "java/lang/Object", null);
        writeMethod(cw, Mappings.RM_RENDER_ENTITY_STATIC, includeTarget);
        writeMethod(cw, "unrelated", true);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void writeMethod(ClassWriter cw, String name, boolean includeTarget)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, name,
            Mappings.RM_RENDER_ENTITY_STATIC_DESC, null, null);
        mv.visitCode();

        if (includeTarget)
        {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(FLOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, Mappings.ENTITY,
                Mappings.ENTITY_GET_BRIGHTNESS_FOR_RENDER,
                Mappings.ENTITY_GET_BRIGHTNESS_FOR_RENDER_DESC, false);
            mv.visitInsn(POP);
        }

        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
