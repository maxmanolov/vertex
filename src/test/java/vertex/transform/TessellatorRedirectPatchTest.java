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
     * WorldRenderer-style cached static: even though the cache field holds whatever the
     * class captured at init, redirected reads must resolve through VertexTessellator so
     * a worker-bound thread sees its own instance.
     */
    @Test
    public void cachedFieldReadsResolveThroughTheRedirect() throws Exception
    {
        byte[] tess = TessellatorRedirectPatch.process(Mappings.TESSELLATOR, TransformerHarness.fakeTessellator(Mappings.TESSELLATOR));
        byte[] cacher = TessellatorRedirectPatch.process(Mappings.WORLD_RENDERER,
            TransformerHarness.cachedFieldConsumer(Mappings.WORLD_RENDERER, Mappings.TESSELLATOR, Mappings.WR_CACHED_TESSELLATOR));
        ByteLoader loader = new ByteLoader().add(Mappings.TESSELLATOR, tess).add(Mappings.WORLD_RENDERER, cacher);
        final Class<?> cacherClass = loader.loadClass(Mappings.WORLD_RENDERER);
        Object viaCache = cacherClass.getMethod("readCache").invoke(null);
        assertNotNull("cache read must have initialized the tessellator class", viaCache);
        assertSame(VertexTessellator.get(), viaCache);
        // Workers bind real Tessellator instances; the injected CHECKCAST enforces that.
        final Object workerOwn = loader.loadClass(Mappings.TESSELLATOR).newInstance();
        final Object[] seen = new Object[1];
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                VertexTessellator.bindThreadInstance(workerOwn);

                try
                {
                    seen[0] = cacherClass.getMethod("readCache").invoke(null);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        });
        worker.start();
        worker.join();
        assertSame("worker must see its bound instance through the cached-field path", workerOwn, seen[0]);
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
