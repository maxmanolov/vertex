package vertex.benchmark.game;

/** Allows one unit of work in each elapsed-time interval. */
public final class FixedRateGate
{
    private final long intervalNanos;
    private long startedAtNanos;
    private long lastInterval = -1L;

    public FixedRateGate(long intervalNanos)
    {
        if (intervalNanos <= 0L)
        {
            throw new IllegalArgumentException("The interval must be positive.");
        }

        this.intervalNanos = intervalNanos;
    }

    public void reset(long nowNanos)
    {
        startedAtNanos = nowNanos;
        lastInterval = -1L;
    }

    public boolean poll(long nowNanos)
    {
        long elapsed = Math.max(0L, nowNanos - startedAtNanos);
        long interval = elapsed / intervalNanos;

        if (interval <= lastInterval)
        {
            return false;
        }

        lastInterval = interval;
        return true;
    }
}
