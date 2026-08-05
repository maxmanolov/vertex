package vertex.ctm;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlobTileOrderTest
{
    @Test
    public void everyClassMapsToADistinctTileWithFullCoverage()
    {
        Set<Integer> indices = new HashSet<Integer>();

        for (int mask = 0; mask < 256; ++mask)
        {
            int index = BlobTileOrder.tileIndex(BlobConnectivity.canonical(mask));
            assertTrue("index out of range: " + index, index >= 0 && index < BlobTileOrder.TILE_COUNT);
            indices.add(Integer.valueOf(index));
        }

        assertEquals(47, BlobTileOrder.TILE_COUNT);
        assertEquals("all 47 tiles must be reachable", 47, indices.size());
    }

    @Test
    public void mappingIsStableAcrossCalls()
    {
        int first = BlobTileOrder.tileIndex(BlobConnectivity.canonical(255));
        assertEquals(first, BlobTileOrder.tileIndex(BlobConnectivity.canonical(255)));
    }
}
