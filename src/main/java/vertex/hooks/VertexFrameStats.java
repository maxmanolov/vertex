package vertex.hooks;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Frame-pacing and memory instrumentation for the diagnostics line. Frame times land in
 * fixed 0.5 ms histogram buckets (up to 100 ms, then overflow), so percentile extraction
 * allocates nothing in steady state and the per-frame cost is one subtraction and one
 * array increment. Percentiles are what the release optimizes for: p99 and max expose
 * frame spikes that an average would bury.
 */
public final class VertexFrameStats
{
    private static final int BUCKET_NANOS = 500_000;
    private static final int BUCKETS = 200;

    private static final int[] histogram = new int[BUCKETS + 1];
    private static long frames = 0L;
    private static long maxNanos = 0L;
    private static long lastFrameNanos = 0L;

    private static long lastGcCount = -1L;
    private static long lastGcMillis = 0L;

    /** Called once per rendered frame from the stats tick. */
    public static void frame()
    {
        long now = System.nanoTime();

        if (lastFrameNanos != 0L)
        {
            long delta = now - lastFrameNanos;
            int bucket = (int)(delta / BUCKET_NANOS);
            ++histogram[bucket < BUCKETS ? bucket : BUCKETS];
            ++frames;

            if (delta > maxNanos)
            {
                maxNanos = delta;
            }
        }

        lastFrameNanos = now;
    }

    /** Appends frame/heap/GC fields to the report line and resets the window. */
    public static String drainReport()
    {
        StringBuilder out = new StringBuilder();
        out.append(" ftP50=").append(format(percentile(50)));
        out.append(" ftP99=").append(format(percentile(99)));
        out.append(" ftMax=").append(format(maxNanos));
        out.append(" frames=").append(frames);
        Runtime runtime = Runtime.getRuntime();
        out.append(" heapMB=").append((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L));
        long gcCount = 0L;
        long gcMillis = 0L;
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();

        for (GarbageCollectorMXBean collector : collectors)
        {
            gcCount += Math.max(0L, collector.getCollectionCount());
            gcMillis += Math.max(0L, collector.getCollectionTime());
        }

        if (lastGcCount >= 0L)
        {
            out.append(" gc=").append(gcCount - lastGcCount).append("/").append(gcMillis - lastGcMillis).append("ms");
        }

        lastGcCount = gcCount;
        lastGcMillis = gcMillis;

        reset();
        return out.toString();
    }

    /** Clears the current report window without formatting or reading runtime metrics. */
    static void reset()
    {
        for (int i = 0; i <= BUCKETS; ++i)
        {
            histogram[i] = 0;
        }

        frames = 0L;
        maxNanos = 0L;
    }

    /**
     * Disabled-interval drain: clears the frame window AND rebaselines the GC delta.
     * Without the rebaseline the first enabled report's gc= field spans the whole
     * disabled epoch labelled as one minute - the same defect #86 describes for frames,
     * one accumulator over. Reads the GC beans (cheap, once per disabled minute) so the
     * first enabled report shows an accurate single-interval delta.
     */
    static void resetWindow()
    {
        reset();
        long gcCount = 0L;
        long gcMillis = 0L;

        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans())
        {
            gcCount += Math.max(0L, collector.getCollectionCount());
            gcMillis += Math.max(0L, collector.getCollectionTime());
        }

        lastGcCount = gcCount;
        lastGcMillis = gcMillis;
    }

    private static long percentile(int pct)
    {
        if (frames == 0L)
        {
            return 0L;
        }

        long target = (frames * pct + 99L) / 100L;
        long seen = 0L;

        for (int i = 0; i <= BUCKETS; ++i)
        {
            seen += histogram[i];

            if (seen >= target)
            {
                // Bucket midpoint; the overflow bucket reports its floor.
                return i < BUCKETS ? (long)i * BUCKET_NANOS + BUCKET_NANOS / 2 : (long)BUCKETS * BUCKET_NANOS;
            }
        }

        return maxNanos;
    }

    private static String format(long nanos)
    {
        long tenthsOfMs = nanos / 100_000L;
        return (tenthsOfMs / 10L) + "." + (tenthsOfMs % 10L) + "ms";
    }

    private VertexFrameStats()
    {
    }
}
