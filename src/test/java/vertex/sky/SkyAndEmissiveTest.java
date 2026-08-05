package vertex.sky;

import java.util.Properties;
import org.junit.Test;
import vertex.emissive.EmissiveSuffix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkyAndEmissiveTest
{
    @Test
    public void clockAnchorsMatchTheMinecraftDay()
    {
        assertEquals(0, SkyLayerTiming.parseClock("6:00"));
        assertEquals(12000, SkyLayerTiming.parseClock("18:00"));
        assertEquals(18000, SkyLayerTiming.parseClock("0:00"));
        assertEquals(500, SkyLayerTiming.parseClock("6:30"));
    }

    @Test
    public void nightLayerFadesAcrossMidnightCorrectly()
    {
        // Fade in 19:00-20:00, out 05:00-06:00: full at midnight, off at noon.
        SkyLayerTiming timing = SkyLayerTiming.parse("19:00", "20:00", "5:00", "6:00");
        assertEquals(1.0F, timing.opacity(18000), 0.0001F);
        assertEquals(0.0F, timing.opacity(6000), 0.0001F);
        assertEquals(0.5F, timing.opacity(13500), 0.0001F);
        assertEquals(0.5F, timing.opacity(23500), 0.0001F);
        assertEquals(1.0F, timing.opacity(14000 + SkyLayerTiming.DAY_TICKS * 3L), 0.0001F);
    }

    @Test
    public void omittedEndFadeOutMirrorsFadeInLength()
    {
        SkyLayerTiming timing = SkyLayerTiming.parse("19:00", "21:00", "4:00", null);
        assertEquals(0.5F, timing.opacity(23000), 0.0001F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badClocksRefuse()
    {
        SkyLayerTiming.parseClock("25:99");
    }

    @Test
    public void emissiveSuffixDetectsAndMaps()
    {
        EmissiveSuffix defaults = new EmissiveSuffix(null);
        assertTrue(defaults.isEmissive("glowstone_e"));
        assertFalse(defaults.isEmissive("_e"));
        assertFalse(defaults.isEmissive("stone"));
        assertEquals("glowstone", defaults.baseOf("glowstone_e"));
        assertEquals("stone_e", defaults.emissiveOf("stone"));
        Properties props = new Properties();
        props.setProperty("suffix.emissive", "_glow");
        EmissiveSuffix custom = new EmissiveSuffix(props);
        assertTrue(custom.isEmissive("lamp_glow"));
        assertFalse(custom.isEmissive("lamp_e"));
    }
}
