package vertex.sky;

import java.util.Properties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkyLayerTest
{
    @Test
    public void fallsBackToTheSiblingTextureAndSaneDefaults()
    {
        SkyLayer layer = SkyLayer.parse(new Properties(), "mcpatcher/sky/world0/sky1.png");
        assertEquals("mcpatcher/sky/world0/sky1.png", layer.source);
        assertEquals("add", layer.blend);
        assertTrue(layer.rotate);
        assertEquals(1.0F, layer.speed, 0.0001F);
        assertEquals(1.0F, layer.timing.opacity(6000), 0.0001F);
        assertEquals(1.0F, layer.timing.opacity(18000), 0.0001F);
    }

    @Test
    public void honoursAnExplicitFadeWindowAcrossMidnight()
    {
        Properties props = new Properties();
        props.setProperty("source", "custom.png");
        props.setProperty("startFadeIn", "19:00");
        props.setProperty("endFadeIn", "20:00");
        props.setProperty("startFadeOut", "5:00");
        props.setProperty("endFadeOut", "6:00");
        props.setProperty("blend", "ALPHA");
        props.setProperty("rotate", "false");
        props.setProperty("speed", "2.5");
        props.setProperty("axis", "1 0 0");
        SkyLayer layer = SkyLayer.parse(props, "unused.png");
        assertEquals("custom.png", layer.source);
        assertEquals("alpha", layer.blend);
        assertTrue(!layer.rotate);
        assertEquals(2.5F, layer.speed, 0.0001F);
        assertEquals(1.0F, layer.axis[0], 0.0001F);
        assertEquals(1.0F, layer.timing.opacity(18000), 0.0001F);
        assertEquals(0.0F, layer.timing.opacity(6000), 0.0001F);
    }

    @Test
    public void derivesStartFadeOutFromTheDocumentedEndFadeOut()
    {
        Properties props = new Properties();
        props.setProperty("source", "custom.png");
        props.setProperty("startFadeIn", "19:00");
        props.setProperty("endFadeIn", "20:00");
        props.setProperty("endFadeOut", "6:00");
        SkyLayer layer = SkyLayer.parse(props, "unused.png");

        assertEquals(1.0F, layer.timing.opacity(18000), 0.0001F);
        assertEquals(0.5F, layer.timing.opacity(23500), 0.0001F);
        assertEquals(0.0F, layer.timing.opacity(0), 0.0001F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void partialFadeWindowsRefuse()
    {
        Properties props = new Properties();
        props.setProperty("startFadeIn", "19:00");
        SkyLayer.parse(props, "sky1.png");
    }

    @Test
    public void unusableLayersReturnNull()
    {
        assertNull(SkyLayer.parse(null, "sky1.png"));
        assertNull(SkyLayer.parse(new Properties(), null));
    }

    @Test
    public void malformedNumbersFallBackInsteadOfThrowing()
    {
        Properties props = new Properties();
        props.setProperty("source", "s.png");
        props.setProperty("speed", "fast");
        props.setProperty("axis", "1 2");
        SkyLayer layer = SkyLayer.parse(props, null);
        assertEquals(1.0F, layer.speed, 0.0001F);
        assertEquals(1.0F, layer.axis[2], 0.0001F);
    }
}
