package vertex.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs one bounded PresentMon console capture. */
public final class PresentMonCollector
{
    public CaptureResult capture(String executable, long processId, int seconds,
        Path outputFile, String sessionName) throws IOException, InterruptedException
    {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"))
        {
            throw new IOException("PresentMon capture requires Windows. Use the import collector on this host.");
        }

        Path parent = outputFile.toAbsolutePath().getParent();

        if (parent != null)
        {
            Files.createDirectories(parent);
        }

        Path logFile = outputFile.resolveSibling("presentmon.log");
        List<String> command = buildCommand(executable, processId, seconds,
            outputFile, sessionName);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile.toFile());
        Process process = builder.start();

        try
        {
            boolean finished = process.waitFor(seconds + 60L, TimeUnit.SECONDS);

            if (!finished)
            {
                stop(process);
                return new CaptureResult(false, -1, true, logFile,
                    "PresentMon did not stop after the capture limit.");
            }

            int exitCode = process.exitValue();

            if (exitCode != 0)
            {
                return new CaptureResult(false, exitCode, false, logFile,
                    "PresentMon exited with code " + exitCode + ". Read " + logFile + ".");
            }

            if (!Files.isRegularFile(outputFile) || Files.size(outputFile) == 0L)
            {
                return new CaptureResult(false, exitCode, false, logFile,
                    "PresentMon did not write frame data. Read " + logFile + ".");
            }

            return new CaptureResult(true, exitCode, false, logFile, null);
        }
        finally
        {
            if (process.isAlive())
            {
                stop(process);
            }
        }
    }

    private static void stop(Process process) throws InterruptedException
    {
        process.destroy();

        if (!process.waitFor(5L, TimeUnit.SECONDS))
        {
            process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
        }
    }

    static List<String> buildCommand(String executable, long processId, int seconds,
        Path outputFile, String sessionName)
    {
        List<String> command = new ArrayList<String>();
        command.add(executable);
        command.add("--process_id");
        command.add(Long.toString(processId));
        command.add("--output_file");
        command.add(outputFile.toAbsolutePath().toString());
        command.add("--timed");
        command.add(Integer.toString(seconds));
        command.add("--terminate_after_timed");
        command.add("--v1_metrics");
        command.add("--no_console_stats");
        command.add("--session_name");
        command.add(sessionName);
        return command;
    }

    public static final class CaptureResult
    {
        private final boolean success;
        private final int exitCode;
        private final boolean timedOut;
        private final Path logFile;
        private final String failure;

        CaptureResult(boolean success, int exitCode, boolean timedOut, Path logFile, String failure)
        {
            this.success = success;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.logFile = logFile;
            this.failure = failure;
        }

        public boolean isSuccess()
        {
            return success;
        }

        public int getExitCode()
        {
            return exitCode;
        }

        public boolean isTimedOut()
        {
            return timedOut;
        }

        public Path getLogFile()
        {
            return logFile;
        }

        public String getFailure()
        {
            return failure;
        }
    }
}
