package vertex.benchmark.quick;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import vertex.benchmark.capture.FrameMetricCalculator;
import vertex.benchmark.capture.FrameMetrics;
import vertex.benchmark.report.RunRecord;
import vertex.benchmark.report.SuiteReportWriter;

public final class MultiScenarioReportWriterTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesLinkedMetricsForEachScenario() throws Exception
    {
        Path output = temporaryFolder.newFolder("benchmark result").toPath();
        Path staticView = output.resolve("scenarios").resolve("static view");
        Path chunkLoad = output.resolve("scenarios").resolve("chunk #load");
        writeScenario(staticView, "static-view", 10.0D, 8.0D);
        writeScenario(chunkLoad, "chunk-load", 20.0D, 10.0D);

        MultiScenarioReportWriter.write(output, Arrays.asList(
            new MultiScenarioReportWriter.Scenario("static", "Static <view>", staticView),
            new MultiScenarioReportWriter.Scenario("chunks", "Chunk loading", chunkLoad)));

        String html = read(output.resolve("summary.html"));
        JsonObject json = new JsonParser().parse(read(output.resolve("summary.json")))
            .getAsJsonObject();
        JsonArray scenarios = json.getAsJsonArray("scenarios");
        JsonObject combined = json.getAsJsonObject("combinedClientIndex");
        JsonObject combinedCandidate = combined.getAsJsonArray("clients").get(1)
            .getAsJsonObject();

        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("Static &lt;view&gt;"));
        assertFalse(html.contains("Static <view>"));
        assertTrue(html.contains("scenarios/static%20view/summary.html"));
        assertTrue(html.contains("scenarios/chunk%20%23load/summary.html"));
        assertTrue(html.contains("125.00"));
        assertTrue(html.contains("+25.00%"));
        assertTrue(html.contains("+20.00%"));
        assertTrue(html.contains("equal-weight index across scenarios, not FPS"));
        assertTrue(html.contains("158.11"));
        assertTrue(html.contains("2 / 2"));
        assertEquals(2, scenarios.size());
        assertEquals(100.0D, combined.get("baseline").getAsDouble(), 0.0D);
        assertEquals(Math.sqrt(1.25D * 2.0D) * 100.0D,
            combinedCandidate.get("index").getAsDouble(), 0.000001D);
        assertEquals(2, combinedCandidate.get("completeScenarios").getAsInt());
        assertTrue(combinedCandidate.get("sufficient").getAsBoolean());

        JsonObject candidate = scenarios.get(0).getAsJsonObject()
            .getAsJsonArray("profiles").get(1).getAsJsonObject();
        assertEquals(125.0D, candidate.getAsJsonObject("metrics")
            .get("meanFps").getAsDouble(), 0.000001D);
        assertEquals(25.0D, candidate.getAsJsonObject("improvementPercentVsBaseline")
            .get("meanFps").getAsDouble(), 0.000001D);
        assertEquals(20.0D, candidate.getAsJsonObject("improvementPercentVsBaseline")
            .get("p99Millis").getAsDouble(), 0.000001D);
    }

    @Test
    public void keepsMissingMetricsAsNullAndNotAvailable() throws Exception
    {
        Path output = temporaryFolder.newFolder("missing-metrics").toPath();
        Path scenario = output.resolve("scenario");
        RunRecord baseline = valid("base", "vanilla", "Vanilla", 1, 1, 10.0D);
        RunRecord failed = RunRecord.builder("bad", 1, 2, "candidate")
            .profileLabel("Candidate")
            .status(RunRecord.Status.FAILED)
            .failure("Capture failed.")
            .build();
        SuiteReportWriter.writeSuite(scenario, "stress", "vanilla", 1,
            Arrays.asList(baseline, failed), Collections.<String>emptyList());

        MultiScenarioReportWriter.write(output, Collections.singletonList(
            new MultiScenarioReportWriter.Scenario("stress", "Stress", scenario)));

        String html = read(output.resolve("summary.html"));
        JsonObject candidate = new JsonParser().parse(read(output.resolve("summary.json")))
            .getAsJsonObject().getAsJsonArray("scenarios").get(0).getAsJsonObject()
            .getAsJsonArray("profiles").get(1).getAsJsonObject();

        assertTrue(html.contains("n/a"));
        assertTrue(html.contains("Insufficient"));
        assertTrue(candidate.getAsJsonObject("metrics").get("meanFps").isJsonNull());
        assertFalse(candidate.get("sufficient").getAsBoolean());
    }

    @Test
    public void marksPartialCombinedIndexAsInsufficient() throws Exception
    {
        Path output = temporaryFolder.newFolder("partial-index").toPath();
        Path complete = output.resolve("complete");
        Path incomplete = output.resolve("incomplete");
        writeScenario(complete, "complete", 10.0D, 8.0D);
        RunRecord baseline = valid("base", "vanilla", "Vanilla", 1, 1, 10.0D);
        RunRecord failed = RunRecord.builder("bad", 1, 2, "candidate")
            .profileLabel("Candidate")
            .status(RunRecord.Status.FAILED)
            .failure("Capture failed.")
            .build();
        SuiteReportWriter.writeSuite(incomplete, "incomplete", "vanilla", 1,
            Arrays.asList(baseline, failed), Collections.<String>emptyList());

        MultiScenarioReportWriter.write(output, Arrays.asList(
            new MultiScenarioReportWriter.Scenario("complete", "Complete", complete),
            new MultiScenarioReportWriter.Scenario("incomplete", "Incomplete",
                incomplete)));

        JsonObject candidate = new JsonParser().parse(read(output.resolve("summary.json")))
            .getAsJsonObject().getAsJsonObject("combinedClientIndex")
            .getAsJsonArray("clients").get(1).getAsJsonObject();
        String html = read(output.resolve("summary.html"));

        assertEquals(125.0D, candidate.get("index").getAsDouble(), 0.000001D);
        assertEquals(1, candidate.get("completeScenarios").getAsInt());
        assertEquals(2, candidate.get("totalScenarios").getAsInt());
        assertFalse(candidate.get("sufficient").getAsBoolean());
        assertTrue(html.contains("1 / 2"));
        assertTrue(html.contains("Insufficient"));
    }

    @Test
    public void rejectsIncompleteScenario() throws Exception
    {
        Path output = temporaryFolder.newFolder("incomplete").toPath();
        Path scenario = output.resolve("scenario");
        Files.createDirectories(scenario);

        try
        {
            MultiScenarioReportWriter.write(output, Collections.singletonList(
                new MultiScenarioReportWriter.Scenario("chunks", "Chunks", scenario)));
        }
        catch (IOException expected)
        {
            assertTrue(expected.getMessage().contains("has no completed summary.json"));
            return;
        }

        throw new AssertionError("Expected an incomplete scenario error.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateScenarioIds() throws Exception
    {
        Path output = temporaryFolder.newFolder("duplicates").toPath();
        Path first = output.resolve("first");
        Path second = output.resolve("second");
        writeScenario(first, "first", 10.0D, 8.0D);
        writeScenario(second, "second", 10.0D, 9.0D);

        MultiScenarioReportWriter.write(output, Arrays.asList(
            new MultiScenarioReportWriter.Scenario("same", "First", first),
            new MultiScenarioReportWriter.Scenario("same", "Second", second)));
    }

    private static void writeScenario(Path output, String suiteId, double baselineTime,
        double candidateTime) throws Exception
    {
        SuiteReportWriter.writeSuite(output, suiteId, "vanilla", 1, Arrays.asList(
            valid("base", "vanilla", "Vanilla 1.7.10", 1, 1, baselineTime),
            valid("candidate", "candidate", "Candidate", 1, 2, candidateTime)),
            Collections.<String>emptyList());
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

    private static String read(Path file) throws Exception
    {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
