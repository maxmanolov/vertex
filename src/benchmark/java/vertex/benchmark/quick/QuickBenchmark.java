package vertex.benchmark.quick;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import vertex.benchmark.CliArguments;
import vertex.benchmark.Hashing;
import vertex.benchmark.ResultOpener;
import vertex.benchmark.capture.FrameCapture;
import vertex.benchmark.capture.FrameCaptureParser;
import vertex.benchmark.capture.FrameMetricCalculator;
import vertex.benchmark.capture.FrameMetrics;
import vertex.benchmark.capture.FrameSeriesKey;
import vertex.benchmark.capture.FrameSeriesSelection;
import vertex.benchmark.capture.FrameTimePreference;
import vertex.benchmark.report.RunRecord;
import vertex.benchmark.report.SuiteReportWriter;
import vertex.benchmark.result.BenchmarkOrder;
import vertex.benchmark.result.RunSlot;

/** Runs the no-plan, no-PID benchmark flow for standard client JARs. */
public final class QuickBenchmark
{
    private static final DateTimeFormatter DIRECTORY_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final long START_TIMEOUT_SECONDS = 45L;
    private static final long READY_TIMEOUT_SECONDS = 180L;
    private static final long STOP_TIMEOUT_SECONDS = 30L;
    private static final ScenarioSpec[] SCENARIOS = {
        new ScenarioSpec("static", "Static world rendering"),
        new ScenarioSpec("chunks", "Chunk travel at 24 blocks per second"),
        new ScenarioSpec("blocks", "1,920 block and lighting updates per second"),
        new ScenarioSpec("entities", "160 moving pigs with AI and collision")
    };

    public Path run(CliArguments arguments) throws Exception
    {
        QuickPreset preset = QuickPreset.parse(arguments.option("preset"));
        Path requestedMinecraft = arguments.option("mcdir") == null ? null
            : Paths.get(arguments.option("mcdir"));
        MinecraftInstallDiscovery.Installation found =
            new MinecraftInstallDiscovery().discover(requestedMinecraft);
        LegacyInstallation installation = LegacyInstallation.resolve(
            found.getMinecraftDirectory());
        List<Path> supplied = candidatePaths(arguments.getPositionals());
        List<ClientProfile> profiles = profiles(supplied, installation);
        List<String> profileIds = new ArrayList<String>();

        for (ClientProfile profile : profiles)
        {
            profileIds.add(profile.id);
        }

        List<RunSlot> order = BenchmarkOrder.create(profileIds, preset.getRepetitions(),
            19700101L);
        showPlan(preset, profiles, order);

        if (arguments.flag("dry-run"))
        {
            System.out.println("Dry run complete. No file was written and no process was started.");
            return null;
        }

        Path suite = createSuiteDirectory(dataDirectory().resolve("results"));
        Map<String, List<RunRecord>> records = new LinkedHashMap<String, List<RunRecord>>();
        Map<String, List<String>> warnings = new LinkedHashMap<String, List<String>>();
        boolean allRunsValid = true;

        for (ScenarioSpec scenario : SCENARIOS)
        {
            records.put(scenario.id, new ArrayList<RunRecord>());
            warnings.put(scenario.id, new ArrayList<String>());
        }

        if (preset == QuickPreset.FAST)
        {
            for (List<String> scenarioWarnings : warnings.values())
            {
                scenarioWarnings.add(
                    "The fast preset is a smoke comparison with one run per client.");
            }
        }

        System.out.println("Do not use the computer until the suite is complete.");

        for (RunSlot slot : order)
        {
            ClientProfile profile = profile(profiles, slot.getProfileId());
            List<RunRecord> completed = executeRun(installation, profile, slot, preset,
                suite);

            for (int index = 0; index < SCENARIOS.length; ++index)
            {
                ScenarioSpec scenario = SCENARIOS[index];
                RunRecord record = completed.get(index);
                records.get(scenario.id).add(record);
                Path scenarioDirectory = scenarioDirectory(suite, scenario);
                SuiteReportWriter.writeRun(scenarioDirectory, record);

                if (!record.isValid())
                {
                    allRunsValid = false;
                    warnings.get(scenario.id).add("Run " + record.getRunId() + " is "
                        + record.getStatus().getValue() + ": " + record.getFailure());
                }
            }
        }

        List<MultiScenarioReportWriter.Scenario> scenarioReports =
            new ArrayList<MultiScenarioReportWriter.Scenario>();

        for (ScenarioSpec scenario : SCENARIOS)
        {
            Path directory = scenarioDirectory(suite, scenario);
            SuiteReportWriter.writeSuiteSummary(directory, "quick-1.7.10-" + scenario.id,
                "vanilla", preset.getRepetitions(), records.get(scenario.id),
                warnings.get(scenario.id));
            scenarioReports.add(new MultiScenarioReportWriter.Scenario(scenario.id,
                scenario.label, directory));
        }

        MultiScenarioReportWriter.write(suite, scenarioReports);
        System.out.println("Suite complete: " + suite.toAbsolutePath());

        if (!new ResultOpener().open(suite.resolve(MultiScenarioReportWriter.SUMMARY_HTML),
            arguments.flag("no-open")))
        {
            System.out.println("Open this report: "
                + suite.resolve(MultiScenarioReportWriter.SUMMARY_HTML).toAbsolutePath());
        }

        if (!allRunsValid)
        {
            throw new IOException("The suite contains invalid runs. Review "
                + suite.resolve(MultiScenarioReportWriter.SUMMARY_HTML).toAbsolutePath());
        }

        return suite;
    }

