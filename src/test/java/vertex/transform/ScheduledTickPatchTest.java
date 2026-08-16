package vertex.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vertex.Mappings;

public final class ScheduledTickPatchTest implements Opcodes
{
    @Test
    public void addsCoordinateBridgeToScheduledTickEntry()
    {
        ClassWriter source = new ClassWriter(0);
        source.visit(V1_6, ACC_PUBLIC, Mappings.SCHEDULED_TICK, null, "java/lang/Object", null);
        source.visitField(ACC_PUBLIC, Mappings.SCHEDULED_TICK_X, "I", null, null).visitEnd();
        source.visitField(ACC_PUBLIC, Mappings.SCHEDULED_TICK_Z, "I", null, null).visitEnd();
        source.visitEnd();

        ClassNode cls = new ClassNode();
        new ClassReader(ScheduledTickEntryPatch.apply(source.toByteArray())).accept(cls, 0);
        assertTrue(cls.interfaces.contains("vertex/api/ScheduledTickPosition"));
        assertTrue(hasMethod(cls, "vertex$x", "()I"));
        assertTrue(hasMethod(cls, "vertex$z", "()I"));
    }

    @Test
    public void replacesOnlyTheMappedChunkQueryIterator()
    {
        ClassNode cls = new ClassNode();
        new ClassReader(WorldServerTickPatch.apply(worldServerStub())).accept(cls, 0);
        int installs = 0;
        int candidates = 0;
        int rawTreeIterators = 0;

        for (MethodNode method : cls.methods)
        {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn instanceof MethodInsnNode)
                {
                    MethodInsnNode call = (MethodInsnNode)insn;

                    if (call.owner.equals("vertex/hooks/VertexScheduledTicks") && call.name.equals("install"))
                    {
                        ++installs;
                    }
                    else if (call.owner.equals("vertex/hooks/VertexScheduledTicks") && call.name.equals("candidateIterator"))
                    {
                        ++candidates;
                    }
                    else if (call.owner.equals("java/util/TreeSet") && call.name.equals("iterator"))
                    {
                        ++rawTreeIterators;
                    }
                }
            }
        }

        assertEquals(2, installs);
        assertEquals(1, candidates);
        assertEquals(0, rawTreeIterators);
    }

    private static boolean hasMethod(ClassNode cls, String name, String desc)
    {
        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(name) && method.desc.equals(desc))
            {
                return true;
            }
        }

        return false;
    }

    private static byte[] worldServerStub()
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, Mappings.WORLD_SERVER, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE, Mappings.WS_PENDING_TICK_SET, "Ljava/util/Set;", null, null).visitEnd();
        cw.visitField(ACC_PRIVATE, Mappings.WS_PENDING_TICK_TREE, "Ljava/util/TreeSet;", null, null).visitEnd();

        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor init = cw.visitMethod(ACC_PROTECTED, Mappings.WS_INITIALIZE, Mappings.WS_INITIALIZE_DESC, null, null);
        init.visitCode();
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor query = cw.visitMethod(ACC_PUBLIC, Mappings.WS_GET_PENDING_TICKS,
            Mappings.WS_GET_PENDING_TICKS_DESC, null, null);
        query.visitCode();
        query.visitVarInsn(ALOAD, 0);
        query.visitFieldInsn(GETFIELD, Mappings.WORLD_SERVER, Mappings.WS_PENDING_TICK_TREE, "Ljava/util/TreeSet;");
        query.visitMethodInsn(INVOKEVIRTUAL, "java/util/TreeSet", "iterator", "()Ljava/util/Iterator;", false);
        query.visitInsn(POP);
        query.visitInsn(ACONST_NULL);
        query.visitInsn(ARETURN);
        query.visitMaxs(0, 0);
        query.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
