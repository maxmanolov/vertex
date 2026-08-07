package vertex.benchmark.game;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;

public final class BenchmarkTransformerTest
{
    @Test
    public void addsTheClientHook()
    {
        byte[] result = new BenchmarkTransformer().transform("bao", "bao",
            classWithVoidMethod("bao", "ak"));

        assertEquals(1, calls(result, "tick"));
        assertEquals(0, calls(result, "serverTick"));
    }

    @Test
    public void addsTheIntegratedServerHook()
    {
        String owner = "net/minecraft/server/MinecraftServer";
        byte[] result = new BenchmarkTransformer().transform(
            "net.minecraft.server.MinecraftServer", owner,
            classWithVoidMethod(owner, "v"));

        assertEquals(0, calls(result, "tick"));
        assertEquals(1, calls(result, "serverTick"));
    }

    private static byte[] classWithVoidMethod(String name, String methodName)
    {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null,
            "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC,
            methodName, "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static int calls(byte[] bytecode, String methodName)
    {
        ClassNode owner = new ClassNode();
        new ClassReader(bytecode).accept(owner, 0);
        int result = 0;

        for (MethodNode method : owner.methods)
        {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext())
            {
                if (instruction instanceof MethodInsnNode
                    && methodName.equals(((MethodInsnNode)instruction).name))
                {
                    ++result;
                }
            }
        }

        return result;
    }
}
