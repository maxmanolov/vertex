package vertex.benchmark.game;

/** Calculates phase positions from elapsed time, not rendered frame count. */
public final class ScenarioMotion
{
    private static final double NANOS_PER_SECOND = 1000000000.0D;

    public static double chunkX(double anchor, long elapsedNanos)
    {
        return anchor + seconds(elapsedNanos) * 24.0D;
    }

    public static double chunkZ(double anchor, long elapsedNanos)
    {
        return anchor + Math.sin(seconds(elapsedNanos) * 0.35D) * 32.0D;
    }

    public static double entityX(double anchor, int index, int count, long elapsedNanos)
    {
        return anchor + 18.0D + Math.cos(entityAngle(index, count, elapsedNanos))
            * entityRadius(index);
    }

    public static double entityY(double anchor, int index)
    {
        return anchor + 1.0D + (index % 5) * 1.5D;
    }

    public static double entityZ(double anchor, int index, int count, long elapsedNanos)
    {
        return anchor + Math.sin(entityAngle(index, count, elapsedNanos))
            * entityRadius(index);
    }

    private static double entityAngle(int index, int count, long elapsedNanos)
    {
        int safeCount = Math.max(1, count);
        double initial = Math.PI * 2.0D * index / safeCount;
        double direction = (index & 1) == 0 ? 1.0D : -1.0D;
        return initial + seconds(elapsedNanos) * direction * (0.35D + (index % 4) * 0.05D);
    }

    private static double entityRadius(int index)
    {
        return 5.0D + (index % 4) * 3.0D;
    }

    private static double seconds(long elapsedNanos)
    {
        return Math.max(0L, elapsedNanos) / NANOS_PER_SECOND;
    }

    private ScenarioMotion()
    {
    }
}
