package vertex.hooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import vertex.api.ScheduledTickPosition;
import vertex.server.IndexedTickSet;

public final class VertexScheduledTicksTest
{
    @Test
    public void narrowsCandidatesAndRemovesFromTheSourceTree()
    {
        World world = new World();
        Tick first = new Tick(1, 0, 0);
        Tick border = new Tick(2, 17, 0);
        Tick far = new Tick(3, 32, 0);
        world.add(first);
        world.add(border);
        world.add(far);

        VertexScheduledTicks.install(world);
        assertTrue(world.M instanceof IndexedTickSet);

        Iterator<?> candidates = VertexScheduledTicks.candidateIterator(world, new Chunk(0, 0));
        assertEquals(first, candidates.next());

        // The mapped vanilla caller removes from M immediately before Iterator.remove.
        assertTrue(world.M.remove(first));
        candidates.remove();
        assertFalse(world.N.contains(first));
        assertEquals(border, candidates.next());
        assertFalse(candidates.hasNext());
        assertTrue(world.N.contains(far));
    }

    private static final class World
    {
        private Set<Object> M = new HashSet<Object>();
        private TreeSet<Object> N = new TreeSet<Object>();

        void add(Tick tick)
        {
            M.add(tick);
            N.add(tick);
        }
    }

    private static final class Chunk
    {
        public final int g;
        public final int h;

        Chunk(int x, int z)
        {
            g = x;
            h = z;
        }
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
