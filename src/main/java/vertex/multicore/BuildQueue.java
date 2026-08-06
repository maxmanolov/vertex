package vertex.multicore;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The multi-core chunk pipeline's spine (docs/ROADMAP.md #1), ported from the design
 * proven out-of-tree: pending builds are consumed by CPU workers, finished builds are
 * drained on the client thread. Validity is a per-renderer stamp plus a global generation;
 * a stale result is discarded at drain time, never applied. Everything here is plain Java
 * with no Minecraft or GL types, so it is fully unit-tested (BuildQueueTest).
 *
 * Threading contract: submit/prioritize/drain/clear run on the client thread; take runs
 * on workers; complete runs on whichever thread built. The pending deque is guarded by
 * its own lock; the finished queue is lock-free.
 */
public final class BuildQueue
{
    /** One chunk-section build in flight. Subclasses carry the actual geometry payload. */
    public static class Build
    {
        public final Object renderer;
        public final int stamp;
        public final int generation;
        public volatile boolean failed;

        public Build(Object renderer, int stamp, int generation)
        {
            this.renderer = renderer;
            this.stamp = stamp;
            this.generation = generation;
        }
    }

    /** Applies a finished build; returns true when it was applied (not stale). */
    public interface Sink
    {
        boolean apply(Build build);

        void discard(Build build);
    }

    private final Object lock = new Object();
    private final ArrayDeque<Build> pending = new ArrayDeque<Build>();
    private final ConcurrentLinkedQueue<Build> finished = new ConcurrentLinkedQueue<Build>();
    private volatile boolean closed = false;
    private volatile int generation = 0;

    /** Invalidate everything queued; prior-generation results discard at drain. */
    public void invalidateGeneration()
    {
        synchronized (this.lock)
        {
            ++this.generation;
            this.pending.clear();
            this.lock.notifyAll();
        }
    }

    public int generation()
    {
        return this.generation;
    }

    public void submit(Build build)
    {
        synchronized (this.lock)
        {
            this.pending.addLast(build);
            this.lock.notifyAll();
        }
    }

    /** Moves the first pending build for this renderer to the head of the queue. */
    public void prioritize(Object renderer)
    {
        synchronized (this.lock)
        {
            Iterator<Build> it = this.pending.iterator();

            while (it.hasNext())
            {
                Build build = it.next();

                if (build.renderer == renderer)
                {
                    it.remove();
                    this.pending.addFirst(build);
                    return;
                }
            }
        }
    }

    /** Worker side: blocks until a build is available or the queue closes (returns null). */
    public Build take() throws InterruptedException
    {
        synchronized (this.lock)
        {
            while (true)
            {
                // Closed beats pending: after close() no worker starts new work, even if
                // builds are still queued - shutdown discards them on the client thread.
                if (this.closed)
                {
                    return null;
                }

                if (!this.pending.isEmpty())
                {
                    return this.pending.pollFirst();
                }

                this.lock.wait(2000L);
            }
        }
    }

    public void complete(Build build)
    {
        this.finished.add(build);
    }

    public int pendingCount()
    {
        synchronized (this.lock)
        {
            return this.pending.size();
        }
    }

    /**
     * Client thread: applies up to maxApplied non-stale finished builds through the sink;
     * stale or failed ones are discarded without consuming budget. Returns applied count.
     */
    public int drain(Sink sink, int maxApplied)
    {
        int applied = 0;

        while (applied < maxApplied)
        {
            Build build = this.finished.peek();

            if (build == null)
            {
                break;
            }

            this.finished.poll();

            if (!build.failed && build.generation == this.generation && sink.apply(build))
            {
                ++applied;
            }
            else
            {
                sink.discard(build);
            }
        }

        return applied;
    }

    /** World reload: invalidates everything in flight and discards pending builds. */
    public void clearAll(Sink sink)
    {
        ++this.generation;

        synchronized (this.lock)
        {
            Build build;

            while ((build = this.pending.pollFirst()) != null)
            {
                sink.discard(build);
            }
        }

        Build finishedBuild;

        while ((finishedBuild = this.finished.poll()) != null)
        {
            sink.discard(finishedBuild);
        }
    }

    public void close()
    {
        synchronized (this.lock)
        {
            this.closed = true;
            this.lock.notifyAll();
        }
    }
}