    private List<RunRecord> executeRun(LegacyInstallation installation, ClientProfile profile,
        RunSlot slot, QuickPreset preset, Path suite)
    {
        String runId = String.format(Locale.ROOT, "r%02d-p%02d-%s", slot.getRound(),
            slot.getPosition(), profile.id);
        Path runDirectory = suite.resolve("processes").resolve(runId);
        Path control = runDirectory.resolve("control");
        Process client = null;
        Long processId = null;
        List<RunRecord> records = new ArrayList<RunRecord>();

        System.out.println();
        System.out.println("Run " + slot.getRound() + ", position " + slot.getPosition()
            + ": " + profile.label);

        try
        {
            Files.createDirectories(runDirectory);
            client = new LegacyClientLauncher().launch(installation, profile.jar,
                profile.tweaker, runDirectory, preset.getSettleMillis());
            processId = Long.valueOf(waitForProcessId(control, client));
            waitForReady(control, client);

            for (ScenarioSpec scenario : SCENARIOS)
            {
                records.add(executeScenario(profile, slot, preset, suite,
                    runId, control, client, processId.longValue(), scenario));
            }
        }
        catch (Exception error)
        {
            String failure = message(error);
            System.out.println("Client run failed: " + failure);

            while (records.size() < SCENARIOS.length)
            {
                ScenarioSpec scenario = SCENARIOS[records.size()];
                records.add(failedRecord(profile, slot, preset, runId, scenario,
                    processId, failure));
            }
        }
        finally
        {
            try
            {
                Files.createDirectories(control);
                Files.write(control.resolve("stop"), "stop\n".getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            catch (Exception ignored)
            {
            }

            if (!stop(client))
            {
                System.out.println("The client needed a forced stop.");
            }

            cooldown(preset.getCooldownSeconds());
        }

        return records;
    }

    private RunRecord executeScenario(ClientProfile profile, RunSlot slot,
        QuickPreset preset, Path suite, String runId, Path control,
        Process client, long processId, ScenarioSpec scenario)
    {
        Instant started = Instant.now();
        long startNanos = System.nanoTime();
        long setupStarted = System.nanoTime();
        Path output = scenarioDirectory(suite, scenario).resolve("runs").resolve(runId);
        Path frames = output.resolve("frames.csv");
        FrameMetrics metrics = null;
        RunRecord.Status status = RunRecord.Status.VALID;
        String failure = null;
        String column = null;
        String swapChain = null;
        String rawHash = null;
        int invalidRows = 0;
        int droppedFrames = 0;
        long serverTicks = -1L;
        List<String> warnings = new ArrayList<String>();

        System.out.println("Scenario: " + scenario.label);

        try
        {
            Files.createDirectories(output);
            activateScenario(control, scenario, client);
            long setupMillis = (System.nanoTime() - setupStarted) / 1000000L;
            startInternalCapture(control, scenario, client);
            long ticksBefore = readServerTicks(control);
            System.out.println("Capture started: " + preset.getCaptureSeconds() + " seconds.");
            waitForCapture(control, client, scenario, preset.getCaptureSeconds());
            Path rawCapture = control.resolve("frames-" + scenario.id + ".csv");
            Files.copy(rawCapture, frames, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            long ticksAfter = readServerTicks(control);
            serverTicks = ticksAfter - ticksBefore;
            int minimumTicks = preset.getCaptureSeconds() * 16;

            if (serverTicks < minimumTicks)
            {
                status = RunRecord.Status.INVALID;
                failure = "The integrated server ran fewer than 16 ticks per second.";
                warnings.add(failure);
            }

            rawHash = Hashing.sha256(frames);
            FrameCapture capture = new FrameCaptureParser().parse(frames,
                FrameTimePreference.PRESENTED);
            List<FrameSeriesKey> matching = matchingSeries(capture, processId);

            if (matching.size() != 1)
            {
                throw new IOException(matching.isEmpty()
                    ? "The capture has no frames for the game process."
                    : "The game process has multiple frame series.");
            }

            FrameSeriesSelection selection = capture.selectSeries(matching.get(0));
            invalidRows = capture.getInvalidRowCount();
            droppedFrames = capture.getDroppedRowCount(matching.get(0));
            warnings.addAll(capture.getWarnings());
            warnings.addAll(selection.getWarnings());
            column = capture.getFrameTimeSource();
            swapChain = matching.get(0).getSwapChain();

            if (invalidRows > 0)
            {
                status = RunRecord.Status.INVALID;
                failure = "The capture contains invalid game-loop intervals.";
            }

            metrics = FrameMetricCalculator.calculateSamples(selection.getSamples());
            int minimumFrames = Math.max(30, preset.getCaptureSeconds());
            double minimumDuration = preset.getCaptureSeconds() * 800.0D;

            if (metrics.getFrameCount() < minimumFrames
                || metrics.getDurationMillis() < minimumDuration)
            {
                status = RunRecord.Status.INVALID;
                failure = "The capture does not cover enough of the timed run.";
            }

            System.out.println(String.format(Locale.ROOT,
                "Mean FPS %.2f, 1%% low %.2f, p99 %.2f ms.",
                metrics.getFramesPerSecond(), metrics.getOnePercentLowFps(),
                metrics.getP99Millis()));
            return buildRecord(profile, slot, preset, runId, started, startNanos,
                setupMillis, processId, metrics, status, failure, column, swapChain,
                rawHash, invalidRows, droppedFrames, serverTicks, warnings);
        }
        catch (Exception error)
        {
            status = RunRecord.Status.FAILED;
            failure = message(error);
            warnings.add("Scenario failed: " + failure);
            System.out.println("Scenario failed: " + failure);
            long setupMillis = (System.nanoTime() - setupStarted) / 1000000L;
            return buildRecord(profile, slot, preset, runId, started, startNanos,
                setupMillis, processId, metrics, status, failure, column, swapChain,
                rawHash, invalidRows, droppedFrames, serverTicks, warnings);
        }
    }

    private RunRecord buildRecord(ClientProfile profile, RunSlot slot, QuickPreset preset,
        String runId, Instant started, long startNanos, long setupMillis, long processId,
        FrameMetrics metrics, RunRecord.Status status, String failure, String column,
        String swapChain, String rawHash, int invalidRows, int droppedFrames,
        long serverTicks, List<String> warnings)
    {
        long elapsed = (System.nanoTime() - startNanos) / 1000000L;
        Map<String, String> host = hostFields();

        if (serverTicks >= 0L)
        {
            host.put("serverTicks", Long.toString(serverTicks));
        }

        RunRecord.Builder builder = RunRecord.builder(runId, slot.getRound(),
            slot.getPosition(), profile.id)
            .profileLabel(profile.label)
            .profileMetadata(profile.metadata)
            .status(status)
            .failure(failure)
            .startedAtUtc(started.toString())
            .finishedAtUtc(Instant.now().toString())
            .timingMillis(Long.valueOf(setupMillis),
                Long.valueOf(preset.getCaptureSeconds() * 1000L),
                Long.valueOf(preset.getCooldownSeconds() * 1000L), Long.valueOf(elapsed))
            .collector("internal-game-loop", "game-loop-interval", column)
            .rawCsvSha256(rawHash)
            .invalidRowCount(invalidRows)
            .droppedFrameCount(droppedFrames)
            .droppedFrameCountAvailable(false)
            .hostFields(host)
            .metrics(metrics)
            .warnings(warnings);

        if (processId > 0L)
        {
            builder.processId(processId);
        }

        if (swapChain != null)
        {
            builder.swapChain(swapChain);
        }

        return builder.build();
    }

    private RunRecord failedRecord(ClientProfile profile, RunSlot slot, QuickPreset preset,
        String runId, ScenarioSpec scenario, Long processId, String failure)
    {
        List<String> warnings = new ArrayList<String>();
        warnings.add("Scenario failed: " + failure);
        return buildRecord(profile, slot, preset, runId, Instant.now(), System.nanoTime(),
            0L, processId == null ? 0L : processId.longValue(), null,
            RunRecord.Status.FAILED, failure, null, null, null, 0, 0, -1L, warnings);
    }

    private static List<ClientProfile> profiles(List<Path> candidates,
        LegacyInstallation installation) throws IOException
    {
        List<ClientProfile> profiles = new ArrayList<ClientProfile>();
        profiles.add(ClientProfile.vanilla());
        ClientArtifactClassifier classifier = new ClientArtifactClassifier();
        Set<String> hashes = new LinkedHashSet<String>();
        int vertexCount = 0;
        int optifineCount = 0;

        for (Path candidate : candidates)
        {
            DetectedClient detected = classifier.classify(candidate);

            if (!detected.isSupported())
            {
                throw new IOException(candidate + ": " + detected.getReason());
            }

            if (detected.getType() == DetectedClient.Type.VANILLA_1_7_10)
            {
                continue;
            }

            String hash = Hashing.sha256(detected.getPath());

            if (!hashes.add(hash))
            {
                continue;
            }

            if (detected.getType() == DetectedClient.Type.VERTEX)
            {
                ++vertexCount;
                profiles.add(ClientProfile.candidate("vertex" + suffix(vertexCount),
                    vertexCount == 1 ? "Vertex" : "Vertex " + vertexCount,
                    detected.getPath(), "vertex.VertexTweaker", "vertex", hash));
            }
            else if (detected.getType() == DetectedClient.Type.OPTIFINE)
            {
                ++optifineCount;
                profiles.add(ClientProfile.candidate("optifine" + suffix(optifineCount),
                    optifineCount == 1 ? "OptiFine" : "OptiFine " + optifineCount,
                    detected.getPath(), "optifine.OptiFineTweaker", "optifine", hash));
            }
        }

        if (profiles.size() < 2)
        {
            throw new IOException("Drop a Vertex or supported OptiFine 1.7.10 JAR to compare it with vanilla.");
        }

        return profiles;
    }

    private static String suffix(int count)
    {
        return count == 1 ? "" : Integer.toString(count);
    }

    private static List<Path> candidatePaths(List<String> values) throws IOException
    {
        List<Path> result = new ArrayList<Path>();

        if (values.isEmpty())
        {
            if (java.awt.GraphicsEnvironment.isHeadless())
            {
                throw new IOException("Drop a client JAR on benchmark.cmd.");
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select client JARs to compare with vanilla 1.7.10");
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("Client JAR files", "jar"));

            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
            {
                throw new IOException("No client JAR was selected.");
            }

            for (java.io.File file : chooser.getSelectedFiles())
            {
                result.add(file.toPath().toAbsolutePath().normalize());
            }
        }
        else
        {
            Set<Path> unique = new LinkedHashSet<Path>();

            for (String value : values)
            {
                unique.add(Paths.get(value).toAbsolutePath().normalize());
            }

            result.addAll(unique);
        }

        return result;
    }

    private static long waitForProcessId(Path control, Process client) throws Exception
    {
        Path pidFile = control.resolve("pid.txt");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline)
        {
            checkFailure(control, client);

            if (Files.isRegularFile(pidFile))
            {
                String value = new String(Files.readAllBytes(pidFile), StandardCharsets.UTF_8)
                    .trim();
                long processId = Long.parseLong(value);

                if (processId > 0L)
                {
                    return processId;
                }
            }

            Thread.sleep(200L);
        }

        throw new IOException("The client did not publish its process ID.");
    }

    private static void waitForReady(Path control, Process client) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(READY_TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline)
        {
            checkFailure(control, client);

            if (Files.isRegularFile(control.resolve("ready")))
            {
                return;
            }

            Thread.sleep(250L);
        }

        throw new IOException("The benchmark world did not become ready within three minutes.");
    }

    private static void activateScenario(Path control, ScenarioSpec scenario, Process client)
        throws Exception
    {
        for (ScenarioSpec item : SCENARIOS)
        {
            Files.deleteIfExists(control.resolve("phase-" + item.id));
        }

        Files.write(control.resolve("phase-" + scenario.id),
            (scenario.id + "\n").getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Path ready = control.resolve("ready-" + scenario.id);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(READY_TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline)
        {
            checkFailure(control, client);

            if (Files.isRegularFile(ready))
            {
                return;
            }

            Thread.sleep(250L);
        }

        throw new IOException(scenario.label + " did not become ready within three minutes.");
    }

    private static void startInternalCapture(Path control, ScenarioSpec scenario,
        Process client) throws Exception
    {
        Path marker = control.resolve("capture-" + scenario.id);
        Path started = control.resolve("capture-started-" + scenario.id);
        Path complete = control.resolve("capture-complete-" + scenario.id);
        Files.deleteIfExists(started);
        Files.deleteIfExists(complete);
        Files.deleteIfExists(control.resolve("frames-" + scenario.id + ".csv"));
        Files.write(marker, (scenario.id + "\n").getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);

        while (System.nanoTime() < deadline)
        {
            checkFailure(control, client);

            if (Files.isRegularFile(started))
            {
                return;
            }

            Thread.sleep(50L);
        }

        Files.deleteIfExists(marker);
        throw new IOException("The internal frame capture did not start.");
    }

    private static void waitForCapture(Path control, Process client, ScenarioSpec scenario,
        int seconds) throws Exception
    {
        Path marker = control.resolve("capture-" + scenario.id);
        Path complete = control.resolve("capture-complete-" + scenario.id);
        long captureEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);

        try
        {
            while (System.nanoTime() < captureEnd)
            {
                checkFailure(control, client);
                long remaining = captureEnd - System.nanoTime();
                long sleepMillis = Math.max(1L, Math.min(200L,
                    TimeUnit.NANOSECONDS.toMillis(Math.max(0L, remaining))));
                Thread.sleep(sleepMillis);
            }
        }
        finally
        {
            Files.deleteIfExists(marker);
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);

        while (System.nanoTime() < deadline)
        {
            checkFailure(control, client);

            if (Files.isRegularFile(complete))
            {
                return;
            }

            Thread.sleep(50L);
        }

        throw new IOException("The internal frame capture did not finish.");
    }

    private static long readServerTicks(Path control) throws Exception
    {
        Path file = control.resolve("server-ticks.txt");

        for (int attempt = 0; attempt < 20; ++attempt)
        {
            if (Files.isRegularFile(file))
            {
                try
                {
                    String value = new String(Files.readAllBytes(file),
                        StandardCharsets.UTF_8).trim();

                    if (!value.isEmpty())
                    {
                        return Long.parseLong(value);
                    }
                }
                catch (NumberFormatException changingFile)
                {
                }
            }

            Thread.sleep(50L);
        }

        throw new IOException("The integrated-server tick counter is not available.");
    }

    private static void checkFailure(Path control, Process client) throws Exception
    {
        Path failure = control.resolve("failed.txt");

        if (Files.isRegularFile(failure))
        {
            throw new IOException(new String(Files.readAllBytes(failure),
                StandardCharsets.UTF_8).trim());
        }

        if (client != null && !client.isAlive())
        {
            throw new IOException("The client exited before the benchmark was ready. Read client.log.");
        }
    }

    private static boolean stop(Process process)
    {
        if (process == null || !process.isAlive())
        {
            return true;
        }

        try
        {
            if (process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            {
                return true;
            }

            process.destroy();

            if (process.waitFor(5L, TimeUnit.SECONDS))
            {
                return false;
            }

            process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
            return false;
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return false;
        }
    }

    private static List<FrameSeriesKey> matchingSeries(FrameCapture capture, long processId)
    {
        List<FrameSeriesKey> result = new ArrayList<FrameSeriesKey>();

        for (FrameSeriesKey key : capture.getSeries().keySet())
        {
            if (key.getProcessId() == processId)
            {
                result.add(key);
            }
        }

        return result;
    }

    private static void cooldown(int seconds)
    {
        if (seconds <= 0)
        {
            return;
        }

        System.out.println("Cooldown: " + seconds + " seconds.");

        try
        {
            Thread.sleep(seconds * 1000L);
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static void showPlan(QuickPreset preset, List<ClientProfile> profiles,
        List<RunSlot> order)
    {
        System.out.println("Vertex 1.7.10 quick benchmark");
        System.out.println("Preset: " + preset.getValue());
        System.out.println("Profiles:");

        for (ClientProfile profile : profiles)
        {
            System.out.println("- " + profile.label);
        }

        System.out.println("Runs: " + order.size());
        System.out.println("Scenarios per run: " + SCENARIOS.length);
        long plannedSeconds = order.size() * (preset.getSettleMillis() / 1000L
            + (long)SCENARIOS.length * preset.getCaptureSeconds()
            + preset.getCooldownSeconds());
        System.out.println("Estimated time: about "
            + Math.max(1L, (plannedSeconds + 59L) / 60L) + " minutes.");
    }

    private static ClientProfile profile(List<ClientProfile> profiles, String id)
    {
        for (ClientProfile profile : profiles)
        {
            if (profile.id.equals(id))
            {
                return profile;
            }
        }

        throw new IllegalStateException("Unknown profile: " + id);
    }

    private static Path createSuiteDirectory(Path root) throws IOException
    {
        Files.createDirectories(root);
        String base = "quick-1.7.10-" + DIRECTORY_TIME.format(Instant.now());

        for (int suffix = 0; suffix < 1000; ++suffix)
        {
            Path candidate = root.resolve(suffix == 0 ? base : base + "-" + suffix);

            try
            {
                return Files.createDirectory(candidate);
            }
            catch (java.nio.file.FileAlreadyExistsException exists)
            {
            }
        }

        throw new IOException("Cannot create a benchmark result directory.");
    }

    private static Path scenarioDirectory(Path suite, ScenarioSpec scenario)
    {
        return suite.resolve("scenarios").resolve(scenario.id);
    }

    private static Path dataDirectory()
    {
        String local = System.getenv("LOCALAPPDATA");
        Path root = local == null || local.trim().isEmpty()
            ? Paths.get(System.getProperty("user.home"), ".vertex-benchmark")
            : Paths.get(local).resolve("VertexBenchmark");
        return root.toAbsolutePath().normalize();
    }

    private static Map<String, String> hostFields()
    {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("osName", System.getProperty("os.name", "unknown"));
        values.put("osVersion", System.getProperty("os.version", "unknown"));
        values.put("osArch", System.getProperty("os.arch", "unknown"));
        values.put("harnessJavaVersion", System.getProperty("java.version", "unknown"));
        values.put("logicalProcessors", Integer.toString(
            Runtime.getRuntime().availableProcessors()));
        return values;
    }

    private static String message(Exception error)
    {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
            ? error.getClass().getSimpleName() : value;
    }

    private static final class ClientProfile
    {
        private final String id;
        private final String label;
        private final Path jar;
        private final String tweaker;
        private final Map<String, String> metadata;

        private ClientProfile(String id, String label, Path jar, String tweaker,
            Map<String, String> metadata)
        {
            this.id = id;
            this.label = label;
            this.jar = jar;
            this.tweaker = tweaker;
            this.metadata = metadata;
        }

        private static ClientProfile vanilla()
        {
            Map<String, String> metadata = new LinkedHashMap<String, String>();
            metadata.put("clientType", "vanilla");
            metadata.put("minecraftVersion", "1.7.10");
            return new ClientProfile("vanilla", "Vanilla 1.7.10", null, null, metadata);
        }

        private static ClientProfile candidate(String id, String label, Path jar,
            String tweaker, String type, String hash)
        {
            Map<String, String> metadata = new LinkedHashMap<String, String>();
            metadata.put("clientType", type);
            metadata.put("minecraftVersion", "1.7.10");
            metadata.put("artifactSha256", hash);
            return new ClientProfile(id, label, jar, tweaker, metadata);
        }
    }

    private static final class ScenarioSpec
    {
        private final String id;
        private final String label;

        private ScenarioSpec(String id, String label)
        {
            this.id = id;
            this.label = label;
        }
    }
}
