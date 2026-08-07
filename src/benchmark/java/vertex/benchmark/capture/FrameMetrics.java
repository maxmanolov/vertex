package vertex.benchmark.capture;

/** Stores calculated frame-time metrics. */
public final class FrameMetrics
{
    private final long frameCount;
    private final double durationMillis;
    private final double meanFrameTimeMillis;
    private final double framesPerSecond;
    private final double p50Millis;
    private final double p95Millis;
    private final double p99Millis;
    private final double p999Millis;
    private final double maxMillis;
    private final double onePercentLowFps;
    private final double pointOnePercentLowFps;
    private final long slowOver16_67;
    private final long slowOver33_33;
    private final long slowOver50;
    private final long slowOver100;

    FrameMetrics(long frameCount, double durationMillis, double meanFrameTimeMillis,
        double framesPerSecond, double p50Millis, double p95Millis, double p99Millis,
        double p999Millis, double maxMillis, double onePercentLowFps,
        double pointOnePercentLowFps, long slowOver16_67, long slowOver33_33,
        long slowOver50, long slowOver100)
    {
        this.frameCount = frameCount;
        this.durationMillis = durationMillis;
        this.meanFrameTimeMillis = meanFrameTimeMillis;
        this.framesPerSecond = framesPerSecond;
        this.p50Millis = p50Millis;
        this.p95Millis = p95Millis;
        this.p99Millis = p99Millis;
        this.p999Millis = p999Millis;
        this.maxMillis = maxMillis;
        this.onePercentLowFps = onePercentLowFps;
        this.pointOnePercentLowFps = pointOnePercentLowFps;
        this.slowOver16_67 = slowOver16_67;
        this.slowOver33_33 = slowOver33_33;
        this.slowOver50 = slowOver50;
        this.slowOver100 = slowOver100;
    }

    public long getFrameCount() { return frameCount; }
    public double getDurationMillis() { return durationMillis; }
    public double getMeanFrameTimeMillis() { return meanFrameTimeMillis; }
    public double getFramesPerSecond() { return framesPerSecond; }
    public double getP50Millis() { return p50Millis; }
    public double getP95Millis() { return p95Millis; }
    public double getP99Millis() { return p99Millis; }
    public double getP999Millis() { return p999Millis; }
    public double getMaxMillis() { return maxMillis; }
    public double getOnePercentLowFps() { return onePercentLowFps; }
    public double getPointOnePercentLowFps() { return pointOnePercentLowFps; }
    public long getSlowFrameCountOver16_67Millis() { return slowOver16_67; }
    public long getSlowFrameCountOver33_33Millis() { return slowOver33_33; }
    public long getSlowFrameCountOver50Millis() { return slowOver50; }
    public long getSlowFrameCountOver100Millis() { return slowOver100; }
}
