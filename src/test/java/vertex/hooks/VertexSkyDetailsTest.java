package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure decision logic for the sky/fog detail settings: which textures arm the sun/moon
 * gate, when the star list drops, the cloud-lift and fog-start parsers (invalid input
 * must resolve to vanilla behavior, never to a surprise value), and the menu cycles.
 */
public final class VertexSkyDetailsTest
{
    @Test
    public void onlySunAndMoonTexturesArmTheCelestialGate()
    {
        assertTrue(VertexSkyDetails.celestialTexture("textures/environment/sun.png"));
        assertTrue(VertexSkyDetails.celestialTexture("textures/environment/moon_phases.png"));
        assertFalse(VertexSkyDetails.celestialTexture("textures/environment/end_sky.png"));
        assertFalse(VertexSkyDetails.celestialTexture("textures/environment/clouds.png"));
        assertFalse(VertexSkyDetails.celestialTexture(null));
    }

    @Test
    public void onlyTheStarListSuppressesAndOnlyWhenDisabled()
    {
        assertTrue(VertexSkyDetails.suppressCallList(7, 7, false));
        assertFalse("stars enabled draws", VertexSkyDetails.suppressCallList(7, 7, true));
        assertFalse("sky dome list always draws", VertexSkyDetails.suppressCallList(8, 7, false));
    }

    @Test
    public void cloudLiftParsesTheFiveStepsAndNothingElse()
    {
        assertEquals(0, VertexSkyDetails.cloudLiftPercent("0"));
        assertEquals(25, VertexSkyDetails.cloudLiftPercent("25"));
        assertEquals(50, VertexSkyDetails.cloudLiftPercent(" 50 "));
        assertEquals(75, VertexSkyDetails.cloudLiftPercent("75"));
        assertEquals(100, VertexSkyDetails.cloudLiftPercent("100"));
        assertEquals("invalid resolves to vanilla", 0, VertexSkyDetails.cloudLiftPercent("60"));
        assertEquals(0, VertexSkyDetails.cloudLiftPercent("clouds"));
        assertEquals(0, VertexSkyDetails.cloudLiftPercent(null));
        assertEquals(148.0F, VertexSkyDetails.cloudLiftBlocks(100), 0.001F);
    }

    @Test
    public void depthFogFactorPassesThroughWhenEnabledAndSaturatesWhenOff()
    {
        assertEquals(0.03125D, VertexSkyDetails.adjustVoidFogFactor(0.03125D, true), 0.0D);
        // 16x: the darkening product (eyeY * factor) stays >= 1 from y=1 up, so the
        // fog color never scales down while blindness keeps its own path.
        assertEquals(16.0D, VertexSkyDetails.adjustVoidFogFactor(0.03125D, false), 0.0D);
    }

    @Test
    public void fogStartParsesTheFourFractionsAndDefaultsEverythingElse()
    {
        assertEquals(0.2F, VertexHooks.fogStartFraction("0.2"), 0.001F);
        assertEquals(0.4F, VertexHooks.fogStartFraction("0.4"), 0.001F);
        assertEquals(0.6F, VertexHooks.fogStartFraction(" 0.6"), 0.001F);
        assertEquals(0.8F, VertexHooks.fogStartFraction("0.8"), 0.001F);
        assertEquals(-1.0F, VertexHooks.fogStartFraction("default"), 0.001F);
        assertEquals(-1.0F, VertexHooks.fogStartFraction("0.5"), 0.001F);
        assertEquals(-1.0F, VertexHooks.fogStartFraction(null), 0.001F);
    }

    @Test
    public void menuCyclesWalkEveryValueAndWrap()
    {
        assertEquals("0.2", VideoMenuLayout.nextFogStart("default"));
        assertEquals("0.4", VideoMenuLayout.nextFogStart("0.2"));
        assertEquals("0.6", VideoMenuLayout.nextFogStart("0.4"));
        assertEquals("0.8", VideoMenuLayout.nextFogStart("0.6"));
        assertEquals("default", VideoMenuLayout.nextFogStart("0.8"));
        assertEquals("a bad stored value cycles back to default", "default",
            VideoMenuLayout.nextFogStart("garbage"));

        assertEquals(25, VideoMenuLayout.nextCloudHeight(0));
        assertEquals(50, VideoMenuLayout.nextCloudHeight(25));
        assertEquals(75, VideoMenuLayout.nextCloudHeight(50));
        assertEquals(100, VideoMenuLayout.nextCloudHeight(75));
        assertEquals(0, VideoMenuLayout.nextCloudHeight(100));

        assertEquals("Fog Start: Default", VideoMenuLayout.fogStartLabel("default"));
        assertEquals("Fog Start: 0.4", VideoMenuLayout.fogStartLabel("0.4"));
        assertEquals("Cloud Height: §cOFF", VideoMenuLayout.cloudHeightLabel(0));
        assertEquals("Cloud Height: 75%", VideoMenuLayout.cloudHeightLabel(75));
    }
}
