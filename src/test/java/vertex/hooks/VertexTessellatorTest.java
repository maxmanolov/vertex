package vertex.hooks;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class VertexTessellatorTest
{
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
}
