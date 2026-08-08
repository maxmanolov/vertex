package vertex.benchmark.game;

/** One-shot monotonic gate used before publishing a benchmark phase as ready. */
final class ScenarioWarmupGate
{
    private final long warmupNanos;
    private long startedAtNanos;
    private boolean started;
    private boolean published;

    ScenarioWarmupGate(long warmupNanos)
    {
        if (warmupNanos < 0L)
        {
            throw new IllegalArgumentException("Scenario warmup must not be negative.");
        }

        this.warmupNanos = warmupNanos;
    }

    void start(long nowNanos)
    {
        startedAtNanos = nowNanos;
        started = true;
        published = false;
    }

    boolean shouldPublish(long nowNanos)
    {
        if (!started || published || nowNanos - startedAtNanos < warmupNanos)
        {
            return false;
        }

        published = true;
        return true;
    }
}
