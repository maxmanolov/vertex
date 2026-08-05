package vertex.colors;

import java.util.Properties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ColorCoreTest
{
    @Test
    public void samplesTheVanillaTriangleWithClamping()
    {
        int[] pixels = new int[ColorMap.SIZE * ColorMap.SIZE];

        for (int y = 0; y < ColorMap.SIZE; ++y)
        {
            for (int x = 0; x < ColorMap.SIZE; ++x)
            {
                pixels[y << 8 | x] = y << 8 | x;
            }
        }

        ColorMap map = new ColorMap(pixels);
        assertEquals(0, map.sample(1.0F, 1.0F));
        assertEquals(255 << 8 | 255, map.sample(0.0F, 1.0F));
        assertEquals(0, map.sample(5.0F, 9.0F));
        assertEquals(255 << 8 | 255, map.sample(-1.0F, -1.0F));
    }

    @Test
    public void averageIsExactForUniformMaps()
    {
        int[] pixels = new int[ColorMap.SIZE * ColorMap.SIZE];
        java.util.Arrays.fill(pixels, 0xFF336699);
        assertEquals(0x336699, new ColorMap(pixels).average());
    }

    @Test
    public void parsesHexWithAndWithoutHashAndSkipsGarbage()
    {
        Properties props = new Properties();
        props.setProperty("lilypad", "208030");
        props.setProperty("fog.nether", "#330808");
        props.setProperty("bad", "not-a-color");
        ColorProperties colors = new ColorProperties(props);
        assertEquals(0x208030, colors.get("lilypad", 0));
        assertEquals(0x330808, colors.get("fog.nether", 0));
        assertFalse(colors.has("bad"));
        assertEquals(0xABCDEF, colors.get("missing", 0xABCDEF));
        assertTrue(colors.size() == 2);
    }

    @Test
    public void blendAveragesExactlyAndHandlesEmpty()
    {
        BiomeBlend blend = new BiomeBlend();
        blend.reset();
        blend.add(0x000000);
        blend.add(0x808080);
        assertEquals(0x404040, blend.average());
        blend.reset();
        assertEquals(0xFFFFFF, blend.average());
    }
}
