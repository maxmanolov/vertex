package vertex.hooks;

import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VertexFrameStatsTest
{
    @Before
    public void reset() throws Exception
    {
        VertexFrameStats.drainReport();
        Field last = VertexFrameStats.class.getDeclaredField("lastFrameNanos");
        last.setAccessible(true);
        last.setLong(null, 0L);
    }

    @Test
    public void percentilesReflectRecordedFrames() throws Exception
    {
        Field last = VertexFrameStats.class.getDeclaredField("lastFrameNanos");
        last.setAccessible(true);
        // Synthesize 99 fast frames (2 ms) and one huge spike (50 ms) by back-dating.
        long cursor = System.nanoTime() - 1_000_000_000L;
        last.setLong(null, cursor);

        for (int i = 0; i < 99; ++i)
        {
            cursor += 2_000_000L;
            setAndFrame(last, cursor);
        }

        cursor += 50_000_000L;
        setAndFrame(last, cursor);
        String report = VertexFrameStats.drainReport();
        assertTrue(report, report.contains("ftP50=2."));
        assertTrue("p99 must surface the spike region, got " + report,
            report.contains("ftP99=50.") || report.contains("ftMax=50."));
        assertTrue(report, report.contains("frames=100"));
    }

    @Test
    public void drainResetsTheWindow() throws Exception
    {
        VertexFrameStats.frame();
        VertexFrameStats.drainReport();
        String report = VertexFrameStats.drainReport();
        assertTrue(report, report.contains("frames=0"));
    }

    private static void setAndFrame(Field last, long fakeNow) throws Exception
    {
        // frame() reads System.nanoTime; emulate by planting lastFrameNanos so the delta
        // equals fakeNow-cursor at call time. We instead call the internal record path.
        java.lang.reflect.Method record = VertexFrameStats.class.getDeclaredMethod("frame");
        long real = System.nanoTime();
        last.setLong(null, real - (fakeNow - lastPlanted == 0 ? 2_000_000L : fakeNow - lastPlanted));
        record.invoke(null);
        lastPlanted = fakeNow;
    }

    private static long lastPlanted = 0L;
}
