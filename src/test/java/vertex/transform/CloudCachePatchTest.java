package vertex.transform;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vertex.Mappings;
import vertex.TransformerHarness;

import static org.junit.Assert.assertEquals;

public class CloudCachePatchTest
{
    @Test
    public void addsOneReplayReturnAndFinishesEveryOriginalReturn()
    {
        byte[] input = TransformerHarness.voidMethodClass("bma", Mappings.RG_RENDER_CLOUDS,
            Mappings.RG_RENDER_CLOUDS_DESC);
        byte[] output = CloudCachePatch.apply(input);
        ClassNode cls = new ClassNode();
        new ClassReader(output).accept(cls, 0);
        int replay = 0;
        int finish = 0;
        int returns = 0;

        for (MethodNode method : cls.methods)
        {
            if (!method.name.equals(Mappings.RG_RENDER_CLOUDS)
                || !method.desc.equals(Mappings.RG_RENDER_CLOUDS_DESC))
            {
                continue;
            }

            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            {
                if (insn.getOpcode() == org.objectweb.asm.Opcodes.RETURN)
                {
                    ++returns;
                }

                if (insn instanceof MethodInsnNode)
                {
                    MethodInsnNode call = (MethodInsnNode)insn;

                    if (call.owner.equals("vertex/hooks/VertexCloudCache") && call.name.equals("replay"))
                    {
                        ++replay;
                    }
                    else if (call.owner.equals("vertex/hooks/VertexCloudCache") && call.name.equals("finish"))
                    {
                        ++finish;
                    }
                }
            }
        }

        assertEquals(1, replay);
        assertEquals(1, finish);
        assertEquals(2, returns);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAClassWithoutTheExactCloudEntryPoint()
    {
        CloudCachePatch.apply(TransformerHarness.voidMethodClass("bma", "c", "(F)V"));
    }
}
