package vertex.hooks;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.launchwrapper.LogWrapper;

/**
 * Opt-in integrated-server tick profiler (-Dvertex.profileServer=true).
 *
 * Prerequisite instrumentation for the chunk-pacing work in #168. "Smooth World" and
 * "Lazy Chunk Loading" are claims about the *distribution* of server tick cost, not its
 * mean: pacing is supposed to convert occasional long ticks (chunk generation, mass
 * lighting, save passes) into a larger number of short ones. Vertex could not measure
 * that at all - every existing instrument times client render phases - so any pacing
 * change would have shipped on the strength of it feeling smoother, which is exactly
 * the failure mode the GL state-cache negative result (#175) was caught by.
 *
 * The client-side render profiler reports means over a window because render phases are
 * near-uniform. Server ticks are not: the interesting signal is entirely in the tail, so
 * this reports percentiles and the worst tick, and counts ticks that blew the 50ms
 * budget - those are the hitches a player actually feels.
 *
 * With the flag absent nothing is woven and this class is never loaded.
 */
public final class VertexServerProfiler
{
    public static final boolean ACTIVE = Boolean.getBoolean("vertex.profileServer");

    private static final long REPORT_NANOS = 10_000_000_000L;
    /** One vanilla tick budget: 20 ticks per second. */
    private static final long BUDGET_NANOS = 50_000_000L;
    private static final int CAPACITY = 4096;

    private static final long[] samples = new long[CAPACITY];
    private static int count = 0;
    // Do not keep an exited integrated server and its worlds alive from the main menu.
    private static WeakReference<Object> currentServer = new WeakReference<Object>(null);
    private static long windowStart = 0L;
    private static long tickStart = 0L;
    private static long overBudget = 0L;
    private static long dropped = 0L;

    /** Head of MinecraftServer.tick. */
    public static void begin(Object server)
    {
        long now = System.nanoTime();

        if (server != currentServer.get())
        {
            currentServer = new WeakReference<Object>(server);
            resetWindow(now);
        }

        tickStart = now;
    }

    /** Tail of MinecraftServer.tick. */
    public static void end(Object server)
    {
        long now = System.nanoTime();

        if (server != currentServer.get() || tickStart == 0L)
        {
            return;
        }

        long elapsed = now - tickStart;
        tickStart = 0L;

        if (count < CAPACITY)
        {
            samples[count++] = elapsed;
        }
        else
        {
            ++dropped;
        }

        if (elapsed > BUDGET_NANOS)
        {
            ++overBudget;
        }

        if (now - windowStart >= REPORT_NANOS)
        {
            report(now - windowStart);
            windowStart = now;
        }
    }

    private static void report(long windowNanos)
    {
        if (count == 0)
        {
            return;
        }

        long[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        StringBuilder line = new StringBuilder("[VertexSrv] window=");
        line.append(String.format(Locale.ROOT, "%.1fs ticks=%d",
            windowNanos / 1_000_000_000.0D, count));
        line.append(String.format(Locale.ROOT, " p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms",
            ms(percentile(sorted, 50)), ms(percentile(sorted, 95)),
            ms(percentile(sorted, 99)), ms(sorted[sorted.length - 1])));
        line.append(" overBudget=").append(overBudget);

        if (dropped > 0L)
        {
            line.append(" droppedSamples=").append(dropped);
        }

        LogWrapper.info(line.toString());
        count = 0;
        overBudget = 0L;
        dropped = 0L;
    }

    private static void resetWindow(long now)
    {
        count = 0;
        windowStart = now;
        tickStart = 0L;
        overBudget = 0L;
        dropped = 0L;
    }

    // ---- pure helpers (unit-tested) -----------------------------------------------------

    /** Nearest-rank percentile over an ascending array. */
    static long percentile(long[] ascending, int percent)
    {
        if (ascending.length == 0)
        {
            return 0L;
        }

        int rank = (int)Math.ceil(percent / 100.0D * ascending.length) - 1;
        return ascending[Math.max(0, Math.min(ascending.length - 1, rank))];
    }

    static double ms(long nanos)
    {
        return nanos / 1_000_000.0D;
    }

    private VertexServerProfiler()
    {
    }
}
