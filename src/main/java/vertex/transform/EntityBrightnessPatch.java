package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vertex.Mappings;

/**
 * Adjusts the packed entity-lightmap value at RenderManager's single verified call to
 * Entity.getBrightnessForRender. Patching after the virtual call preserves subclass
 * dispatch while allowing fullbright to replace the result before RenderManager splits
 * it into the two coordinates sent to OpenGlHelper.
 */
final class EntityBrightnessPatch implements Opcodes
{
    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        int patched = 0;

        for (MethodNode candidate : cls.methods)
        {
            if (!candidate.name.equals(Mappings.RM_RENDER_ENTITY_STATIC)
                || !candidate.desc.equals(Mappings.RM_RENDER_ENTITY_STATIC_DESC))
            {
                continue;
            }

            for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null;
                insn = insn.getNext())
            {
                if (insn.getOpcode() != INVOKEVIRTUAL)
                {
                    continue;
                }

                MethodInsnNode call = (MethodInsnNode) insn;

                if (call.owner.equals(Mappings.ENTITY)
                    && call.name.equals(Mappings.ENTITY_GET_BRIGHTNESS_FOR_RENDER)
                    && call.desc.equals(Mappings.ENTITY_GET_BRIGHTNESS_FOR_RENDER_DESC))
                {
                    candidate.instructions.insert(insn, new MethodInsnNode(INVOKESTATIC,
                        "vertex/hooks/VertexFullbright", "adjustEntityBrightness", "(I)I", false));
                    ++patched;
                }
            }
        }

        if (patched != 1)
        {
            throw new IllegalStateException("Expected one Entity.getBrightnessForRender call in "
                + Mappings.RM_RENDER_ENTITY_STATIC + Mappings.RM_RENDER_ENTITY_STATIC_DESC
                + ", found " + patched);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private EntityBrightnessPatch()
    {
    }
}
