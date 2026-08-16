package vertex.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import vertex.api.ScheduledTickPosition;

public final class IndexedTickSetTest
{
    @Test
    public void indexesNearbyChunksAndKeepsTickOrder()
    {
        Tick first = new Tick(1, 0, 0);
        Tick second = new Tick(2, 17, -1);
        Tick far = new Tick(3, 32, 0);
        IndexedTickSet set = new IndexedTickSet(new HashSet<Object>(Arrays.<Object>asList(far, second, first)));

        assertEquals(Arrays.asList(first, second), collect(set.nearby(0, 0, null)));
        assertTrue(set.contains(first));
        assertTrue(set.remove(first));
        assertFalse(set.contains(first));
        assertEquals(2, set.size());
    }

    @Test
    public void survivesGrowthTombstonesAndIteratorRemoval()
    {
        IndexedTickSet set = new IndexedTickSet(null);
        List<Tick> ticks = new ArrayList<Tick>();

        for (int i = 0; i < 2000; ++i)
        {
            Tick tick = new Tick(i, i * 16, -i * 16);
            ticks.add(tick);
            assertTrue(set.add(tick));
        }

        for (int i = 0; i < ticks.size(); i += 2)
        {
            assertTrue(set.remove(ticks.get(i)));
        }

        int removed = 0;
        Iterator<Object> entries = set.iterator();

        while (entries.hasNext())
        {
            entries.next();
            entries.remove();
            ++removed;
        }

        assertEquals(1000, removed);
        assertTrue(set.isEmpty());
    }

    private static List<Object> collect(Iterator<Object> values)
    {
        List<Object> result = new ArrayList<Object>();

        while (values.hasNext())
        {
            result.add(values.next());
        }

        return result;
    }

    private static final class Tick implements ScheduledTickPosition, Comparable<Tick>
    {
        private final int order;
        private final int x;
        private final int z;

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
