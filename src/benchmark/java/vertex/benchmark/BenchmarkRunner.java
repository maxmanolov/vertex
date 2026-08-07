package vertex.benchmark;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import vertex.benchmark.capture.FrameCapture;
import vertex.benchmark.capture.FrameCaptureParser;
import vertex.benchmark.capture.FrameMetricCalculator;
import vertex.benchmark.capture.FrameMetrics;
import vertex.benchmark.capture.FrameSample;
import vertex.benchmark.capture.FrameSeriesKey;
import vertex.benchmark.capture.FrameSeriesSelection;
import vertex.benchmark.capture.FrameTimePreference;
import vertex.benchmark.plan.BenchmarkPlan;
import vertex.benchmark.plan.BenchmarkPlanIO;
import vertex.benchmark.plan.CollectorPlan;
import vertex.benchmark.plan.ProfilePlan;
import vertex.benchmark.report.RunRecord;
import vertex.benchmark.report.SuiteReportWriter;
import vertex.benchmark.result.BenchmarkOrder;
import vertex.benchmark.result.RunSlot;

/** Runs one local profile matrix and keeps every raw capture. */
public final class BenchmarkRunner
{
    private static final int FOCUS_SETTLE_SECONDS = 5;
    private static final DateTimeFormatter DIRECTORY_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private final ConsolePrompt prompt;
    private final ProcessStateProbe processStateProbe;

    public BenchmarkRunner()
    {
        this(new ConsolePrompt(), new LocalProcessStateProbe());
    }

    BenchmarkRunner(ConsolePrompt prompt)
    {
        this(prompt, new LocalProcessStateProbe());
    }

    BenchmarkRunner(ConsolePrompt prompt, ProcessStateProbe processStateProbe)
    {
        if (prompt == null || processStateProbe == null)
        {
            throw new IllegalArgumentException("Runner dependencies must not be null.");
        }

        this.prompt = prompt;
        this.processStateProbe = processStateProbe;
    }

    public void run(Path planPath, BenchmarkPlan plan, String presentMonOverride,
        boolean dryRun) throws Exception
    {
        List<String> suiteWarnings = new ArrayList<String>(FairnessChecker.check(plan));
        List<String> profileIds = new ArrayList<String>();
        Map<String, ProfilePlan> profiles = new LinkedHashMap<String, ProfilePlan>();

        for (ProfilePlan profile : plan.getProfiles())
        {
            profileIds.add(profile.getId());
            profiles.put(profile.getId(), profile);
        }

        List<RunSlot> order = BenchmarkOrder.create(profileIds, plan.getRepetitions(),
            plan.getSeed());
        int fullBlock = BenchmarkOrder.fullBlockSize(profileIds.size());

        if (plan.getRepetitions() % fullBlock != 0)
        {
            suiteWarnings.add("The repetition count does not complete a balanced order block of "
                + fullBlock + " rounds.");
        }

        showPlan(plan, order, profiles, suiteWarnings);

        if (dryRun)
        {
            System.out.println("Dry run complete. No process was started.");
            return;
        }

        long secondsPerRun = (long)plan.getWarmupSeconds()
            + (long)plan.getCaptureSeconds() + (long)plan.getCooldownSeconds()
            + FOCUS_SETTLE_SECONDS;
        long plannedSeconds = (long)order.size() * secondsPerRun;

        if (plannedSeconds > 7200L)
        {
            String confirmation = prompt.readRequired(
                "The planned timed phases exceed two hours. Type RUN to continue.");

            if (!confirmation.equals("RUN"))
            {
                throw new IOException("The suite was not confirmed.");
            }
        }

        Path planDirectory = planPath.toAbsolutePath().getParent();

        if (planDirectory == null)
        {
            planDirectory = Paths.get(".").toAbsolutePath().normalize();
        }

        Path resultRoot = Paths.get(plan.getResultDirectory());

        if (!resultRoot.isAbsolute())
        {
            resultRoot = planDirectory.resolve(resultRoot).normalize();
        }

        Path suiteDirectory = createSuiteDirectory(resultRoot, plan.getSuiteId());
        BenchmarkPlanIO.write(suiteDirectory.resolve("effective-plan.json"),
            PlanRedactor.redact(plan));
        Map<String, String> host = hostFields(presentMonOverride, plan);
        List<RunRecord> records = new ArrayList<RunRecord>();

        for (RunSlot slot : order)
        {
            ProfilePlan profile = profiles.get(slot.getProfileId());
            RunRecord record = executeRun(plan, profile, slot, planDirectory, suiteDirectory,
                presentMonOverride, host);
            records.add(record);
            SuiteReportWriter.writeRun(suiteDirectory, record);

            if (record.getStatus() != RunRecord.Status.VALID)
            {
                suiteWarnings.add("Run " + record.getRunId() + " is "
                    + record.getStatus().getValue() + ": " + record.getFailure());
            }
        }

        SuiteReportWriter.writeSuiteSummary(suiteDirectory, plan.getSuiteId(),
            plan.getBaselineProfile(), plan.getRepetitions(), records, suiteWarnings);
        System.out.println("Suite complete: " + suiteDirectory.toAbsolutePath());
    }

