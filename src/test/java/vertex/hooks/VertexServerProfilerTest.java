package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Percentile arithmetic for the server tick profiler. */
public final class VertexServerProfilerTest
{
    @Test
    public void nearestRankPercentilesPickTheRightSample()
    {
        long[] ascending = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};

        assertEquals(5L, VertexServerProfiler.percentile(ascending, 50));
        assertEquals(10L, VertexServerProfiler.percentile(ascending, 95));
        assertEquals(10L, VertexServerProfiler.percentile(ascending, 99));
        assertEquals(1L, VertexServerProfiler.percentile(ascending, 1));
    }

    @Test
    public void aSingleOutlierIsVisibleAtTheTailAndNotAtTheMedian()
    {
        // The whole point of percentiles here: one 200ms hitch among 99 fast ticks must
        // not disappear into a mean, and must not drag the median either.
        long[] ascending = new long[100];

        for (int i = 0; i < 99; ++i)
        {
            ascending[i] = 1_000_000L;
        }

        ascending[99] = 200_000_000L;

        assertEquals(1_000_000L, VertexServerProfiler.percentile(ascending, 50));
        assertEquals(200_000_000L, VertexServerProfiler.percentile(ascending, 100));
    }

    @Test
    public void emptyAndSingleSampleAreSafe()
    {
        assertEquals(0L, VertexServerProfiler.percentile(new long[0], 50));
        assertEquals(7L, VertexServerProfiler.percentile(new long[] {7L}, 99));
    }

    @Test
    public void nanosConvertToMilliseconds()
    {
        assertEquals(1.5D, VertexServerProfiler.ms(1_500_000L), 0.000001D);
    }

    @Test
    public void changingIntegratedServersStartsAFreshWindow() throws Exception
    {
        Object first = new Object();
        Object second = new Object();
        resetState();

        try
        {
            VertexServerProfiler.begin(first);
            VertexServerProfiler.end(first);
            assertEquals(1, intField("count"));

            VertexServerProfiler.begin(second);

            assertSame(second, ((WeakReference<?>)objectField("currentServer")).get());
            assertEquals(0, intField("count"));
            assertEquals(0L, longField("overBudget"));
            assertEquals(0L, longField("dropped"));
        }
        finally
        {
            resetState();
        }
    }

    private static void resetState() throws Exception
    {
        setField("currentServer", new WeakReference<Object>(null));
        setField("count", Integer.valueOf(0));
        setField("windowStart", Long.valueOf(0L));
        setField("tickStart", Long.valueOf(0L));
        setField("overBudget", Long.valueOf(0L));
        setField("dropped", Long.valueOf(0L));
    }

    private static int intField(String name) throws Exception
    {
        return ((Integer)getField(name)).intValue();
    }

    private static long longField(String name) throws Exception
    {
        return ((Long)getField(name)).longValue();
    }

    private static Object objectField(String name) throws Exception
    {
        return getField(name);
    }

    private static Object getField(String name) throws Exception
    {
        Field field = VertexServerProfiler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setField(String name, Object value) throws Exception
    {
        Field field = VertexServerProfiler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
