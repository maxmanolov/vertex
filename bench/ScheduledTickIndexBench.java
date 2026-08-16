import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import vertex.api.ScheduledTickPosition;
import vertex.server.IndexedTickSet;

/** Standalone microbenchmark for chunk-local scheduled-tick queries and insertion cost. */
public final class ScheduledTickIndexBench
{
    private static final int TICKS = 120000;
    private static final int QUERY_ROUNDS = 800;
    private static final int ADD_ROUNDS = 9;

    public static void main(String[] args)
    {
        List<Tick> ticks = fixture();
        TreeSet<Object> ordered = new TreeSet<Object>(ticks);
        IndexedTickSet indexed = new IndexedTickSet(new HashSet<Object>(ticks));

        for (int i = 0; i < 100; ++i)
        {
            scanAll(ordered, 0, 0);
            scanNearby(indexed, 0, 0);
        }

        long rawStart = System.nanoTime();
        int rawCount = 0;

        for (int i = 0; i < QUERY_ROUNDS; ++i)
        {
            rawCount += scanAll(ordered, 0, 0);
        }

        long rawNanos = System.nanoTime() - rawStart;
        long indexedStart = System.nanoTime();
        int indexedCount = 0;

        for (int i = 0; i < QUERY_ROUNDS; ++i)
        {
            indexedCount += scanNearby(indexed, 0, 0);
        }

        long indexedNanos = System.nanoTime() - indexedStart;

        if (rawCount != indexedCount)
        {
            throw new AssertionError("query results differ: " + rawCount + " vs " + indexedCount);
        }

        long[] rawAdds = new long[ADD_ROUNDS];
        long[] indexedAdds = new long[ADD_ROUNDS];

        for (int round = 0; round < ADD_ROUNDS; ++round)
        {
            rawAdds[round] = addRaw(ticks);
            indexedAdds[round] = addIndexed(ticks);
        }

        double rawQueryMs = rawNanos / 1000000.0D / QUERY_ROUNDS;
        double indexedQueryMs = indexedNanos / 1000000.0D / QUERY_ROUNDS;
        double rawAddMs = median(rawAdds) / 1000000.0D;
        double indexedAddMs = median(indexedAdds) / 1000000.0D;
        System.out.printf("ticks=%d queryMatches=%d rawQueryMs=%.4f indexedQueryMs=%.4f querySpeedup=%.1fx%n",
            TICKS, rawCount / QUERY_ROUNDS, rawQueryMs, indexedQueryMs, rawQueryMs / indexedQueryMs);
        System.out.printf("rawScheduleMs=%.3f indexedScheduleMs=%.3f scheduleRatio=%.2fx%n",
            rawAddMs, indexedAddMs, indexedAddMs / rawAddMs);
    }

    private static int scanAll(Iterable<Object> values, int chunkX, int chunkZ)
    {
        int matches = 0;
        int minX = (chunkX << 4) - 2;
        int maxX = minX + 20;
        int minZ = (chunkZ << 4) - 2;
        int maxZ = minZ + 20;

        for (Object value : values)
        {
            Tick tick = (Tick)value;

            if (tick.x >= minX && tick.x < maxX && tick.z >= minZ && tick.z < maxZ)
            {
                ++matches;
            }
        }

        return matches;
    }

    private static int scanNearby(IndexedTickSet values, int chunkX, int chunkZ)
    {
        final Iterator<Object> nearby = values.nearby(chunkX, chunkZ, null);
        return scanAll(new Iterable<Object>()
        {
            @Override
            public Iterator<Object> iterator()
            {
                return nearby;
            }
        }, chunkX, chunkZ);
    }

    private static long addRaw(List<Tick> ticks)
    {
        Set<Object> membership = new HashSet<Object>();
        TreeSet<Object> order = new TreeSet<Object>();
        long start = System.nanoTime();

        for (Tick tick : ticks)
        {
            membership.add(tick);
            order.add(tick);
        }

        keep(membership.size() + order.size());
        return System.nanoTime() - start;
    }

    private static long addIndexed(List<Tick> ticks)
    {
        Set<Object> membership = new IndexedTickSet(null);
        TreeSet<Object> order = new TreeSet<Object>();
        long start = System.nanoTime();

        for (Tick tick : ticks)
        {
            membership.add(tick);
            order.add(tick);
        }

        keep(membership.size() + order.size());
        return System.nanoTime() - start;
    }

    private static List<Tick> fixture()
    {
        List<Tick> result = new ArrayList<Tick>(TICKS);
        // 40 x 40 loaded chunks with many pending ticks per chunk models a busy
        // integrated-server world without making every entry its own index bucket.
        int side = 40;

        for (int i = 0; i < TICKS; ++i)
        {
            int chunkX = i % side - side / 2;
            int chunkZ = (i / side) % side - side / 2;
            result.add(new Tick(i, chunkX * 16 + (i & 15), chunkZ * 16 + ((i >>> 4) & 15)));
        }

        return result;
    }

    private static long median(long[] values)
    {
        java.util.Arrays.sort(values);
        return values[values.length / 2];
    }

    private static volatile int sink;

    private static void keep(int value)
    {
        sink = value;
    }

    private static final class Tick implements ScheduledTickPosition, Comparable<Tick>
    {
        final int order;
        final int x;
        final int z;

        Tick(int order, int x, int z)
        {
            this.order = order;
            this.x = x;
            this.z = z;
        }

        @Override
        public int vertex$x()
        {
            return x;
        }

        @Override
        public int vertex$z()
        {
            return z;
        }

        @Override
        public int compareTo(Tick other)
        {
            return order < other.order ? -1 : order == other.order ? 0 : 1;
        }
    }
}
