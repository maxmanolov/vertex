package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/** Adds direct empty-state and center-coordinate bridges to WorldRenderer. */
final class ActiveSectionPatch implements Opcodes
{
    private static final String API = "vertex/api/ActiveSection";

    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        verifyField(cls, Mappings.WR_SKIP_RENDER_PASS, "[Z");
        verifyField(cls, Mappings.WR_CENTER_X, "I");
        verifyField(cls, Mappings.WR_CENTER_Y, "I");
        verifyField(cls, Mappings.WR_CENTER_Z, "I");
        cls.interfaces.add(API);
        cls.methods.add(hasMesh(cls.name));
        cls.methods.add(intGetter(cls.name, "vertex$centerX", Mappings.WR_CENTER_X));
        cls.methods.add(intGetter(cls.name, "vertex$centerY", Mappings.WR_CENTER_Y));
        cls.methods.add(intGetter(cls.name, "vertex$centerZ", Mappings.WR_CENTER_Z));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode hasMesh(String owner)
    {
        MethodNode method = new MethodNode(ACC_PUBLIC, "vertex$hasMesh", "()Z", null, null);
        LabelNode present = new LabelNode(new Label());
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, owner, Mappings.WR_SKIP_RENDER_PASS, "[Z"));
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new InsnNode(BALOAD));
        method.instructions.add(new JumpInsnNode(IFEQ, present));
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, owner, Mappings.WR_SKIP_RENDER_PASS, "[Z"));
        method.instructions.add(new InsnNode(ICONST_1));
        method.instructions.add(new InsnNode(BALOAD));
        method.instructions.add(new JumpInsnNode(IFEQ, present));
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new InsnNode(IRETURN));
        method.instructions.add(present);
        method.instructions.add(new InsnNode(ICONST_1));
        method.instructions.add(new InsnNode(IRETURN));
        return method;
    }

    private static MethodNode intGetter(String owner, String methodName, String fieldName)
    {
        MethodNode method = new MethodNode(ACC_PUBLIC, methodName, "()I", null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, owner, fieldName, "I"));
        method.instructions.add(new InsnNode(IRETURN));
        return method;
    }

    private static void verifyField(ClassNode cls, String name, String desc)
    {
        for (FieldNode field : cls.fields)
        {
            if (field.name.equals(name) && field.desc.equals(desc))
            {
                return;
            }
        }

        throw new IllegalStateException("Active-section field is missing: " + cls.name + "." + name + " " + desc);
    }

    private ActiveSectionPatch()
    {
    }
}
