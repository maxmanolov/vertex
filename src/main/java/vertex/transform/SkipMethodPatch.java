package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Config-gated render-pass suppression: inserts at the head of a void method
 *
 *   if (VertexConfig.skip("key")) return;
 *
 * so the pass costs one static call per frame when enabled and nothing else changes.
 * Only valid for void methods - enforced at patch time.
 */
final class SkipMethodPatch implements Opcodes
{
    /** One entry: target method name, descriptor, and the vertex.properties key gating it. */
    static final class Target
    {
        final String method;
        final String desc;
        final String configKey;

        Target(String method, String desc, String configKey)
        {
            this.method = method;
            this.desc = desc;
            this.configKey = configKey;
        }
    }

    static byte[] apply(byte[] basicClass, Target[] targets)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int applied = 0;

        for (MethodNode method : cls.methods)
        {
            for (Target target : targets)
            {
                if (method.name.equals(target.method) && method.desc.equals(target.desc))
                {
                    if (!target.desc.endsWith(")V"))
                    {
                        throw new IllegalStateException("Skip target must return void: " + target.method + target.desc);
                    }

                    InsnList head = new InsnList();
                    LabelNode proceed = new LabelNode();
                    head.add(new LdcInsnNode(target.configKey));
                    head.add(new MethodInsnNode(INVOKESTATIC, "vertex/hooks/VertexConfig", "skip", "(Ljava/lang/String;)Z", false));
                    head.add(new JumpInsnNode(IFEQ, proceed));
                    head.add(new InsnNode(RETURN));
                    head.add(proceed);
                    method.instructions.insertBefore(method.instructions.getFirst(), head);
                    ++applied;
                }
            }
        }

        if (applied != targets.length)
        {
            throw new IllegalStateException("Skip patch matched " + applied + " of " + targets.length + " targets");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private SkipMethodPatch()
    {
    }
}
