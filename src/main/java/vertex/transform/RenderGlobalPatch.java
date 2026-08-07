package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/**
 * RenderGlobal (bma) patch:
 *  - markBlockForUpdate keeps its vanilla 3x3x3 dirty-marking, then reports the exact
 *    changed block to {@link vertex.hooks.VertexHooks#blockChanged}, which decides whether
 *    the containing section (and face-adjacent boundary sections) get immediate priority
 *  - updateRenderers gets a head call into {@link vertex.hooks.VertexHooks#consumeImmediates}
 *    so immediate sections rebuild before vanilla's distance-sorted, budgeted pass
 *  - loadRenderers clamps shared modern-launcher render-distance values to 1.7.10's
 *    hard allocation limit before it sizes the renderer grid
 */
final class RenderGlobalPatch implements Opcodes
{
    private static final String HOOKS = "vertex/hooks/VertexHooks";

    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);
        boolean patchedMark = false;
        boolean patchedUpdate = false;
        boolean patchedLoad = false;

        for (MethodNode method : cls.methods)
        {
            if (!patchedMark && method.name.equals(Mappings.RG_MARK_BLOCK_FOR_UPDATE) && method.desc.equals(Mappings.RG_MARK_BLOCK_FOR_UPDATE_DESC))
            {
                // Replace the body: vanilla range mark, then the Vertex hook.
                InsnList body = new InsnList();
                body.add(new VarInsnNode(ALOAD, 0));

                for (int axis = 1; axis <= 3; ++axis)
                {
                    body.add(new VarInsnNode(ILOAD, axis));
                    body.add(new InsnNode(ICONST_1));
                    body.add(new InsnNode(ISUB));
                }

                for (int axis = 1; axis <= 3; ++axis)
                {
                    body.add(new VarInsnNode(ILOAD, axis));
                    body.add(new InsnNode(ICONST_1));
                    body.add(new InsnNode(IADD));
                }

                body.add(new MethodInsnNode(INVOKEVIRTUAL, cls.name, Mappings.RG_MARK_BLOCKS_FOR_UPDATE, Mappings.RG_MARK_BLOCKS_FOR_UPDATE_DESC, false));
                body.add(new VarInsnNode(ALOAD, 0));
                body.add(new VarInsnNode(ILOAD, 1));
                body.add(new VarInsnNode(ILOAD, 2));
                body.add(new VarInsnNode(ILOAD, 3));
                body.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "blockChanged", "(Ljava/lang/Object;III)V", false));
                body.add(new InsnNode(RETURN));
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables = null;
                method.instructions.add(body);
                patchedMark = true;
            }
            else if (!patchedUpdate && method.name.equals(Mappings.RG_UPDATE_RENDERERS) && method.desc.equals(Mappings.RG_UPDATE_RENDERERS_DESC))
            {
                InsnList head = new InsnList();
                head.add(new VarInsnNode(ALOAD, 0));
                head.add(new VarInsnNode(ALOAD, 1));
                head.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "consumeImmediates", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                method.instructions.insertBefore(method.instructions.getFirst(), head);
                patchedUpdate = true;
            }
            else if (!patchedLoad && method.name.equals(Mappings.RG_LOAD_RENDERERS) && method.desc.equals(Mappings.RG_LOAD_RENDERERS_DESC))
            {
                // 1.7.10 reserves display-list and occlusion-query ids for a maximum
                // radius of 16 in RenderGlobal's constructor. Newer Minecraft versions
                // can leave a larger value in the shared options.txt; vanilla 1.7.10
                // accepts it and then indexes past its fixed query buffer (SourceFile:271).
                InsnList head = new InsnList();
                head.add(new VarInsnNode(ALOAD, 0));
                head.add(new FieldInsnNode(GETFIELD, cls.name,
                    Mappings.RG_MC, "L" + Mappings.MINECRAFT + ";"));
                head.add(new FieldInsnNode(GETFIELD, Mappings.MINECRAFT,
                    Mappings.MC_GAME_SETTINGS, "L" + Mappings.GAME_SETTINGS + ";"));
                head.add(new InsnNode(DUP));
                head.add(new FieldInsnNode(GETFIELD, Mappings.GAME_SETTINGS,
                    Mappings.GS_RENDER_DISTANCE, "I"));
                head.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "clampLegacyRenderDistance", "(I)I", false));
                head.add(new FieldInsnNode(PUTFIELD, Mappings.GAME_SETTINGS,
                    Mappings.GS_RENDER_DISTANCE, "I"));
                method.instructions.insertBefore(method.instructions.getFirst(), head);
                patchedLoad = true;
            }
        }

        if (!patchedMark || !patchedUpdate || !patchedLoad)
        {
            throw new IllegalStateException("RenderGlobal patch incomplete: markBlockForUpdate=" + patchedMark
                + " updateRenderers=" + patchedUpdate + " loadRenderers=" + patchedLoad);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private RenderGlobalPatch()
    {
    }
}
