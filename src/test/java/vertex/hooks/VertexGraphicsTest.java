package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure decision logic of the fancy/fast decoupling: the tri-state override, the smooth
 * lighting level parser and scaler, and the menu cycles. Invalid stored values must
 * resolve to vanilla behavior.
 */
public final class VertexGraphicsTest
{
    @Test
    public void triStateFollowsVanillaByDefaultAndForcesOnDemand()
    {
        assertTrue(VertexGraphics.overrideFancy("default", true));
        assertFalse(VertexGraphics.overrideFancy("default", false));
        assertFalse(VertexGraphics.overrideFancy("fast", true));
        assertTrue(VertexGraphics.overrideFancy("fancy", false));
        assertTrue("garbage follows vanilla", VertexGraphics.overrideFancy("garbage", true));
        assertFalse(VertexGraphics.overrideFancy(null, false));
    }

    @Test
    public void aoLevelParsesTheThreeStepsAndScalesTheCornerDarkening()
    {
        assertEquals(0, VertexGraphics.aoLevelPercent("0"));
        assertEquals(50, VertexGraphics.aoLevelPercent(" 50"));
        assertEquals(100, VertexGraphics.aoLevelPercent("100"));
        assertEquals("invalid resolves to vanilla", 100, VertexGraphics.aoLevelPercent("75"));
        assertEquals(100, VertexGraphics.aoLevelPercent(null));

        // Vanilla corner value for a normal cube is 0.2: full level keeps it, zero
        // removes the darkening entirely, half lands between.
        assertEquals(0.2F, VertexGraphics.scaleAoValue(0.2F, 100), 0.0001F);
        assertEquals(1.0F, VertexGraphics.scaleAoValue(0.2F, 0), 0.0001F);
        assertEquals(0.6F, VertexGraphics.scaleAoValue(0.2F, 50), 0.0001F);
        // Blocks that never darken (value 1) stay untouched at every level.
        assertEquals(1.0F, VertexGraphics.scaleAoValue(1.0F, 100), 0.0001F);
        assertEquals(1.0F, VertexGraphics.scaleAoValue(1.0F, 0), 0.0001F);
    }

    @Test
    public void menuCyclesWalkEveryValueAndWrap()
    {
        assertEquals("fast", VideoMenuLayout.nextTriState("default"));
        assertEquals("fancy", VideoMenuLayout.nextTriState("fast"));
        assertEquals("default", VideoMenuLayout.nextTriState("fancy"));
        assertEquals("bad values cycle back to default", "default",
            VideoMenuLayout.nextTriState("garbage"));

        assertEquals(0, VideoMenuLayout.nextAoLevel(100));
        assertEquals(50, VideoMenuLayout.nextAoLevel(0));
        assertEquals(100, VideoMenuLayout.nextAoLevel(50));

        assertEquals("Trees: Default", VideoMenuLayout.triStateLabel("Trees", "default"));
        assertEquals("Trees: Fast", VideoMenuLayout.triStateLabel("Trees", "fast"));
        assertEquals("Dropped Items: Fancy", VideoMenuLayout.triStateLabel("Dropped Items", "fancy"));
        assertEquals("Smooth Lighting Level: §cOFF", VideoMenuLayout.aoLevelLabel(0));
        assertEquals("Smooth Lighting Level: 50%", VideoMenuLayout.aoLevelLabel(50));
        assertEquals("Smooth Lighting Level: 100%", VideoMenuLayout.aoLevelLabel(100));
    }
}
