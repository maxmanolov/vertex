package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
 * Adds GuiVideoSettings.keyTyped, which vanilla 1.7.10 inherits directly from GuiScreen.
 * The override handles Esc through VertexVideoMenu and delegates every other key to the
 * exact superclass method. COMPUTE_MAXS is enough for the game's version-50 classes.
 */
final class VideoSettingsKeyPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
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

        if (keyTyped == null)
        {
            keyTyped = new MethodNode(ACC_PROTECTED, Mappings.SCREEN_KEY_TYPED,
                Mappings.SCREEN_KEY_TYPED_DESC, null, null);
            cls.methods.add(keyTyped);
            appendGuard(keyTyped.instructions);
            keyTyped.instructions.add(new VarInsnNode(ALOAD, 0));
            keyTyped.instructions.add(new VarInsnNode(ILOAD, 1));
            keyTyped.instructions.add(new VarInsnNode(ILOAD, 2));
            keyTyped.instructions.add(new MethodInsnNode(INVOKESPECIAL, cls.superName,
                Mappings.SCREEN_KEY_TYPED, Mappings.SCREEN_KEY_TYPED_DESC, false));
            keyTyped.instructions.add(new InsnNode(RETURN));
        }
        else
        {
            InsnList guard = new InsnList();
            appendGuard(guard);
            keyTyped.instructions.insertBefore(keyTyped.instructions.getFirst(), guard);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static void appendGuard(InsnList instructions)
    {
        LabelNode proceed = new LabelNode();
        instructions.add(new VarInsnNode(ALOAD, 0));
        instructions.add(new VarInsnNode(ILOAD, 2));
        instructions.add(new MethodInsnNode(INVOKESTATIC, "vertex/hooks/VertexVideoMenu",
            "keyTyped", "(Ljava/lang/Object;I)Z", false));
        instructions.add(new JumpInsnNode(IFEQ, proceed));
        instructions.add(new InsnNode(RETURN));
        instructions.add(proceed);
    }

    private VideoSettingsKeyPatch()
    {
    }
}
