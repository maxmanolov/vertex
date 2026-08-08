package vertex.benchmark.capture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Calculates metrics from valid frame times. */
public final class FrameMetricCalculator
{
    private static final double EMPTY_VALUE = 0.0D;

    public static FrameMetrics calculateSamples(Iterable<FrameSample> samples)
    {
        if (samples == null)
        {
            throw new IllegalArgumentException("Samples must not be null.");
        }

        List<Double> values = new ArrayList<Double>();

        for (FrameSample sample : samples)
        {
            if (sample != null)
            {
                values.add(sample.getFrameTimeMillis());
            }
        }

        double[] frameTimes = new double[values.size()];

        for (int i = 0; i < values.size(); ++i)
        {
            frameTimes[i] = values.get(i);
        }

        return calculateMillis(frameTimes);
    }

    /** Rejects non-finite and non-positive values before calculation. */
    public static FrameMetrics calculateMillis(double[] frameTimesMillis)
    {
        if (frameTimesMillis == null)
        {
            throw new IllegalArgumentException("Frame times must not be null.");
        }

        double[] valid = new double[frameTimesMillis.length];
        int count = 0;

        for (double value : frameTimesMillis)
        {
            if (!Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0D)
            {
                valid[count++] = value;
            }
        }

        if (count == 0)
        {
            return new FrameMetrics(0L, EMPTY_VALUE, EMPTY_VALUE, EMPTY_VALUE,
                EMPTY_VALUE, EMPTY_VALUE, EMPTY_VALUE, EMPTY_VALUE, EMPTY_VALUE,
                EMPTY_VALUE, EMPTY_VALUE, 0L, 0L, 0L, 0L);
        }

        valid = Arrays.copyOf(valid, count);
        Arrays.sort(valid);
        double sum = 0.0D;
        long over16 = 0L;
        long over33 = 0L;
        long over50 = 0L;
        long over100 = 0L;

        for (double value : valid)
        {
            sum += value;

            if (value > 16.67D) { ++over16; }
            if (value > 33.33D) { ++over33; }
            if (value > 50.0D) { ++over50; }
            if (value > 100.0D) { ++over100; }
        }

        double mean = sum / count;
        return new FrameMetrics(count, sum, mean, 1000.0D / mean,
            nearestRank(valid, 0.50D), nearestRank(valid, 0.95D),
            nearestRank(valid, 0.99D), nearestRank(valid, 0.999D),
            valid[count - 1], lowFps(valid, 0.01D), lowFps(valid, 0.001D),
            over16, over33, over50, over100);
    }

    private static double nearestRank(double[] sorted, double percentile)
    {
        int rank = (int)Math.ceil(percentile * sorted.length);
        return sorted[Math.max(1, rank) - 1];
    }

    private static double lowFps(double[] sorted, double fraction)
    {
        int sampleCount = (int)Math.ceil(sorted.length * fraction);
        double sum = 0.0D;

        for (int i = sorted.length - sampleCount; i < sorted.length; ++i)
        {
            sum += sorted[i];
        }

        return 1000.0D / (sum / sampleCount);
    }

    private FrameMetricCalculator()
    {
    }
}
