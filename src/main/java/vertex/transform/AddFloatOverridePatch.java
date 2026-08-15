package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Adds a (F)F override to a class that inherits the method: the override calls super
 * and routes the result through a static (F)F adjuster. This scopes a behavioral tweak
 * to one subclass - the client-visual time override lives on WorldClient while every
 * server-side World keeps the vanilla method, so gameplay (mob spawning, daylight
 * sensors, sleep) never sees the adjusted value.
 *
 * Fails loudly if the class already declares the method: an existing body must be
 * patched, not shadowed.
 */
final class AddFloatOverridePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        if (!"(F)F".equals(desc))
        {
            throw new IllegalStateException("Override generator only supports (F)F: " + desc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);

        for (MethodNode existing : cls.methods)
        {
            if (existing.name.equals(method) && existing.desc.equals(desc))
            {
                throw new IllegalStateException(cls.name + " already declares " + method + desc);
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, method, desc, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(FLOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, cls.superName, method, desc, false);
        mv.visitMethodInsn(INVOKESTATIC, hookOwner, hookName, desc, false);
        mv.visitInsn(FRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        return writer.toByteArray();
    }

    private AddFloatOverridePatch()
    {
    }
}
