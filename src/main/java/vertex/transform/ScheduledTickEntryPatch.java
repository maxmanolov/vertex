package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/** Adds a game-type-free coordinate bridge to NextTickListEntry. */
final class ScheduledTickEntryPatch implements Opcodes
{
    private static final String API = "vertex/api/ScheduledTickPosition";

    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        verifyIntField(cls, Mappings.SCHEDULED_TICK_X);
        verifyIntField(cls, Mappings.SCHEDULED_TICK_Z);

        if (!cls.interfaces.contains(API))
        {
            cls.interfaces.add(API);
        }

        cls.methods.add(getter(cls.name, "vertex$x", Mappings.SCHEDULED_TICK_X));
        cls.methods.add(getter(cls.name, "vertex$z", Mappings.SCHEDULED_TICK_Z));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode getter(String owner, String name, String field)
    {
        MethodNode method = new MethodNode(ACC_PUBLIC, name, "()I", null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, owner, field, "I"));
        method.instructions.add(new InsnNode(IRETURN));
        return method;
    }

    private static void verifyIntField(ClassNode cls, String name)
    {
        for (FieldNode field : cls.fields)
        {
            if (field.name.equals(name) && field.desc.equals("I"))
            {
                return;
            }
        }

        throw new IllegalStateException("Scheduled-tick coordinate field is missing: " + cls.name + "." + name);
    }

    private ScheduledTickEntryPatch()
    {
    }
}
