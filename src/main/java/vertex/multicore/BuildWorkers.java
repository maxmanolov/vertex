package vertex.multicore;

import net.minecraft.launchwrapper.LogWrapper;

/**
 * CPU-only worker pool over a BuildQueue. Workers never touch GL: they run the supplied
 * task (geometry tessellation, once the renderer split lands) and hand the build back
 * through the queue for client-thread application. Sized min(cores-2, 6) with a floor of
 * 2 - chunk building saturates well before high core counts and the client thread must
 * keep a core.
 */
public final class BuildWorkers
{
    /** The worker-side unit of work. Implementations must be GL-free. */
    public interface Task
    {
        void build(BuildQueue.Build build) throws Exception;
    }

    private final Thread[] threads;

    public BuildWorkers(final BuildQueue queue, final Task task, String namePrefix)
    {
        int count = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 2, 6));
        this.threads = new Thread[count];

        for (int i = 0; i < count; ++i)
        {
            Thread thread = new Thread(new Runnable()
            {
                public void run()
                {
                    while (true)
                    {
                        BuildQueue.Build build;

                        try
                        {
                            build = queue.take();
                        }
                        catch (InterruptedException interrupted)
                        {
                            return;
                        }

                        if (build == null)
                        {
                            return;
                        }

                        try
                        {
                            task.build(build);
                        }
                        catch (Throwable failure)
                        {
                            build.failed = true;
                            failure.printStackTrace();
                        }
                        finally
                        {
                            // Every claimed build needs one client-thread disposition.
                            // An Error used to terminate this worker before complete(),
                            // which left its renderer permanently marked in flight (#97).
                            queue.complete(build);
                        }
                    }
                }
            }, namePrefix + "-" + i);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 2);
            this.threads[i] = thread;
            thread.start();
        }

        LogWrapper.info("[Vertex] Build workers started: " + count);
    }

    public int size()
    {
        return this.threads.length;
    }

    /**
     * Interrupts every worker and waits briefly for each to exit. Safe from any thread:
     * a worker calling in (self-disable fired inside a build) skips joining itself.
     */
    public void shutdown()
    {
        for (Thread thread : this.threads)
        {
            thread.interrupt();
        }

        for (Thread thread : this.threads)
        {
            if (thread == Thread.currentThread())
            {
                continue;
            }

            try
            {
                thread.join(1000L);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
