package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites INVOKEVIRTUAL call sites of one instance method inside one specific method of
 * the patched class into INVOKESTATIC calls on a hook that receives the original receiver
 * as a leading Object parameter: owner.name(args) becomes hook(Object, args) with the
 * exact same stack consumption, so the patch composes with any surrounding control flow.
 * The scoped-reroute rationale matches {@link RerouteStaticInMethodPatch}: only the named
 * container method changes; every other caller of the target keeps vanilla behavior.
 *
 * The call-site owner is matched literally. Obfuscated call sites record the receiver's
 * static type (which may be a subclass of the declaring class), so callers pass the type
 * observed in the target bytecode, not the declaring class.
 */
final class RerouteVirtualInMethodPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass, String method, String desc,
        String targetOwner, String targetName, String targetDesc,
        String hookOwner, String hookName)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        String hookDesc = "(Ljava/lang/Object;" + targetDesc.substring(1);
        int rerouted = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (!candidate.name.equals(method) || !candidate.desc.equals(desc))
            {
                continue;
            }

            AbstractInsnNode insn = candidate.instructions.getFirst();

            while (insn != null)
            {
                // Capture the successor first: set() unlinks the replaced node.
                AbstractInsnNode next = insn.getNext();

                if (insn.getOpcode() == INVOKEVIRTUAL)
                {
                    MethodInsnNode call = (MethodInsnNode) insn;

                    if (call.name.equals(targetName) && call.desc.equals(targetDesc)
                        && call.owner.equals(targetOwner))
                    {
                        candidate.instructions.set(insn,
                            new MethodInsnNode(INVOKESTATIC, hookOwner, hookName, hookDesc, false));
                        ++rerouted;
                    }
                }

                insn = next;
            }
        }

        if (rerouted == 0)
        {
            // A silent no-match would ship a feature that does nothing; fail the patch so
            // the transformer falls back to vanilla bytes and logs the miss.
            throw new IllegalStateException("Virtual reroute matched no " + targetOwner + "."
                + targetName + targetDesc + " call sites in " + method + desc);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private RerouteVirtualInMethodPatch()
    {
    }
}
