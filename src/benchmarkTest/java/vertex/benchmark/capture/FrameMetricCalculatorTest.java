package vertex.benchmark.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FrameMetricCalculatorTest
{
    @Test
    public void calculatesNearestRankMetricsAndSlowFrameCounts()
    {
        double[] values = new double[1000];

        for (int i = 0; i < values.length; ++i)
        {
            values[i] = i + 1.0D;
        }

        FrameMetrics metrics = FrameMetricCalculator.calculateMillis(values);

        assertEquals(1000L, metrics.getFrameCount());
        assertEquals(500500.0D, metrics.getDurationMillis(), 0.0D);
        assertEquals(500.5D, metrics.getMeanFrameTimeMillis(), 0.0D);
        assertEquals(1000.0D / 500.5D, metrics.getFramesPerSecond(), 0.0000001D);
        assertEquals(500.0D, metrics.getP50Millis(), 0.0D);
        assertEquals(950.0D, metrics.getP95Millis(), 0.0D);
        assertEquals(990.0D, metrics.getP99Millis(), 0.0D);
        assertEquals(999.0D, metrics.getP999Millis(), 0.0D);
        assertEquals(1000.0D, metrics.getMaxMillis(), 0.0D);
        assertEquals(1000.0D / 995.5D, metrics.getOnePercentLowFps(), 0.0000001D);
        assertEquals(1.0D, metrics.getPointOnePercentLowFps(), 0.0D);
        assertEquals(984L, metrics.getSlowFrameCountOver16_67Millis());
        assertEquals(967L, metrics.getSlowFrameCountOver33_33Millis());
        assertEquals(950L, metrics.getSlowFrameCountOver50Millis());
        assertEquals(900L, metrics.getSlowFrameCountOver100Millis());
    }

    @Test
    public void lowMetricsUseCeilingSampleCounts()
    {
        double[] values = new double[101];

        for (int i = 0; i < values.length; ++i)
        {
            values[i] = i + 1.0D;
        }

        FrameMetrics metrics = FrameMetricCalculator.calculateMillis(values);

        assertEquals(1000.0D / 100.5D, metrics.getOnePercentLowFps(), 0.0000001D);
        assertEquals(1000.0D / 101.0D, metrics.getPointOnePercentLowFps(),
            0.0000001D);
    }

    @Test
    public void rejectsInvalidValuesBeforeCalculation()
    {
        FrameMetrics metrics = FrameMetricCalculator.calculateMillis(new double[] {
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            -1.0D, 0.0D, 10.0D, 20.0D
        });

        assertEquals(2L, metrics.getFrameCount());
        assertEquals(30.0D, metrics.getDurationMillis(), 0.0D);
        assertEquals(15.0D, metrics.getMeanFrameTimeMillis(), 0.0D);
        assertEquals(20.0D, metrics.getP99Millis(), 0.0D);
        assertEquals(1L, metrics.getSlowFrameCountOver16_67Millis());
    }

    @Test
    public void returnsZeroMetricsForNoValidFrames()
    {
        FrameMetrics metrics = FrameMetricCalculator.calculateMillis(new double[0]);

        assertEquals(0L, metrics.getFrameCount());
        assertEquals(0.0D, metrics.getFramesPerSecond(), 0.0D);
        assertEquals(0.0D, metrics.getP999Millis(), 0.0D);
        assertEquals(0.0D, metrics.getOnePercentLowFps(), 0.0D);
    }
}
