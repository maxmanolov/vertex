package vertex.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import vertex.benchmark.capture.FrameCapture;
import vertex.benchmark.capture.FrameCaptureParser;
import vertex.benchmark.capture.FrameMetricCalculator;
import vertex.benchmark.capture.FrameMetrics;
import vertex.benchmark.capture.FrameSeriesSelection;
import vertex.benchmark.capture.FrameTimePreference;

/** Prints one raw capture analysis. */
public final class CaptureAnalyzer
{
    public static void analyze(Path csv, FrameTimePreference preference) throws IOException
    {
        FrameCapture capture = new FrameCaptureParser().parse(csv, preference);
        FrameSeriesSelection series = capture.selectLargestSeries();

        for (String warning : capture.getWarnings())
        {
            System.out.println("Warning: " + warning);
        }

        for (String warning : series.getWarnings())
        {
            System.out.println("Warning: " + warning);
        }

        if (!series.isPresent())
        {
            throw new IOException("No valid frame series is available.");
        }

        FrameMetrics metrics = FrameMetricCalculator.calculateSamples(series.getSamples());
        System.out.println("Series: " + series.getKey());
        System.out.println("Frame-time column: " + capture.getFrameTimeSource());
        System.out.println("Invalid rows: " + capture.getInvalidRowCount());
        System.out.println("Dropped frames: "
            + capture.getDroppedRowCount(series.getKey()));
        System.out.println("Frames: " + metrics.getFrameCount());
        System.out.println("Mean FPS: " + format(metrics.getFramesPerSecond()));
        System.out.println("1% low FPS: " + format(metrics.getOnePercentLowFps()));
        System.out.println("0.1% low FPS: " + format(metrics.getPointOnePercentLowFps()));
        System.out.println("p50: " + format(metrics.getP50Millis()) + " ms");
        System.out.println("p95: " + format(metrics.getP95Millis()) + " ms");
        System.out.println("p99: " + format(metrics.getP99Millis()) + " ms");
        System.out.println("p99.9: " + format(metrics.getP999Millis()) + " ms");
        System.out.println("Maximum: " + format(metrics.getMaxMillis()) + " ms");
    }

    static FrameTimePreference preference(String value)
    {
        if (value == null || value.equalsIgnoreCase("auto"))
        {
            return FrameTimePreference.AUTO;
        }

        if (value.equalsIgnoreCase("presented"))
        {
            return FrameTimePreference.PRESENTED;
        }

        if (value.equalsIgnoreCase("displayed"))
        {
            return FrameTimePreference.DISPLAYED;
        }

        throw new IllegalArgumentException("Metric must be presented, displayed, or auto.");
    }

    private static String format(double value)
    {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private CaptureAnalyzer()
    {
    }
}
