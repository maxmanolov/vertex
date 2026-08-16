package vertex.hooks;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Comparator;
import org.junit.Test;
import vertex.api.DistanceKeyHost;

public final class VertexRenderOrderTest
{
    @Test
    public void derivesEachKeyOnceAndOrdersNearestFirst()
    {
        Section[] sections = {
            new Section(24, 8, 8), new Section(8, 8, 8), new Section(-8, 8, 8),
            new Section(8, 24, 8), new Section(8, 8, 24)};
        CameraComparator vanilla = new CameraComparator(8.0D, 8.0D, 8.0D);

        VertexRenderOrder.sort(sections, vanilla);

        assertEquals(0.0D, sections[0].key, 0.0D);
        assertEquals(256.0D, sections[1].key, 0.0D);
        assertEquals(256.0D, sections[2].key, 0.0D);
        assertEquals(256.0D, sections[3].key, 0.0D);
        assertEquals(256.0D, sections[4].key, 0.0D);

        for (Section section : sections)
        {
            assertEquals(1, section.writes);
        }
    }

    @Test
    public void delegatesArraysWithoutDistanceHosts()
    {
        Integer[] values = {4, 1, 3, 2};
        VertexRenderOrder.sort(values, new Comparator<Integer>()
        {
            public int compare(Integer left, Integer right)
            {
                return right.compareTo(left);
            }
        });
        assertArrayEquals(new Integer[] {4, 3, 2, 1}, values);
    }

    private static final class CameraComparator implements Comparator<Section>
    {
        private final double a;
        private final double b;
        private final double c;

        CameraComparator(double x, double y, double z)
        {
            this.a = -x;
            this.b = -y;
            this.c = -z;
        }

        public int compare(Section left, Section right)
        {
            return Double.compare(distance(left), distance(right));
        }

        private double distance(Section section)
        {
            double dx = section.x + this.a;
            double dy = section.y + this.b;
            double dz = section.z + this.c;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final class Section implements DistanceKeyHost
    {
        final int x;
        final int y;
        final int z;
        double key;
        int writes;

        Section(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double vertex$sortKey() { return this.key; }
        public void vertex$setSortKey(double key) { this.key = key; ++this.writes; }
        public int vertex$centerX() { return this.x; }
        public int vertex$centerY() { return this.y; }
        public int vertex$centerZ() { return this.z; }
    }
}
