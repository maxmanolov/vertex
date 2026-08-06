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

import static org.junit.Assert.assertEquals;

public class GLCallCountPatchTest implements Opcodes
{
    @Test
    public void reroutesStateCallsAndLeavesOthersAlone()
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, "gltarget", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(Integer.valueOf(3553));
        mv.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glEnable", "(I)V", false);
        mv.visitLdcInsn(Integer.valueOf(3553));
        mv.visitLdcInsn(Integer.valueOf(7));
        mv.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glBindTexture", "(II)V", false);
        mv.visitLdcInsn(Integer.valueOf(2929));
        mv.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDepthFunc", "(I)V", false);
        mv.visitLdcInsn(Integer.valueOf(33984));
        mv.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL13", "glActiveTexture", "(I)V", false);
        mv.visitLdcInsn(Integer.valueOf(33984));
        mv.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/ARBMultitexture", "glActiveTextureARB", "(I)V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] patched = GLCallCountPatch.process(cw.toByteArray());
        ClassNode cls = new ClassNode();
        new ClassReader(patched).accept(cls, 0);
        int hooked = 0;
        int untouched = 0;

        for (MethodNode method : cls.methods)
        {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn instanceof MethodInsnNode)
                {
                    MethodInsnNode call = (MethodInsnNode)insn;

                    if (call.owner.equals("vertex/hooks/VertexGLStats"))
                    {
                        ++hooked;
                    }
                    else if (call.owner.equals("org/lwjgl/opengl/GL11"))
                    {
                        ++untouched;
                    }
                }
            }
        }

        assertEquals(4, hooked);
        assertEquals(1, untouched);
    }
}
