package vertex.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Test;
import vertex.benchmark.plan.BenchmarkPlan;
import vertex.benchmark.plan.CollectorPlan;
import vertex.benchmark.plan.ProfilePlan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BenchmarkRunnerTest
{
    @Test
    public void dryRunDoesNotCreateAResultDirectory() throws Exception
    {
        Path directory = Files.createTempDirectory("vertex-benchmark-dry-run");
        Path resultDirectory = directory.resolve("results");
        BenchmarkPlan plan = plan(resultDirectory);
        new BenchmarkRunner().run(directory.resolve("plan.json"), plan, null, true);
        assertFalse(Files.exists(resultDirectory));
    }

    @Test
    public void importRunWritesRawRunsAndSuiteSummaries() throws Exception
    {
        Path directory = Files.createTempDirectory("vertex-benchmark-import-run");
        Path resultDirectory = directory.resolve("results");
        Path capture = directory.resolve("capture.csv");
        StringBuilder csv = new StringBuilder(
            "Application,ProcessID,SwapChainAddress,FrameTime\n");

        for (int index = 0; index < 500; ++index)
        {
            csv.append("javaw.exe,4812,0x1,10.0\n");
            csv.append("javaw.exe,4813,0x1,10.0\n");
        }

        Files.write(capture, csv.toString().getBytes(StandardCharsets.UTF_8));
        FakePrompt prompt = new FakePrompt(capture);
        BenchmarkRunner.ProcessStateProbe probe = new BenchmarkRunner.ProcessStateProbe()
        {
            private final Map<Long, Integer> checks = new HashMap<Long, Integer>();

            @Override
            public BenchmarkRunner.ProcessState check(long processId)
            {
                Long key = Long.valueOf(processId);
                Integer count = checks.get(key);
                checks.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));

                if (count == null)
                {
                    return BenchmarkRunner.ProcessState.ALIVE;
                }

                if (count.intValue() == 1)
                {
                    return BenchmarkRunner.ProcessState.UNKNOWN;
                }

                return count.intValue() == 2 ? BenchmarkRunner.ProcessState.ALIVE
                    : BenchmarkRunner.ProcessState.EXITED;
            }
        };

        new BenchmarkRunner(prompt, probe).run(directory.resolve("plan.json"),
            plan(resultDirectory), null, false);

        Path suite = onlyChild(resultDirectory);
        assertTrue(Files.isRegularFile(suite.resolve("summary.json")));
        assertTrue(Files.isRegularFile(suite.resolve("summary.csv")));
        assertTrue(Files.isRegularFile(suite.resolve("summary.md")));
        String summary = new String(Files.readAllBytes(suite.resolve("summary.json")),
            StandardCharsets.UTF_8);
        assertTrue(summary, summary.contains("\"validRuns\": 1"));
        assertTrue(summary, summary.contains("\"sufficient\": true"));

        try (Stream<Path> files = Files.walk(suite.resolve("runs")))
        {
            assertTrue(files.filter(path -> path.getFileName().toString().equals("run.json"))
                .count() == 2L);
        }
    }

    private static Path onlyChild(Path directory) throws IOException
    {
        Path selected = null;

        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory))
        {
            for (Path child : children)
            {
                if (selected != null)
                {
                    throw new AssertionError("Expected one suite directory.");
                }

                selected = child;
            }
        }

        if (selected == null)
        {
            throw new AssertionError("Expected one suite directory.");
        }

        return selected;
    }

    private static BenchmarkPlan plan(Path resultDirectory)
    {
        BenchmarkPlan plan = new BenchmarkPlan();
        plan.setSchemaVersion(1);
        plan.setSuiteId("dry-run");
        plan.setBaselineProfile("vanilla");
        plan.setRepetitions(1);
        plan.setWarmupSeconds(0);
        plan.setCaptureSeconds(5);
        plan.setCooldownSeconds(0);
        plan.setSeed(1L);
        plan.setResultDirectory(resultDirectory.toString());
        CollectorPlan collector = new CollectorPlan();
        collector.setType(CollectorPlan.Type.IMPORT);
        collector.setMetric(CollectorPlan.Metric.PRESENTED);
        plan.setCollector(collector);
        plan.setProfiles(Arrays.asList(profile("vanilla"), profile("vertex")));
        return plan;
    }

    private static ProfilePlan profile(String id)
    {
        ProfilePlan profile = new ProfilePlan();
        profile.setId(id);
        profile.setLabel(id);
        profile.setLaunchMode(ProfilePlan.LaunchMode.MANUAL);
        profile.setProcessName("javaw.exe");
        return profile;
    }

    private static final class FakePrompt extends ConsolePrompt
    {
        private final String capturePath;
        private int processIndex;

        private FakePrompt(Path capture)
        {
            capturePath = capture.toAbsolutePath().toString();
        }

        @Override
        public void waitForEnter(String message)
        {
        }

        @Override
        public String readRequired(String message)
        {
            if (message.contains("process ID"))
            {
                return processIndex++ == 0 ? "4812" : "4813";
            }

            return capturePath;
        }

        @Override
        public void waitSeconds(String phase, int seconds)
        {
        }
    }
}
