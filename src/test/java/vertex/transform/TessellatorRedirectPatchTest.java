package vertex.transform;

import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;
import vertex.Mappings;
import vertex.TransformerHarness;
import vertex.TransformerHarness.ByteLoader;
import vertex.hooks.VertexTessellator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class TessellatorRedirectPatchTest
{
    @Before
    public void resetBinding() throws Exception
    {
        Field field = VertexTessellator.class.getDeclaredField("mainInstance");
        field.setAccessible(true);
        field.set(null, null);
    }

    /**
     * Regression test for the class-initialization side effect: the consumer is loaded and
     * executed WITHOUT the fake Tessellator being initialized first. The preserved GETSTATIC
     * must trigger its <clinit> (which binds), so the redirected read never observes null.
     */
    @Test
    public void redirectedReadInitializesTheTessellatorClassFirst() throws Exception
    {
        byte[] tess = TessellatorRedirectPatch.process(Mappings.TESSELLATOR, TransformerHarness.fakeTessellator(Mappings.TESSELLATOR));
        byte[] consumer = TessellatorRedirectPatch.process("consumer0", TransformerHarness.tessellatorConsumer("consumer0", Mappings.TESSELLATOR));
        ByteLoader loader = new ByteLoader().add(Mappings.TESSELLATOR, tess).add("consumer0", consumer);
        Class<?> consumerClass = loader.loadClass("consumer0");
        Object read = consumerClass.getMethod("read").invoke(null);
        assertNotNull("redirected read returned null - class-init side effect lost", read);
        assertSame(read, VertexTessellator.get());
        assertSame("consumer must observe the bound instance",
            loader.loadClass(Mappings.TESSELLATOR).getField("a").get(null), read);
    }
}
