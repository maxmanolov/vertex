package vertex.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import vertex.Mappings;

/**
 * WorldRenderer (blo) patch:
 *  - adds a public boolean vertex$immediate field
 *  - implements {@link vertex.api.ImmediateMarker} with bridge methods over the flag,
 *    needsUpdate, and updateRenderer(EntityLivingBase)
 *  - clears the flag at the head of setPosition so recycled renderers never inherit it
 *
 * MC 1.7.10 classes are version 50, so COMPUTE_MAXS suffices and no stack-map frames are
 * generated - frame computation would resolve classes through the wrong loader.
 */
final class WorldRendererPatch implements Opcodes
{
    private static final String MARKER_IFACE = "vertex/api/ImmediateMarker";
    private static final String MESH_HOST_IFACE = "vertex/api/MeshHost";

    static byte[] apply(byte[] basicClass)
    {
        ClassNode cls = new ClassNode();
        new ClassReader(basicClass).accept(cls, 0);

        cls.interfaces.add(MARKER_IFACE);
        cls.fields.add(new FieldNode(ACC_PUBLIC | ACC_VOLATILE, Mappings.ADDED_IMMEDIATE_FIELD, "Z", null, null));

        // MeshHost: one opaque slot for the managed render backend's per-section GPU
        // state, reachable without reflection from both sides of the loader split.
        cls.interfaces.add(MESH_HOST_IFACE);
        cls.fields.add(new FieldNode(ACC_PUBLIC, Mappings.ADDED_MESH_FIELD, "Ljava/lang/Object;", null, null));

        MethodNode meshGet = new MethodNode(ACC_PUBLIC, "vertex$mesh", "()Ljava/lang/Object;", null, null);
        meshGet.instructions.add(new VarInsnNode(ALOAD, 0));
        meshGet.instructions.add(new FieldInsnNode(GETFIELD, cls.name, Mappings.ADDED_MESH_FIELD, "Ljava/lang/Object;"));
        meshGet.instructions.add(new InsnNode(ARETURN));
        cls.methods.add(meshGet);

        MethodNode meshSet = new MethodNode(ACC_PUBLIC, "vertex$setMesh", "(Ljava/lang/Object;)V", null, null);
        meshSet.instructions.add(new VarInsnNode(ALOAD, 0));
        meshSet.instructions.add(new VarInsnNode(ALOAD, 1));
        meshSet.instructions.add(new FieldInsnNode(PUTFIELD, cls.name, Mappings.ADDED_MESH_FIELD, "Ljava/lang/Object;"));
        meshSet.instructions.add(new InsnNode(RETURN));
        cls.methods.add(meshSet);

        // void vertex$markImmediate() { this.needsUpdate = true; this.vertex$immediate = true; }
        MethodNode mark = new MethodNode(ACC_PUBLIC, "vertex$markImmediate", "()V", null, null);
        mark.instructions.add(new VarInsnNode(ALOAD, 0));
        mark.instructions.add(new InsnNode(ICONST_1));
        mark.instructions.add(new FieldInsnNode(PUTFIELD, cls.name, Mappings.WR_NEEDS_UPDATE, "Z"));
        mark.instructions.add(new VarInsnNode(ALOAD, 0));
        mark.instructions.add(new InsnNode(ICONST_1));
        mark.instructions.add(new FieldInsnNode(PUTFIELD, cls.name, Mappings.ADDED_IMMEDIATE_FIELD, "Z"));
        mark.instructions.add(new InsnNode(RETURN));
        cls.methods.add(mark);

        // boolean vertex$needsImmediate() { return this.vertex$immediate; }
        MethodNode needs = new MethodNode(ACC_PUBLIC, "vertex$needsImmediate", "()Z", null, null);
        needs.instructions.add(new VarInsnNode(ALOAD, 0));
        needs.instructions.add(new FieldInsnNode(GETFIELD, cls.name, Mappings.ADDED_IMMEDIATE_FIELD, "Z"));
        needs.instructions.add(new InsnNode(IRETURN));
        cls.methods.add(needs);

        // void vertex$clearImmediate() { this.vertex$immediate = false; }
        MethodNode clear = new MethodNode(ACC_PUBLIC, "vertex$clearImmediate", "()V", null, null);
        clear.instructions.add(new VarInsnNode(ALOAD, 0));
        clear.instructions.add(new InsnNode(ICONST_0));
        clear.instructions.add(new FieldInsnNode(PUTFIELD, cls.name, Mappings.ADDED_IMMEDIATE_FIELD, "Z"));
        clear.instructions.add(new InsnNode(RETURN));
        cls.methods.add(clear);

        // boolean vertex$isDirty() { return this.needsUpdate; }
        MethodNode dirty = new MethodNode(ACC_PUBLIC, "vertex$isDirty", "()Z", null, null);
        dirty.instructions.add(new VarInsnNode(ALOAD, 0));
        dirty.instructions.add(new FieldInsnNode(GETFIELD, cls.name, Mappings.WR_NEEDS_UPDATE, "Z"));
        dirty.instructions.add(new InsnNode(IRETURN));
        cls.methods.add(dirty);

        // void vertex$rebuild(Object e) { this.updateRenderer((EntityLivingBase) e); }
        MethodNode rebuild = new MethodNode(ACC_PUBLIC, "vertex$rebuild", "(Ljava/lang/Object;)V", null, null);
        rebuild.instructions.add(new VarInsnNode(ALOAD, 0));
        rebuild.instructions.add(new VarInsnNode(ALOAD, 1));
        rebuild.instructions.add(new TypeInsnNode(CHECKCAST, Mappings.ENTITY_LIVING_BASE));
        rebuild.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, cls.name, Mappings.WR_UPDATE_RENDERER, Mappings.WR_UPDATE_RENDERER_DESC, false));
        rebuild.instructions.add(new InsnNode(RETURN));
        cls.methods.add(rebuild);

        // void vertex$setupTranslation() { this.setupGLTranslation(); }
        MethodNode setup = new MethodNode(ACC_PUBLIC, "vertex$setupTranslation", "()V", null, null);
        setup.instructions.add(new VarInsnNode(ALOAD, 0));
        setup.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, cls.name, Mappings.WR_SETUP_GL_TRANSLATION, "()V", false));
        setup.instructions.add(new InsnNode(RETURN));
        cls.methods.add(setup);

        // setPosition head: this.vertex$immediate = false; recycled grid slots must not
        // carry a pending immediate from their previous section. The reposition also
        // notifies the multicore stamp tracker so in-flight worker builds for this
        // renderer invalidate (an A->B->A move would otherwise pass the drain XYZ check).
        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(Mappings.WR_SET_POSITION) && method.desc.equals(Mappings.WR_SET_POSITION_DESC))
            {
                InsnList head = new InsnList();
                head.add(new VarInsnNode(ALOAD, 0));
                head.add(new InsnNode(ICONST_0));
                head.add(new FieldInsnNode(PUTFIELD, cls.name, Mappings.ADDED_IMMEDIATE_FIELD, "Z"));
                head.add(new VarInsnNode(ALOAD, 0));
                head.add(new MethodInsnNode(INVOKESTATIC, "vertex/hooks/VertexMulticore",
                    "onRendererRepositioned", "(Ljava/lang/Object;)V", false));
                method.instructions.insertBefore(method.instructions.getFirst(), head);
                break;
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }

    private WorldRendererPatch()
    {
    }
}
