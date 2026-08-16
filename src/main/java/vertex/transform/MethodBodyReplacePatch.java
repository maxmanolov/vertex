package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Replaces a static (F)F method's body with a delegation to a hook of the same shape.
 * Unlike the return adjusters this removes the vanilla computation entirely - the use
 * case is MathHelper's table-backed sin/cos, where adjusting the result would keep the
 * cost being optimized away.
 */
final class MethodBodyReplacePatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc, String hookOwner, String hookName)
    {
        if (!"(F)F".equals(desc))
        {
            throw new IllegalStateException("Body replacement only supports static (F)F: " + desc);
        }

        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean replaced = false;

        for (MethodNode candidate : cls.methods)
        {
            if (candidate.name.equals(method) && candidate.desc.equals(desc)
                && (candidate.access & ACC_STATIC) != 0)
            {
                InsnList body = new InsnList();
                body.add(new VarInsnNode(FLOAD, 0));
                body.add(new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, desc, false));
                body.add(new InsnNode(FRETURN));
                candidate.instructions.clear();
                candidate.instructions.add(body);
                candidate.tryCatchBlocks.clear();
                candidate.localVariables = null;
                replaced = true;
            }
        }

        if (!replaced)
        {
            throw new IllegalStateException("Body replacement found no static " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private MethodBodyReplacePatch()
    {
    }
}
