package vertex.lights;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure diffing for dynamic light sources: given the previous and current snapshots
 * (groups of four ints: x, y, z, level), yields the origins of every render section that
 * can contain a positive contribution from a source that appeared, vanished or changed.
 * Capped so a burst of sources cannot flood the priority queue; the per-frame consumption
 * cap bounds the rebuild cost downstream anyway.
 */
public final class DynamicSourceTracker
{
    public static final int MAX_REMARKS = 8;

    private int[] previous = new int[0];
    private final List<int[]> pending = new ArrayList<int[]>();

    /**
     * Returns section origins (groups of three ints) to re-mark, and retains the snapshot.
     * The per-call cap DELAYS work rather than deleting it (kyrofx #31): positions beyond
     * the cap queue up and drain on later calls, so no section keeps stale light.
     */
    public int[] update(int[] current)
    {
        collectDelta(this.previous, current, this.pending);
        collectDelta(current, this.previous, this.pending);
        this.previous = current;
        int count = Math.min(this.pending.size(), MAX_REMARKS);
        int[] out = new int[count * 3];

        for (int i = 0; i < count; ++i)
        {
            int[] pos = this.pending.get(i);
            out[i * 3] = pos[0];
            out[i * 3 + 1] = pos[1];
            out[i * 3 + 2] = pos[2];
        }

        this.pending.subList(0, count).clear();
        return out;
    }

    private static void collectDelta(int[] from, int[] to, List<int[]> remarks)
    {
        for (int i = 0; i < from.length; i += 4)
        {
            boolean present = false;

            for (int j = 0; j < to.length; j += 4)
            {
                if (from[i] == to[j] && from[i + 1] == to[j + 1] && from[i + 2] == to[j + 2] && from[i + 3] == to[j + 3])
                {
                    present = true;
                    break;
                }
            }

            if (!present)
            {
                addAffectedSections(remarks, from[i], from[i + 1], from[i + 2], from[i + 3]);
            }
        }
    }

    /** Adds each 16x16x16 render section that intersects the source's light radius. */
    private static void addAffectedSections(List<int[]> remarks, int x, int y, int z, int level)
    {
        int effectiveLevel = Math.max(0, Math.min(15, level));

        if (effectiveLevel == 0)
        {
            return;
        }

        int radius = effectiveLevel - 1;
        int minSectionX = horizontalSection((long)x - radius);
        int maxSectionX = horizontalSection((long)x + radius);
        int minSectionY = Math.max(0, section((long)y - radius));
        int maxSectionY = Math.min(15, section((long)y + radius));
        int minSectionZ = horizontalSection((long)z - radius);
        int maxSectionZ = horizontalSection((long)z + radius);

        if (minSectionY > maxSectionY)
        {
            return;
        }

        for (int sectionX = minSectionX; sectionX <= maxSectionX; ++sectionX)
        {
            long baseX = (long)sectionX << 4;
            long distanceX = distanceToRange(x, baseX, baseX + 15L);

            for (int sectionY = minSectionY; sectionY <= maxSectionY; ++sectionY)
            {
                long baseY = (long)sectionY << 4;
                long distanceY = distanceToRange(y, baseY, baseY + 15L);

                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; ++sectionZ)
                {
                    long baseZ = (long)sectionZ << 4;
                    long distanceZ = distanceToRange(z, baseZ, baseZ + 15L);

                    if (distanceX + distanceY + distanceZ < effectiveLevel)
                    {
                        addRemark(remarks, (int)baseX, (int)baseY, (int)baseZ);
                    }
                }
            }
        }
    }

    private static int horizontalSection(long coordinate)
    {
        long value = Math.floorDiv(coordinate, 16L);
        long minimum = (long)Integer.MIN_VALUE >> 4;
        long maximum = (long)Integer.MAX_VALUE >> 4;
        return (int)Math.max(minimum, Math.min(maximum, value));
    }

    private static int section(long coordinate)
    {
        return (int)Math.floorDiv(coordinate, 16L);
    }

    private static long distanceToRange(long value, long minimum, long maximum)
    {
        return value < minimum ? minimum - value : (value > maximum ? value - maximum : 0L);
    }

    /** True while capped remarks from earlier updates are still queued. */
    public boolean hasPending()
    {
        return !this.pending.isEmpty();
    }

    /** Adds one section origin unless it is already waiting for a rebuild. */
    private static void addRemark(List<int[]> remarks, int x, int y, int z)
    {
        for (int[] remark : remarks)
        {
            if (remark[0] == x && remark[1] == y && remark[2] == z)
            {
                return;
            }
        }

        remarks.add(new int[] {x, y, z});
    }
}
