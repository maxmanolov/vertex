package vertex.transform;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class VideoSettingsKeyPatchTest implements Opcodes
{
    @Test
    public void addsEscapeGuardAndPreservesSuperclassKeyHandling()
    {
        ClassNode cls = new ClassNode();
        new ClassReader(VideoSettingsKeyPatch.apply(videoSettingsStub())).accept(cls, 0);
        MethodNode keyTyped = null;

        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(Mappings.SCREEN_KEY_TYPED)
                && method.desc.equals(Mappings.SCREEN_KEY_TYPED_DESC))
            {
                keyTyped = method;
                break;
            }
        }

        assertNotNull(keyTyped);
        assertTrue((keyTyped.access & ACC_PROTECTED) != 0);
        int guardCalls = 0;
        int superCalls = 0;

        for (AbstractInsnNode insn = keyTyped.instructions.getFirst(); insn != null; insn = insn.getNext())
        {
            if (!(insn instanceof MethodInsnNode))
            {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode)insn;

            if (call.getOpcode() == INVOKESTATIC
                && call.owner.equals("vertex/hooks/VertexVideoMenu")
                && call.name.equals("keyTyped")
                && call.desc.equals("(Ljava/lang/Object;I)Z"))
            {
                ++guardCalls;
                assertTrue("the hook must receive keyCode local 2",
                    call.getPrevious() instanceof VarInsnNode
                        && ((VarInsnNode)call.getPrevious()).var == 2);
            }
            else if (call.getOpcode() == INVOKESPECIAL
                && call.owner.equals(Mappings.GUI_SCREEN)
                && call.name.equals(Mappings.SCREEN_KEY_TYPED)
                && call.desc.equals(Mappings.SCREEN_KEY_TYPED_DESC))
            {
                ++superCalls;
            }
        }

        assertEquals(1, guardCalls);
        assertEquals(1, superCalls);
    }

    private static byte[] videoSettingsStub()
    {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_6, ACC_PUBLIC, Mappings.GUI_VIDEO_SETTINGS, null,
            Mappings.GUI_SCREEN, null);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
