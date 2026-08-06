package vertex.multicore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import vertex.multicore.BuildQueue.Build;
import vertex.multicore.BuildQueue.Sink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BuildQueueTest
{
    private static final class RecordingSink implements Sink
    {
        final List<Build> applied = new ArrayList<Build>();
        final List<Build> discarded = new ArrayList<Build>();

        public boolean apply(Build build)
        {
            this.applied.add(build);
            return true;
        }

        public void discard(Build build)
        {
            this.discarded.add(build);
        }
    }

    @Test
    public void appliesCompletedBuildsInOrderUpToBudget()
    {
        BuildQueue queue = new BuildQueue();
        Build a = new Build("a", 1, queue.generation());
        Build b = new Build("b", 1, queue.generation());
        Build c = new Build("c", 1, queue.generation());
        queue.complete(a);
        queue.complete(b);
        queue.complete(c);
        RecordingSink sink = new RecordingSink();
        assertEquals(2, queue.drain(sink, 2));
        assertEquals(2, sink.applied.size());
        assertSame(a, sink.applied.get(0));
        assertEquals(1, queue.drain(sink, 2));
        assertSame(c, sink.applied.get(2));
    }

    @Test
    public void staleGenerationAndFailedBuildsAreDiscardedWithoutBudget()
    {
        BuildQueue queue = new BuildQueue();
        Build stale = new Build("s", 1, queue.generation());
        RecordingSink sink = new RecordingSink();
        queue.clearAll(sink);
        queue.complete(stale);
        Build failed = new Build("f", 1, queue.generation());
        failed.failed = true;
        queue.complete(failed);
        Build good = new Build("g", 1, queue.generation());
        queue.complete(good);
        assertEquals(1, queue.drain(sink, 1));
        assertEquals(2, sink.discarded.size());
        assertSame(good, sink.applied.get(0));
    }

    @Test
    public void prioritizeMovesARendererToTheHead()
    {
        BuildQueue queue = new BuildQueue();
        queue.submit(new Build("a", 1, 0));
        queue.submit(new Build("b", 1, 0));
        queue.submit(new Build("c", 1, 0));
        queue.prioritize("c");

        try
        {
            assertSame("c", queue.take().renderer);
            assertSame("a", queue.take().renderer);
        }
        catch (InterruptedException e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void clearAllDiscardsPendingAndInvalidatesGeneration()
    {
        BuildQueue queue = new BuildQueue();
        queue.submit(new Build("a", 1, queue.generation()));
        RecordingSink sink = new RecordingSink();
        int before = queue.generation();
        queue.clearAll(sink);
        assertEquals(before + 1, queue.generation());
        assertEquals(1, sink.discarded.size());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    public void workersBuildConcurrentlyAndFailuresAreMarked() throws Exception
    {
        final BuildQueue queue = new BuildQueue();
        final CountDownLatch built = new CountDownLatch(20);
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        new BuildWorkers(queue, new BuildWorkers.Task()
        {
            public void build(BuildQueue.Build build) throws Exception
            {
                int now = concurrent.incrementAndGet();
                peak.getAndUpdate(new java.util.function.IntUnaryOperator()
                {
                    public int applyAsInt(int prev)
                    {
                        return Math.max(prev, now);
                    }
                });
                Thread.sleep(30L);
                concurrent.decrementAndGet();
                built.countDown();

                if ("boom".equals(build.renderer))
                {
                    throw new IllegalStateException("boom");
                }
            }
        }, "TestWorker");

        for (int i = 0; i < 19; ++i)
        {
            queue.submit(new Build("r" + i, 1, 0));
        }

        queue.submit(new Build("boom", 1, 0));
        assertTrue("workers did not finish in time", built.await(10, TimeUnit.SECONDS));
        // countDown precedes complete(); spin until all 20 results have crossed the queue.
        RecordingSink sink = new RecordingSink();
        int applied = 0;
        long deadline = System.currentTimeMillis() + 5000L;

        while (applied + sink.discarded.size() < 20 && System.currentTimeMillis() < deadline)
        {
            applied += queue.drain(sink, 100);
            Thread.sleep(10L);
        }

        assertEquals(19, applied);
        assertEquals(1, sink.discarded.size());
        assertTrue("no concurrency observed", peak.get() >= 2);
        queue.close();
    }

    @Test
    public void takeReturnsNullAfterCloseEvenWithPendingBuilds() throws Exception
    {
        BuildQueue queue = new BuildQueue();
        queue.submit(new Build("a", 1, 0));
        queue.close();
        assertEquals(null, queue.take());
        // The undrained build is still there for the shutdown discard pass.
        assertEquals(1, queue.pendingCount());
    }

    @Test
    public void closeWakesABlockedTaker() throws Exception
    {
        final BuildQueue queue = new BuildQueue();
        final CountDownLatch exited = new CountDownLatch(1);
        Thread taker = new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    if (queue.take() == null)
                    {
                        exited.countDown();
                    }
                }
                catch (InterruptedException interrupted)
                {
                    // failure: latch never counts down
                }
            }
        }, "TestTaker");
        taker.start();
        Thread.sleep(100L);
        queue.close();
        assertTrue("blocked take() did not observe close", exited.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void shutdownStopsWorkersEvenWithABacklog() throws Exception
    {
        final BuildQueue queue = new BuildQueue();
        final AtomicInteger started = new AtomicInteger();
        BuildWorkers workers = new BuildWorkers(queue, new BuildWorkers.Task()
        {
            public void build(BuildQueue.Build build) throws Exception
            {
                started.incrementAndGet();
                Thread.sleep(20L);
            }
        }, "ShutdownWorker");

        for (int i = 0; i < 200; ++i)
        {
            queue.submit(new Build("r" + i, 1, 0));
        }

        Thread.sleep(50L);
        queue.close();
        workers.shutdown();
        int afterShutdown = started.get();
        Thread.sleep(100L);
        // No worker picked up new builds after shutdown returned.
        assertEquals(afterShutdown, started.get());
        assertTrue("backlog should remain undrained", queue.pendingCount() > 0);
    }
}
