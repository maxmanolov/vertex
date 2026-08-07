package vertex.benchmark.game;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import vertex.benchmark.capture.FrameCapture;
import vertex.benchmark.capture.FrameCaptureParser;
import vertex.benchmark.capture.FrameTimePreference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BenchmarkFrameRecorderTest
{
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void writesOneFrameIntervalForEachCapturedTick() throws Exception
    {
        Path control = temporary.newFolder("control").toPath();
        FakeClock clock = new FakeClock(1000000000L);
        BenchmarkFrameRecorder.Recorder recorder = recorder(control, clock, 42L, 2, 8);
        Files.write(control.resolve("capture-static"), new byte[0]);

        recorder.tick();
        assertTrue(Files.isRegularFile(control.resolve("capture-started-static")));

        clock.advance(16000000L);
        recorder.tick();
        clock.advance(20000000L);
        recorder.tick();
        Files.delete(control.resolve("capture-static"));
        recorder.tick();

        Path output = control.resolve("frames-static.csv");
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.isRegularFile(control.resolve("capture-complete-static")));
        List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertEquals("Application,ProcessID,SwapChainAddress,FrameTime", lines.get(0));
        assertEquals("Minecraft,42,internal,16.0", lines.get(1));
        assertEquals("Minecraft,42,internal,20.0", lines.get(2));

        FrameCapture capture = new FrameCaptureParser().parse(output,
            FrameTimePreference.PRESENTED);
        assertEquals(2, capture.getSamples().size());
        assertEquals(0, capture.getInvalidRowCount());
        assertTrue(capture.getWarnings().isEmpty());
    }

    @Test
    public void resetsTheSeriesForEachCapture() throws Exception
    {
        Path control = temporary.newFolder("reset").toPath();
        FakeClock clock = new FakeClock(100L);
        BenchmarkFrameRecorder.Recorder recorder = recorder(control, clock, 7L, 1, 8);

        capture(control, clock, recorder, "chunks", 10L, 20L, 30L);
        capture(control, clock, recorder, "blocks", 40L);

        assertEquals(4, Files.readAllLines(control.resolve("frames-chunks.csv"),
            StandardCharsets.UTF_8).size());
        assertEquals(2, Files.readAllLines(control.resolve("frames-blocks.csv"),
            StandardCharsets.UTF_8).size());
    }

    @Test
    public void ignoresUnknownCaptureMarkers() throws Exception
    {
        Path control = temporary.newFolder("unknown").toPath();
        BenchmarkFrameRecorder.Recorder recorder = recorder(control,
            new FakeClock(1L), 1L, 1, 8);
        Files.write(control.resolve("capture-menu"), new byte[0]);

        recorder.tick();

        assertFalse(Files.isRegularFile(control.resolve("capture-started-menu")));
        assertFalse(Files.isRegularFile(control.resolve("failed.txt")));
    }

    @Test
    public void rejectsMoreThanOneCaptureMarker() throws Exception
    {
        Path control = temporary.newFolder("multiple").toPath();
        BenchmarkFrameRecorder.Recorder recorder = recorder(control,
            new FakeClock(1L), 1L, 1, 8);
        Files.write(control.resolve("capture-static"), new byte[0]);
        Files.write(control.resolve("capture-entities"), new byte[0]);

        recorder.tick();

        String failure = new String(Files.readAllBytes(control.resolve("failed.txt")),
            StandardCharsets.UTF_8);
        assertTrue(failure.contains("Only one capture marker is permitted."));
        assertFalse(Files.isRegularFile(control.resolve("capture-started-static")));
        assertFalse(Files.isRegularFile(control.resolve("capture-started-entities")));
    }

    @Test
    public void enforcesTheSampleLimit() throws Exception
    {
        Path control = temporary.newFolder("limit").toPath();
        FakeClock clock = new FakeClock(1L);
        BenchmarkFrameRecorder.Recorder recorder = recorder(control, clock, 1L, 1, 2);
        Files.write(control.resolve("capture-static"), new byte[0]);
        recorder.tick();

        for (int index = 0; index < 3; ++index)
        {
            clock.advance(1L);
            recorder.tick();
        }

        String failure = new String(Files.readAllBytes(control.resolve("failed.txt")),
            StandardCharsets.UTF_8);
        assertTrue(failure.contains("sample limit"));
        assertFalse(Files.isRegularFile(control.resolve("capture-complete-static")));
    }

    private static BenchmarkFrameRecorder.Recorder recorder(Path control, FakeClock clock,
        long processId, int initialCapacity, int maximumCapacity)
    {
        return new BenchmarkFrameRecorder.Recorder(control.toFile(), clock, processId,
            initialCapacity, maximumCapacity, 1);
    }

    private static void capture(Path control, FakeClock clock,
        BenchmarkFrameRecorder.Recorder recorder, String phase, long... intervals)
        throws Exception
    {
        Path marker = control.resolve("capture-" + phase);
        Files.write(marker, new byte[0]);
        recorder.tick();

        for (long interval : intervals)
        {
            clock.advance(interval);
            recorder.tick();
        }

        Files.delete(marker);
        recorder.tick();
    }

    private static final class FakeClock implements BenchmarkFrameRecorder.NanoClock
    {
        private long now;

        private FakeClock(long now)
        {
            this.now = now;
        }

        private void advance(long nanos)
        {
            now += nanos;
        }

        @Override
        public long nanoTime()
        {
            return now;
        }
    }
}
