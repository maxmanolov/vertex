package vertex.benchmark.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import vertex.benchmark.capture.FrameMetricCalculator;
import vertex.benchmark.capture.FrameMetrics;

public final class SuiteReportWriterTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void usesPositiveImprovementsForBetterResults()
    {
        List<RunRecord> records = Arrays.asList(
            valid("base-1", "vanilla", "Vanilla", 1, 1, 10.0D),
            valid("fast-1", "fast", "Fast", 1, 2, 8.0D),
            valid("base-2", "vanilla", "Vanilla", 2, 1, 20.0D),
            valid("fast-2", "fast", "Fast", 2, 2, 10.0D)
        );

        JsonObject comparison = comparisons(
            SuiteReportWriter.toSummaryJson("vanilla", 2, records)).get(0)
            .getAsJsonObject();
        JsonObject improvements = comparison.get("medianImprovementPercent")
            .getAsJsonObject();

        assertEquals(62.5D, median(improvements, "framesPerSecond"), 0.000001D);
        assertEquals(35.0D, median(improvements, "meanFrameTimeMillis"), 0.000001D);
        assertEquals(35.0D, median(improvements, "p99Millis"), 0.000001D);
        assertEquals(62.5D, median(improvements, "onePercentLowFps"), 0.000001D);
    }

    @Test
    public void keepsOutliersInProfileRangesAndMedians()
    {
        List<RunRecord> records = Arrays.asList(
            valid("base-1", "vanilla", "Vanilla", 1, 1, 10.0D),
            valid("base-2", "vanilla", "Vanilla", 2, 1, 1000.0D),
            valid("base-3", "vanilla", "Vanilla", 3, 1, 20.0D)
        );

        JsonObject root = json(SuiteReportWriter.toSummaryJson("vanilla", 3, records));
        JsonObject profile = root.get("profiles").getAsJsonArray().get(0)
            .getAsJsonObject();
        JsonObject frameTime = profile.get("metrics").getAsJsonObject()
            .get("meanFrameTimeMillis").getAsJsonObject();

        assertFalse(root.get("outliersRemoved").getAsBoolean());
        assertEquals(20.0D, frameTime.get("median").getAsDouble(), 0.0D);
        assertEquals(10.0D, frameTime.get("minimum").getAsDouble(), 0.0D);
        assertEquals(1000.0D, frameTime.get("maximum").getAsDouble(), 0.0D);
    }

    @Test
    public void countsInvalidRunsAndWritesNullForEmptyMetrics()
    {
        RunRecord invalid = RunRecord.builder("bad-1", 1, 2, "bad")
            .profileLabel("Bad")
            .status(RunRecord.Status.INVALID)
            .failure("No usable frame rows.")
            .build();
        String output = SuiteReportWriter.toSummaryJson("vanilla", 1, Arrays.asList(
            valid("base-1", "vanilla", "Vanilla", 1, 1, 10.0D), invalid));
        JsonObject profile = json(output).get("profiles").getAsJsonArray().get(1)
            .getAsJsonObject();
        JsonObject fps = profile.get("metrics").getAsJsonObject()
            .get("framesPerSecond").getAsJsonObject();

        assertEquals(0, profile.get("validRuns").getAsInt());
        assertEquals(1, profile.get("invalidRuns").getAsInt());
        assertFalse(profile.get("sufficient").getAsBoolean());
        assertTrue(fps.get("median").isJsonNull());
        assertFalse(output.contains("NaN"));
        assertFalse(output.contains("Infinity"));
    }

    @Test
    public void escapesCsvLabels()
    {
        String label = "Fast, \"quoted\"\nclient";
        String csv = SuiteReportWriter.toSummaryCsv("vanilla", 1, Arrays.asList(
            valid("base-1", "vanilla", "Vanilla", 1, 1, 10.0D),
            valid("fast-1", "fast", label, 1, 2, 8.0D)
        ));

        assertTrue(csv.contains("\"Fast, \"\"quoted\"\"\nclient\""));
    }

    @Test
    public void writesKeyMarkdownContent()
    {
        String markdown = SuiteReportWriter.toSummaryMarkdown("vanilla", 1,
            Arrays.asList(
                valid("base-1", "vanilla", "Vanilla", 1, 1, 10.0D),
                valid("fast-1", "fast", "Fast", 1, 2, 8.0D)
            ));

        assertTrue(markdown.contains("# Benchmark summary"));
        assertTrue(markdown.contains("Positive values mean that the candidate performs better."));
        assertTrue(markdown.contains("P99 frame time"));
        assertTrue(markdown.contains("Frames over 100 ms"));
        assertTrue(markdown.contains("+25.000%"));
    }

    @Test
    public void writesRunRecordBesideCaptureFile() throws Exception
    {
        FrameMetrics metrics = constantMetrics(8.0D);
        RunRecord record = RunRecord.builder("r01/p02", 1, 2, "fast")
            .profileLabel("Fast")
            .profileMetadata("version", "1.7.10")
            .startedAtUtc("2026-08-07T00:00:00Z")
            .finishedAtUtc("2026-08-07T00:01:00Z")
            .timingMillis(Long.valueOf(1000L), Long.valueOf(2000L),
                Long.valueOf(3000L), Long.valueOf(6000L))
            .collector("presentmon", "presented", "MsBetweenPresents")
            .processId(1710L)
            .swapChain("0x123")
            .rawCsvSha256("abc123")
            .invalidRowCount(2)
            .droppedFrameCount(3)
            .settingsHashBefore("options.txt", "before")
            .settingsHashAfter("options.txt", "after")
            .hostField("os", "Windows")
            .metrics(metrics)
            .warning("One row was incomplete.")
            .build();
        Path output = temporaryFolder.newFolder("suite").toPath();

        Path recordFile = SuiteReportWriter.writeRun(output, record);
        String json = new String(Files.readAllBytes(recordFile), StandardCharsets.UTF_8);

        assertEquals(output.resolve("runs").resolve("r01_p02").resolve("run.json"),
            recordFile);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"selectedColumn\": \"MsBetweenPresents\""));
        assertTrue(json.contains("\"rawCsvSha256\": \"abc123\""));
        assertTrue(json.contains("\"droppedFrames\": 3"));
        assertTrue(json.contains("\"onePercentLowFps\""));
    }

    @Test
    public void writesAllSuiteFiles() throws Exception
    {
        Path output = temporaryFolder.newFolder("complete-suite").toPath();
        List<RunRecord> records = Collections.singletonList(
            valid("base-1", "vanilla", "Vanilla", 1, 1, 10.0D));

        SuiteReportWriter.writeSuite(output, "quality,parity", "vanilla", 1,
            records, Collections.singletonList("Keep the power mode constant."));

        assertTrue(Files.isRegularFile(output.resolve("runs").resolve("base-1")
            .resolve("run.json")));
        assertTrue(Files.isRegularFile(output.resolve("summary.json")));
        assertTrue(Files.isRegularFile(output.resolve("summary.csv")));
        assertTrue(Files.isRegularFile(output.resolve("summary.md")));
        String csv = new String(Files.readAllBytes(output.resolve("summary.csv")),
            StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"quality,parity\""));
    }

    @Test
    public void writesSuiteSummaryWithoutRewritingRunFiles() throws Exception
    {
        Path output = temporaryFolder.newFolder("summary-only").toPath();
        RunRecord saved = valid("base-1", "vanilla", "Vanilla", 1, 1, 10.0D);
        Path runFile = SuiteReportWriter.writeRun(output, saved);
        byte[] sentinel = "saved run record\n".getBytes(StandardCharsets.UTF_8);
        Files.write(runFile, sentinel);

        List<RunRecord> updated = Collections.singletonList(
            valid("base-1", "vanilla", "Vanilla", 1, 1, 8.0D));
        SuiteReportWriter.writeSuiteSummary(output, "summary-only", "vanilla", 1,
            updated, Collections.<String>emptyList());

        assertTrue(Arrays.equals(sentinel, Files.readAllBytes(runFile)));
        assertTrue(Files.isRegularFile(output.resolve("summary.json")));
        assertTrue(Files.isRegularFile(output.resolve("summary.csv")));
        assertTrue(Files.isRegularFile(output.resolve("summary.md")));
    }

    private static RunRecord valid(String runId, String profileId, String label,
        int round, int position, double frameTimeMillis)
    {
        return RunRecord.builder(runId, round, position, profileId)
            .profileLabel(label)
            .metrics(constantMetrics(frameTimeMillis))
            .build();
    }

    private static FrameMetrics constantMetrics(double frameTimeMillis)
    {
        double[] frames = new double[100];
        Arrays.fill(frames, frameTimeMillis);
        return FrameMetricCalculator.calculateMillis(frames);
    }

    private static JsonObject json(String value)
    {
        return new JsonParser().parse(value).getAsJsonObject();
    }

    private static JsonArray comparisons(String value)
    {
        return json(value).get("comparisons").getAsJsonArray();
    }

    private static double median(JsonObject metrics, String name)
    {
        return metrics.get(name).getAsJsonObject().get("median").getAsDouble();
    }
}