    private RunRecord executeRun(BenchmarkPlan plan, ProfilePlan profile, RunSlot slot,
        Path planDirectory, Path suiteDirectory, String presentMonOverride,
        Map<String, String> host)
    {
        String runId = String.format(Locale.ROOT, "r%02d-p%02d-%s",
            slot.getRound(), slot.getPosition(), profile.getId());
        Path runDirectory = suiteDirectory.resolve("runs").resolve(runId);
        List<String> warnings = new ArrayList<String>();
        Map<String, String> settingsBefore = SettingsSnapshot.capture(
            profile.getSettingsFiles(), planDirectory, warnings);
        Map<String, String> settingsAfter = new LinkedHashMap<String, String>();
        Instant started = Instant.now();
        long startedNanos = System.nanoTime();
        FrameMetrics metrics = null;
        RunRecord.Status status = RunRecord.Status.VALID;
        String failure = null;
        String selectedColumn = null;
        Long processId = null;
        String swapChain = null;
        String rawHash = null;
        int invalidRows = 0;
        int droppedFrames = 0;
        Process clientProcess = null;
        Long targetProcessId = null;

        try
        {
            Files.createDirectories(runDirectory);
            System.out.println();
            System.out.println("Run " + slot.getRound() + ", position " + slot.getPosition()
                + ": " + profile.getLabel());

            for (String instruction : profile.getInstructions())
            {
                System.out.println("- " + instruction);
            }

            clientProcess = new ClientLauncher().launch(profile, slot, runDirectory);
            prompt.waitForEnter("Press Enter when the client and scenario are ready.");
            showMatchingProcesses(profile.getProcessName());
            targetProcessId = Long.valueOf(readProcessId());
            ProcessState targetState = processStateProbe.check(targetProcessId.longValue());

            if (targetState == ProcessState.EXITED)
            {
                throw new IOException("The target process is not active.");
            }

            if (targetState == ProcessState.UNKNOWN)
            {
                warnings.add("The target process state could not be checked before capture.");
            }

            prompt.waitSeconds("Focus settle", FOCUS_SETTLE_SECONDS);
            prompt.waitSeconds("Warm-up", plan.getWarmupSeconds());
            Path rawCsv = runDirectory.resolve("frames.csv");

            if (plan.getCollector().getType() == CollectorPlan.Type.PRESENTMON)
            {
                String executable = presentMonOverride != null
                    ? presentMonOverride : plan.getCollector().getExecutable();

                if (executable == null || executable.trim().isEmpty())
                {
                    throw new IOException("A PresentMon executable is required.");
                }

                System.out.println("Capture started. Keep the client focused.");
                String session = ("VertexBench_"
                    + Integer.toHexString(suiteDirectory.toString().hashCode()) + "_" + runId)
                    .replace('-', '_');
                PresentMonCollector.CaptureResult capture = new PresentMonCollector().capture(
                    executable, targetProcessId.longValue(), plan.getCaptureSeconds(), rawCsv,
                    session);

                if (!capture.isSuccess())
                {
                    throw new IOException(capture.getFailure());
                }
            }
            else
            {
                prompt.waitForEnter("Start the external capture and press Enter.");
                prompt.waitSeconds("Capture", plan.getCaptureSeconds());
                String sourceText = prompt.readRequired(
                    "Stop the external capture. Enter the CSV file path.");
                Path source = Paths.get(sourceText).toAbsolutePath().normalize();

                if (!Files.isRegularFile(source))
                {
                    throw new IOException("Capture CSV is not available: " + source);
                }

                if (!source.equals(rawCsv.toAbsolutePath().normalize()))
                {
                    Files.copy(source, rawCsv, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }

            rawHash = Hashing.sha256(rawCsv);
            FrameCapture capture = new FrameCaptureParser().parse(rawCsv,
                preference(plan.getCollector().getMetric()));
            FrameSeriesSelection selection = selectSeries(capture,
                targetProcessId.longValue());
            warnings.addAll(capture.getWarnings());
            warnings.addAll(selection.getWarnings());
            invalidRows = capture.getInvalidRowCount();
            selectedColumn = capture.getFrameTimeSource();

            if (!selection.isPresent())
            {
                throw new IOException("The capture has no valid frame series.");
            }

            processId = Long.valueOf(selection.getKey().getProcessId());
            swapChain = selection.getKey().getSwapChain();
            droppedFrames = capture.getDroppedRowCount(selection.getKey());

            if (invalidRows > 0)
            {
                String invalid = "The capture contains " + invalidRows + " invalid rows.";
                warnings.add(invalid);
                status = RunRecord.Status.INVALID;
                failure = invalid;
            }

            if (droppedFrames > 0)
            {
                String dropped = "The selected series contains " + droppedFrames
                    + " dropped frames.";
                warnings.add(dropped);
                status = RunRecord.Status.INVALID;
                failure = dropped;
            }

            metrics = FrameMetricCalculator.calculateSamples(selection.getSamples());
            int minimumFrames = Math.max(30, plan.getCaptureSeconds());
            double minimumDuration = plan.getCaptureSeconds() * 1000.0D * 0.80D;

            if (metrics.getFrameCount() < minimumFrames)
            {
                status = RunRecord.Status.INVALID;
                failure = "The capture has fewer than " + minimumFrames + " valid frames.";
            }
            else if (metrics.getDurationMillis() < minimumDuration)
            {
                status = RunRecord.Status.INVALID;
                failure = "The selected series covers less than 80 percent of the capture time.";
            }
        }
        catch (Exception error)
        {
            status = RunRecord.Status.FAILED;
            failure = message(error);
            warnings.add("Run failed: " + failure);
        }

        try
        {
            prompt.waitForEnter("Close the client. Press Enter when it is closed.");
            ProcessState closeState = targetProcessId == null ? ProcessState.EXITED
                : processStateProbe.check(targetProcessId.longValue());
            boolean launcherAlive = clientProcess != null && clientProcess.isAlive();

            while (closeState != ProcessState.EXITED || launcherAlive)
            {
                String closeMessage = closeState == ProcessState.UNKNOWN
                    ? "The target process state is unknown. Check that it is closed, then press Enter."
                    : "A run process is still active. Close it and press Enter.";
                prompt.waitForEnter(closeMessage);
                closeState = targetProcessId == null ? ProcessState.EXITED
                    : processStateProbe.check(targetProcessId.longValue());
                launcherAlive = clientProcess != null && clientProcess.isAlive();
            }

            prompt.waitSeconds("Cooldown", plan.getCooldownSeconds());
        }
        catch (Exception closeError)
        {
            warnings.add("Close step failed: " + message(closeError));

            if (status == RunRecord.Status.VALID)
            {
                status = RunRecord.Status.FAILED;
                failure = message(closeError);
            }

            waitForRunProcessesToExit(clientProcess, targetProcessId);
        }

        settingsAfter.putAll(SettingsSnapshot.capture(profile.getSettingsFiles(),
            planDirectory, warnings));

        if (SettingsSnapshot.hasFailures(settingsBefore)
            || SettingsSnapshot.hasFailures(settingsAfter))
        {
            String unavailable = "One or more configured setting files could not be verified.";
            warnings.add(unavailable);

            if (status == RunRecord.Status.VALID)
            {
                status = RunRecord.Status.INVALID;
                failure = unavailable;
            }
        }

        if (SettingsSnapshot.changed(settingsBefore, settingsAfter))
        {
            String changed = "One or more setting files changed during the run.";
            warnings.add(changed);

            if (status == RunRecord.Status.VALID)
            {
                status = RunRecord.Status.INVALID;
                failure = changed;
            }
        }

        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        RunRecord.Builder builder = RunRecord.builder(runId, slot.getRound(),
            slot.getPosition(), profile.getId())
            .profileLabel(profile.getLabel())
            .profileMetadata(profile.getMetadata())
            .status(status)
            .failure(failure)
            .startedAtUtc(started.toString())
            .finishedAtUtc(Instant.now().toString())
            .timingMillis(Long.valueOf(plan.getWarmupSeconds() * 1000L),
                Long.valueOf(plan.getCaptureSeconds() * 1000L),
                Long.valueOf(plan.getCooldownSeconds() * 1000L),
                Long.valueOf(elapsedMillis))
            .collector(collectorName(plan.getCollector().getType()),
                metricName(plan.getCollector().getMetric()), selectedColumn)
            .rawCsvSha256(rawHash)
            .invalidRowCount(invalidRows)
            .droppedFrameCount(droppedFrames)
            .settingsHashesBefore(settingsBefore)
            .settingsHashesAfter(settingsAfter)
            .hostFields(host)
            .metrics(metrics)
            .warnings(warnings);

        if (processId != null)
        {
            builder.processId(processId.longValue());
        }

        if (swapChain != null)
        {
            builder.swapChain(swapChain);
        }

        RunRecord record = builder.build();
        System.out.println("Run status: " + record.getStatus().getValue());

        if (record.getMetrics() != null)
        {
            System.out.println(String.format(Locale.ROOT,
                "Mean FPS %.2f, 1%% low %.2f, p99 %.2f ms.",
                record.getMetrics().getFramesPerSecond(),
                record.getMetrics().getOnePercentLowFps(),
                record.getMetrics().getP99Millis()));
        }

        return record;
    }

    private void waitForRunProcessesToExit(Process clientProcess, Long targetProcessId)
    {
        int checks = 0;
        ProcessState targetState = targetProcessId == null ? ProcessState.EXITED
            : processStateProbe.check(targetProcessId.longValue());

        while ((clientProcess != null && clientProcess.isAlive())
            || targetState != ProcessState.EXITED)
        {
            if (checks++ % 5 == 0)
            {
                System.out.println("A run process is still active. Stop it to continue.");
            }

            try
            {
                Thread.sleep(1000L);
                targetState = targetProcessId == null ? ProcessState.EXITED
                    : processStateProbe.check(targetProcessId.longValue());
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "The suite stopped while a run process was active.", interrupted);
            }
        }
    }

    private long readProcessId() throws IOException
    {
        while (true)
        {
            String value = prompt.readRequired("Enter the game process ID for this run.");

            try
            {
                long processId = Long.parseLong(value);

                if (processId > 0L)
                {
                    return processId;
                }
            }
            catch (NumberFormatException ignored)
            {
            }

            System.out.println("Enter a positive process ID.");
        }
    }

    private static void showMatchingProcesses(String processName)
    {
        System.out.println("Configured process name: " + processName);

        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
        {
            System.out.println("Use the system process list to find the game process ID.");
            return;
        }

        Process process = null;

        try
        {
            process = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq " + processName,
                "/FO", "TABLE", "/NH").redirectErrorStream(true).start();
            process.getOutputStream().close();

            if (!process.waitFor(5L, TimeUnit.SECONDS))
            {
                process.destroy();
                System.out.println("The matching process list timed out.");
                return;
            }

            BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = output.readLine()) != null)
            {
                if (!line.trim().isEmpty())
                {
                    System.out.println(line);
                }
            }
        }
        catch (Exception error)
        {
            System.out.println("The matching process list is not available.");
        }
        finally
        {
            if (process != null && process.isAlive())
            {
                process.destroy();
            }
        }
    }

    private FrameSeriesSelection selectSeries(FrameCapture capture, long processId)
        throws IOException
    {
        List<FrameSeriesKey> keys = new ArrayList<FrameSeriesKey>();

        for (FrameSeriesKey key : capture.getSeries().keySet())
        {
            if (key.getProcessId() == processId)
            {
                keys.add(key);
            }
        }

        if (keys.isEmpty())
        {
            return capture.selectLargestSeriesForProcess(processId);
        }

        if (keys.size() == 1)
        {
            return capture.selectSeries(keys.get(0));
        }

        System.out.println("The target process has multiple swap-chain series:");

        for (int index = 0; index < keys.size(); ++index)
        {
            FrameSeriesKey key = keys.get(index);
            List<FrameSample> samples = capture.getSeries().get(key);
            String chain = key.getSwapChain().isEmpty() ? "<empty>" : key.getSwapChain();
            System.out.println((index + 1) + ". " + chain + ": " + samples.size()
                + " valid frames");
        }

        while (true)
        {
            String value = prompt.readRequired("Enter the swap-chain number for this run.");

            try
            {
                int index = Integer.parseInt(value) - 1;

                if (index >= 0 && index < keys.size())
                {
                    return capture.selectSeries(keys.get(index));
                }
            }
            catch (NumberFormatException ignored)
            {
            }

            System.out.println("Enter a listed swap-chain number.");
        }
    }

    enum ProcessState
    {
        ALIVE,
        EXITED,
        UNKNOWN
    }

    interface ProcessStateProbe
    {
        ProcessState check(long processId);
    }

    private static final class LocalProcessStateProbe implements ProcessStateProbe
    {
        @Override
        public ProcessState check(long processId)
        {
            try
            {
                Class<?> processHandle = Class.forName("java.lang.ProcessHandle");
                Method of = processHandle.getMethod("of", Long.TYPE);
                Object optional = of.invoke(null, Long.valueOf(processId));
                Method isPresent = optional.getClass().getMethod("isPresent");

                if (!((Boolean)isPresent.invoke(optional)).booleanValue())
                {
                    return ProcessState.EXITED;
                }

                Method get = optional.getClass().getMethod("get");
                Object handle = get.invoke(optional);
                Method isAlive = processHandle.getMethod("isAlive");
                return ((Boolean)isAlive.invoke(handle)).booleanValue()
                    ? ProcessState.ALIVE : ProcessState.EXITED;
            }
            catch (ClassNotFoundException legacyRuntime)
            {
                return legacyCheck(processId);
            }
            catch (Exception error)
            {
                return ProcessState.UNKNOWN;
            }
        }

        private static ProcessState legacyCheck(long processId)
        {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

            if (os.contains("win"))
            {
                return runProbe(new String[] {"tasklist", "/FI", "PID eq " + processId,
                    "/FO", "CSV", "/NH"}, "\",\"" + processId + "\",\"");
            }

            if (os.contains("linux"))
            {
                return Files.exists(Paths.get("/proc", Long.toString(processId)))
                    ? ProcessState.ALIVE : ProcessState.EXITED;
            }

            return runProbe(new String[] {"ps", "-p", Long.toString(processId),
                "-o", "pid="}, Long.toString(processId));
        }

        private static ProcessState runProbe(String[] command, String marker)
        {
            Process process = null;

            try
            {
                process = new ProcessBuilder(command).redirectErrorStream(true).start();
                process.getOutputStream().close();

                if (!process.waitFor(5L, TimeUnit.SECONDS))
                {
                    process.destroy();
                    return ProcessState.UNKNOWN;
                }

                BufferedReader output = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                String line;
                boolean found = false;

                while ((line = output.readLine()) != null)
                {
                    if (line.contains(marker))
                    {
                        found = true;
                    }
                }

                if (process.exitValue() != 0)
                {
                    return ProcessState.UNKNOWN;
                }

                return found ? ProcessState.ALIVE : ProcessState.EXITED;
            }
            catch (Exception error)
            {
                if (process != null)
                {
                    process.destroy();
                }

                return ProcessState.UNKNOWN;
            }
        }
    }

    private static void showPlan(BenchmarkPlan plan, List<RunSlot> order,
        Map<String, ProfilePlan> profiles, List<String> warnings)
    {
        System.out.println("Suite: " + plan.getSuiteId());
        System.out.println("Baseline: " + plan.getBaselineProfile());
        System.out.println("Runs: " + order.size());

        for (String warning : warnings)
        {
            System.out.println("Warning: " + warning);
        }

        for (RunSlot slot : order)
        {
            System.out.println("Round " + slot.getRound() + ", position "
                + slot.getPosition() + ": " + profiles.get(slot.getProfileId()).getLabel());
        }
    }

    private static Path createSuiteDirectory(Path root, String suiteId) throws IOException
    {
        Files.createDirectories(root);
        String baseName = suiteId + "-" + DIRECTORY_TIME.format(Instant.now());

        for (int suffix = 0; suffix < 1000; ++suffix)
        {
            Path candidate = root.resolve(suffix == 0 ? baseName : baseName + "-" + suffix);

            try
            {
                return Files.createDirectory(candidate);
            }
            catch (java.nio.file.FileAlreadyExistsException exists)
            {
            }
        }

        throw new IOException("Cannot allocate a new suite directory.");
    }

    private static Map<String, String> hostFields(String presentMonOverride,
        BenchmarkPlan plan)
    {
        Map<String, String> host = new LinkedHashMap<String, String>();
        put(host, "osName", System.getProperty("os.name"));
        put(host, "osVersion", System.getProperty("os.version"));
        put(host, "osArch", System.getProperty("os.arch"));
        put(host, "javaVersion", System.getProperty("java.version"));
        put(host, "javaVendor", System.getProperty("java.vendor"));
        put(host, "logicalProcessors", Integer.toString(
            Runtime.getRuntime().availableProcessors()));
        put(host, "maxHeapMB", Long.toString(
            Runtime.getRuntime().maxMemory() / (1024L * 1024L)));
        put(host, "cpu", System.getenv("PROCESSOR_IDENTIFIER"));
        String executable = presentMonOverride != null
            ? presentMonOverride : plan.getCollector().getExecutable();

        if (executable != null)
        {
            try
            {
                Path file = Paths.get(executable);

                if (Files.isRegularFile(file))
                {
                    put(host, "collectorSha256", Hashing.sha256(file));
                }
            }
            catch (IOException ignored)
            {
            }
            catch (RuntimeException ignored)
            {
            }
        }

        return host;
    }

    private static void put(Map<String, String> target, String name, String value)
    {
        if (value != null && !value.trim().isEmpty())
        {
            target.put(name, value);
        }
    }

    private static FrameTimePreference preference(CollectorPlan.Metric metric)
    {
        if (metric == CollectorPlan.Metric.PRESENTED)
        {
            return FrameTimePreference.PRESENTED;
        }

        if (metric == CollectorPlan.Metric.DISPLAYED)
        {
            return FrameTimePreference.DISPLAYED;
        }

        return FrameTimePreference.AUTO;
    }

    private static String collectorName(CollectorPlan.Type type)
    {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String metricName(CollectorPlan.Metric metric)
    {
        return metric.name().toLowerCase(Locale.ROOT);
    }

    private static String message(Exception error)
    {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
            ? error.getClass().getSimpleName() : value;
    }
}
