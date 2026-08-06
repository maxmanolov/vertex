package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites INVOKESTATIC call sites of one static method inside one specific method of the
 * patched class, leaving every other caller of that method untouched. This is the scoped
 * variant of a call redirect: rerouting Gui.drawRect only inside GuiNewChat.drawChat turns
 * off the chat background without touching the hotbar, boss bar, or any other HUD element
 * that draws rectangles through the same static helper.
 *
 * The call-site owner is matched against both the true declaring class and the patched
 * class itself, because javac records whichever type the source referenced and a subclass
 * of Gui may legally name itself as the owner of an inherited static method.
 */
final class RerouteStaticInMethodPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc,
        String targetOwner, String targetName, String targetDesc,
        String hookOwner, String hookName)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int rerouted = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (!candidate.name.equals(method) || !candidate.desc.equals(desc))
            {
                continue;
            }

            for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn.getOpcode() != INVOKESTATIC)
                {
                    continue;
                }

                MethodInsnNode call = (MethodInsnNode) insn;

                if (call.name.equals(targetName) && call.desc.equals(targetDesc)
                    && (call.owner.equals(targetOwner) || call.owner.equals(cls.name)))
                {
                    call.owner = hookOwner;
                    call.name = hookName;
                    call.itf = false;
                    ++rerouted;
                }
            }
        }

        if (rerouted == 0)
        {
            // A silent no-match would ship a toggle that does nothing; fail the patch so the
            // transformer falls back to vanilla bytes and logs the miss.
            throw new IllegalStateException("Reroute matched no " + targetOwner + "." + targetName
                + targetDesc + " call sites in " + method + desc);
        }

        return finish(cls);
    }

    private static byte[] finish(ClassNode cls)
    {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private RerouteStaticInMethodPatch()
    {
    }
}
