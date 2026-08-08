package vertex.benchmark.game;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adds one neutral scenario tick to the Minecraft 1.7.10 game loop. */
public final class BenchmarkTransformer implements IClassTransformer
{
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytecode)
    {
        if (bytecode == null)
        {
            return bytecode;
        }

        if ("bao".equals(name))
        {
            return transformClient(bytecode);
        }

        if ("net.minecraft.server.MinecraftServer".equals(name)
            || "net/minecraft/server/MinecraftServer".equals(name))
        {
            return transformServer(bytecode);
        }

        return bytecode;
    }

    private static byte[] transformClient(byte[] bytecode)
    {

        ClassNode owner = new ClassNode();
        new ClassReader(bytecode).accept(owner, 0);

        for (MethodNode method : owner.methods)
        {
            if ("ak".equals(method.name) && "()V".equals(method.desc))
            {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "vertex/benchmark/game/BenchmarkWorldDriver", "tick",
                    "(Ljava/lang/Object;)V", false));
                method.instructions.insert(hook);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                owner.accept(writer);
                return writer.toByteArray();
            }
        }

        throw new IllegalStateException("Minecraft.runGameLoop was not found.");
    }

    private static byte[] transformServer(byte[] bytecode)
    {
        ClassNode owner = new ClassNode();
        new ClassReader(bytecode).accept(owner, 0);

        for (MethodNode method : owner.methods)
        {
            if (!"v".equals(method.name) || !"()V".equals(method.desc))
            {
                continue;
            }

            int hooks = 0;

            for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null; instruction = instruction.getNext())
            {
                if (instruction.getOpcode() == Opcodes.RETURN)
                {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "vertex/benchmark/game/BenchmarkWorldDriver", "serverTick",
                        "(Ljava/lang/Object;)V", false));
                    method.instructions.insertBefore(instruction, hook);
                    ++hooks;
                }
            }

            if (hooks == 0)
            {
                throw new IllegalStateException("MinecraftServer.updateTimeLightAndEntities has no return.");
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            owner.accept(writer);
            return writer.toByteArray();
        }

        throw new IllegalStateException("MinecraftServer.updateTimeLightAndEntities was not found.");
    }
}
