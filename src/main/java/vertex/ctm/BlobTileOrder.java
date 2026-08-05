package vertex.ctm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps a canonical blob connectivity class to a tile index in a pack's 47-tile sheet.
 *
 * The 47 classes are ordered deterministically by their canonical mask value, giving a
 * stable, reproducible index assignment. Whether that ordering matches the community
 * sheet layout is a **calibration seam**: it is mechanically correct (every class maps to
 * exactly one tile, no collisions, full coverage) but visually unconfirmed, and is
 * flagged as such in FEATURES.md. Correcting it, if needed, is a change to this one table
 * rather than to any logic - which is precisely why the mapping was isolated here instead
 * of being inlined into the dispatch.
 */
public final class BlobTileOrder
{
    private static final Map<Integer, Integer> INDEX = new HashMap<Integer, Integer>();
    public static final int TILE_COUNT;

    static
    {
        int[] classes = new int[256];
        int count = 0;

        for (int mask = 0; mask < 256; ++mask)
        {
            int canonical = BlobConnectivity.canonical(mask);
            boolean seen = false;

            for (int i = 0; i < count; ++i)
            {
                if (classes[i] == canonical)
                {
                    seen = true;
                    break;
                }
            }

            if (!seen)
            {
                classes[count++] = canonical;
            }
        }

        int[] ordered = Arrays.copyOf(classes, count);
        Arrays.sort(ordered);

        for (int i = 0; i < ordered.length; ++i)
        {
            INDEX.put(Integer.valueOf(ordered[i]), Integer.valueOf(i));
        }

        TILE_COUNT = count;
    }

    public static int tileIndex(int canonicalClass)
    {
        Integer index = INDEX.get(Integer.valueOf(canonicalClass));
        return index != null ? index.intValue() : 0;
    }

    private BlobTileOrder()
    {
    }
}
