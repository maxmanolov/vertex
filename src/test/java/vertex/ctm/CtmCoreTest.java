package vertex.ctm;

import java.util.List;
import java.util.Properties;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CtmCoreTest
{
    @Test
    public void blobMaskingYieldsExactlyThe47ClassicClasses()
    {
        assertEquals(47, BlobConnectivity.classCount());
    }

    @Test
    public void cornersAreIgnoredUnlessBothAdjacentEdgesConnect()
    {
        // NE corner with only N connected: corner is irrelevant, class equals plain N.
        assertEquals(BlobConnectivity.N,
            BlobConnectivity.canonical(BlobConnectivity.N | BlobConnectivity.NE));
        // NE corner with both N and E: corner is significant.
        int withCorner = BlobConnectivity.canonical(BlobConnectivity.N | BlobConnectivity.E | BlobConnectivity.NE);
        int withoutCorner = BlobConnectivity.canonical(BlobConnectivity.N | BlobConnectivity.E);
        assertTrue(withCorner != withoutCorner);
    }

    @Test
    public void fullSurroundAndIslandAreDistinctSingletonClasses()
    {
        assertEquals(255, BlobConnectivity.canonical(255));
        assertEquals(0, BlobConnectivity.canonical(0));
    }

    @Test
    public void parsesRangesFacesAndDefaults()
    {
        Properties props = new Properties();
        props.setProperty("method", "ctm");
        props.setProperty("matchBlocks", "20 95");
        props.setProperty("tiles", "0-4 11 13-15");
        props.setProperty("faces", "sides");
        CtmProperties parsed = new CtmProperties(props);
        assertEquals(CtmProperties.Method.CTM, parsed.method);
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 11, 13, 14, 15}, parsed.tiles);
        assertEquals(CtmProperties.FACE_NORTH | CtmProperties.FACE_SOUTH | CtmProperties.FACE_WEST | CtmProperties.FACE_EAST, parsed.facesMask);
        assertEquals(CtmProperties.Connect.BLOCK, parsed.connect);
    }

    @Test
    public void tileMatchesDefaultToTileConnectAndAliasesResolve()
    {
        Properties props = new Properties();
        props.setProperty("method", "glass");
        props.setProperty("matchTiles", "glass");
        CtmProperties parsed = new CtmProperties(props);
        assertEquals(CtmProperties.Method.CTM, parsed.method);
        assertEquals(CtmProperties.Connect.TILE, parsed.connect);
        assertEquals(CtmProperties.FACE_ALL, parsed.facesMask);
    }

    @Test
    public void matchingCombinesBlockAndTileRulesInPackOrderWithoutDuplicates()
    {
        CtmProperties blockOnly = rule("matchBlocks", "20", "0");
        CtmProperties tileOnly = rule("matchTiles", "glass", "1");
        Properties bothProps = new Properties();
        bothProps.setProperty("matchBlocks", "minecraft:glass");
        bothProps.setProperty("matchTiles", "glass");
        bothProps.setProperty("tiles", "2");
        CtmProperties both = new CtmProperties(bothProps);
        CtmRuleSet rules = new CtmRuleSet();
        rules.add(blockOnly);
        rules.add(tileOnly);
        rules.add(both);

        List<CtmProperties> matching = rules.matching("glass", "20", "glass");
        assertEquals(3, matching.size());
        assertSame(blockOnly, matching.get(0));
        assertSame(tileOnly, matching.get(1));
        assertSame("a rule that matches two keys must appear once", both, matching.get(2));
        assertSame("the default namespace must normalize in the block index", both,
            rules.forBlock("glass").get(0));
    }

    private static CtmProperties rule(String key, String value, String tile)
    {
        Properties props = new Properties();
        props.setProperty(key, value);
        props.setProperty("tiles", tile);
        return new CtmProperties(props);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownMethodsRefuseLoudly()
    {
        Properties props = new Properties();
        props.setProperty("method", "spaghetti");
        new CtmProperties(props);
    }
}
