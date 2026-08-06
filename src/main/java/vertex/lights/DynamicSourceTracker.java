package vertex.lights;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure diffing for dynamic light sources: given the previous and current snapshots
 * (groups of four ints: x, y, z, level), yields the block positions whose chunk sections
 * need a re-mark - the old and new positions of any source that appeared, vanished or
 * moved. Capped so a burst of sources cannot flood the priority queue; the per-frame
 * consumption cap bounds the rebuild cost downstream anyway.
 */
public final class DynamicSourceTracker
{
    public static final int MAX_REMARKS = 8;

    private int[] previous = new int[0];
    private final List<int[]> pending = new ArrayList<int[]>();

    /**
     * Returns positions (groups of three ints) to re-mark, and retains the snapshot.
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
                remarks.add(new int[] {from[i], from[i + 1], from[i + 2]});
            }
        }
    }
}
