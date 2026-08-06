package vertex.hooks;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class VertexTessellatorTest
{
    public static final class RecoverableTessellator
    {
        private boolean x;
        private boolean throwOnReset;
        private int resets;

        private void d()
        {
            ++this.resets;

            if (this.throwOnReset)
            {
                throw new IllegalStateException("reset failed");
            }
        }
    }

    private final Object main = new Object();
    private final Object workerOwn = new Object();

    @Before
    public void reset() throws Exception
    {
        Field field = VertexTessellator.class.getDeclaredField("mainInstance");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    public void unboundThreadsSeeTheMainInstance() throws Exception
    {
        VertexTessellator.bind(this.main);
        assertSame(this.main, VertexTessellator.get());
        final AtomicReference<Object> seen = new AtomicReference<Object>();
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                seen.set(VertexTessellator.get());
            }
        });
        worker.start();
        worker.join();
        assertSame(this.main, seen.get());
    }

    @Test
    public void aBoundWorkerSeesItsOwnInstanceWhileOthersKeepTheMain() throws Exception
    {
        VertexTessellator.bind(this.main);
        final AtomicReference<Object> seenAfterBind = new AtomicReference<Object>();
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                VertexTessellator.bindThreadInstance(VertexTessellatorTest.this.workerOwn);
                seenAfterBind.set(VertexTessellator.get());
            }
        });
        worker.start();
        worker.join();
        assertSame(this.workerOwn, seenAfterBind.get());
        assertSame("client thread must be unaffected by worker bindings", this.main, VertexTessellator.get());
    }

    @Test
    public void worldChangeRecoversAnAbandonedMainTessellation()
    {
        RecoverableTessellator tessellator = new RecoverableTessellator();
        tessellator.x = true;
        VertexTessellator.bind(tessellator);

        VertexTessellator.sanitizeOnWorldChange(null);

        assertFalse(tessellator.x);
        assertEquals(1, tessellator.resets);
    }

    @Test
    public void worldChangeLeavesAnIdleMainTessellatorAlone()
    {
        RecoverableTessellator tessellator = new RecoverableTessellator();
        VertexTessellator.bind(tessellator);

        VertexTessellator.sanitizeOnWorldChange(null);

        assertFalse(tessellator.x);
        assertEquals(0, tessellator.resets);
    }

    @Test
    public void worldChangeClearsDrawingStateWhenResetFails()
    {
        RecoverableTessellator tessellator = new RecoverableTessellator();
        tessellator.x = true;
        tessellator.throwOnReset = true;
        VertexTessellator.bind(tessellator);

        VertexTessellator.sanitizeOnWorldChange(null);

        assertFalse(tessellator.x);
        assertEquals(1, tessellator.resets);
    }
}
