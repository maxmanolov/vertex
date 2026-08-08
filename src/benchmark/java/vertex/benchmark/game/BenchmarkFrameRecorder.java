package vertex.benchmark.game;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

/** Records game-loop intervals without an external capture tool. */
public final class BenchmarkFrameRecorder
{
    private static final String[] PHASES = {
        "static", "chunks", "blocks", "entities"
    };
    private static final int INITIAL_CAPACITY = 16384;
    private static final int MAX_CAPACITY = 1048576;
    private static final int CONTROL_POLL_TICKS = 8;
    private static final Recorder DEFAULT = new Recorder(controlDirectory(),
        new SystemNanoClock(), currentProcessId(), INITIAL_CAPACITY, MAX_CAPACITY,
        CONTROL_POLL_TICKS);

    /** Records one interval at the head of the client game loop. */
    public static void tick()
    {
        DEFAULT.tick();
    }

    interface NanoClock
    {
        long nanoTime();
    }

    static final class Recorder
    {
        private final File control;
        private final NanoClock clock;
        private final long processId;
        private final int maximumCapacity;
        private final int pollTicks;
        private final File[] captureMarkers;
        private long[] intervals;
        private int size;
        private int activePhase = -1;
        private int ticksToPoll;
        private long previousNanos;
        private boolean failed;

        Recorder(File control, NanoClock clock, long processId, int initialCapacity,
            int maximumCapacity, int pollTicks)
        {
            if (clock == null)
            {
                throw new IllegalArgumentException("The clock must not be null.");
            }

            if (initialCapacity < 1 || maximumCapacity < initialCapacity)
            {
                throw new IllegalArgumentException("The recorder capacity is invalid.");
            }

            if (pollTicks < 1)
            {
                throw new IllegalArgumentException("The control poll rate is invalid.");
            }

            this.control = control;
            this.clock = clock;
            this.processId = Math.max(0L, processId);
            this.maximumCapacity = maximumCapacity;
            this.pollTicks = pollTicks;
            this.intervals = new long[initialCapacity];
            this.captureMarkers = control == null ? null : captureMarkers(control);
        }

        void tick()
        {
            if (control == null || failed)
            {
                return;
            }

            try
            {
                int requestedPhase = activePhase;

                if (ticksToPoll == 0)
                {
                    ticksToPoll = pollTicks - 1;
                    requestedPhase = requestedPhase();
                }
                else
                {
                    --ticksToPoll;
                }

                if (activePhase < 0)
                {
                    if (requestedPhase >= 0)
                    {
                        start(requestedPhase);
                    }

                    return;
                }

                if (requestedPhase < 0)
                {
                    finish();
                    return;
                }

                if (requestedPhase != activePhase)
                {
                    throw new IOException("The capture marker changed during a capture.");
                }

                long now = clock.nanoTime();
                append(Math.max(1L, now - previousNanos));
                previousNanos = now;
            }
            catch (Exception error)
            {
                fail(error);
            }
        }

        private void start(int phase) throws IOException
        {
            activePhase = phase;
            size = 0;
            previousNanos = clock.nanoTime();
            delete(file("capture-complete-" + PHASES[phase]));
            delete(file("frames-" + PHASES[phase] + ".csv"));
            writeMarker(file("capture-started-" + PHASES[phase]), "started\n");
        }

        private void finish() throws IOException
        {
            int completedPhase = activePhase;
            writeCapture(file("frames-" + PHASES[completedPhase] + ".csv"));
            writeMarker(file("capture-complete-" + PHASES[completedPhase]),
                "complete\n");
            activePhase = -1;
            size = 0;
        }

        private void append(long interval) throws IOException
        {
            if (size == maximumCapacity)
            {
                throw new IOException("The frame recorder reached its sample limit.");
            }

            if (size == intervals.length)
            {
                int grown = Math.min(maximumCapacity, intervals.length * 2);
                long[] replacement = new long[grown];
                System.arraycopy(intervals, 0, replacement, 0, size);
                intervals = replacement;
            }

            intervals[size++] = interval;
        }

        private int requestedPhase() throws IOException
        {
            int result = -1;

            for (int index = 0; index < captureMarkers.length; ++index)
            {
                if (!captureMarkers[index].isFile())
                {
                    continue;
                }

                if (result >= 0)
                {
                    throw new IOException("Only one capture marker is permitted.");
                }

                result = index;
            }

            return result;
        }

        private void writeCapture(File target) throws IOException
        {
            control.mkdirs();
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8), 65536);

            try
            {
                output.write("Application,ProcessID,SwapChainAddress,FrameTime\n");

                for (int index = 0; index < size; ++index)
                {
                    output.write("Minecraft,");
                    output.write(Long.toString(processId));
                    output.write(",internal,");
                    output.write(Double.toString(intervals[index] / 1000000.0D));
                    output.write('\n');
                }
            }
            finally
            {
                output.close();
            }
        }

        private void fail(Exception error)
        {
            failed = true;
            String message = error.getMessage();

            try
            {
                writeMarker(file("failed.txt"), "Frame recorder: "
                    + (message == null ? "The capture failed." : message) + "\n");
            }
            catch (IOException ignored)
            {
            }
        }

        private File file(String name)
        {
            return new File(control, name);
        }

        private static File[] captureMarkers(File control)
        {
            File[] result = new File[PHASES.length];

            for (int index = 0; index < PHASES.length; ++index)
            {
                result[index] = new File(control, "capture-" + PHASES[index]);
            }

            return result;
        }

        private static void delete(File file) throws IOException
        {
            if (file.isFile() && !file.delete())
            {
                throw new IOException("Cannot replace " + file.getName() + ".");
            }
        }

        private static void writeMarker(File file, String value) throws IOException
        {
            File parent = file.getParentFile();

            if (parent != null)
            {
                parent.mkdirs();
            }

            FileOutputStream output = new FileOutputStream(file);

            try
            {
                output.write(value.getBytes(StandardCharsets.UTF_8));
            }
            finally
            {
                output.close();
            }
        }
    }

    private static final class SystemNanoClock implements NanoClock
    {
        @Override
        public long nanoTime()
        {
            return System.nanoTime();
        }
    }

    private static File controlDirectory()
    {
        String path = System.getProperty("vertex.benchmark.controlDir");
        return path == null || path.trim().isEmpty() ? null : new File(path);
    }

    private static long currentProcessId()
    {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');
        String value = separator < 0 ? runtimeName : runtimeName.substring(0, separator);

        try
        {
            return Long.parseLong(value);
        }
        catch (NumberFormatException ignored)
        {
            return 0L;
        }
    }

    private BenchmarkFrameRecorder()
    {
    }
}
