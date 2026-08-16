package vertex.hooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Frame surgery logic for Clear Water: alpha scaling, round-trips, sprite matching. */
public final class VertexClearWaterTest
{
    @Test
    public void alphaScalesAndRgbPassesThrough()
    {
        assertEquals(0x66123456, VertexClearWater.scaleAlpha(0xFF123456, 40));
        assertEquals(0x00ABCDEF, VertexClearWater.scaleAlpha(0x00ABCDEF, 40));
        assertEquals(0xFFFFFFFF, VertexClearWater.scaleAlpha(0xFFFFFFFF, 100));
        assertEquals(0x00000000, VertexClearWater.scaleAlpha(0xFF000000, 0));
        // integer truncation: 0x80 (128) * 40 / 100 = 51 = 0x33
        assertEquals(0x33445566, VertexClearWater.scaleAlpha(0x80445566, 40));
    }

    @Test
    public void blendFrameScalesEveryMipLevelAndRestoresBitExact()
    {
        int[][] original = {
            {0xFF112233, 0xC0445566, 0x00778899},
            {0xFFAABBCC},
        };
        int[][] live = {
            original[0].clone(),
            original[1].clone(),
        };

        VertexClearWater.blendFrame(live, original, true, 40);
        assertEquals(0x66112233, live[0][0]);
        assertEquals(0x4C445566, live[0][1]); // 0xC0 (192) * 40 / 100 = 76 = 0x4C
        assertEquals(0x00778899, live[0][2]);
        assertEquals(0x66AABBCC, live[1][0]);

        VertexClearWater.blendFrame(live, original, false, 40);
        assertArrayEquals(original[0], live[0]);
        assertArrayEquals(original[1], live[1]);
    }

    @Test
    public void blendFrameSkipsNullLevelsAndLengthMismatches()
    {
        int[][] original = {{0xFF000000}, null};
        int[][] live = {{0xFF000000}, null};

        VertexClearWater.blendFrame(live, original, true, 40);
        assertEquals(0x66000000, live[0][0]);
        assertNull(live[1]);

        // A live frame shorter than the original must not throw.
        int[][] shortLive = {{0xFF000000}};
        VertexClearWater.blendFrame(shortLive, original, true, 40);
        assertEquals(0x66000000, shortLive[0][0]);
    }

    @Test
    public void onlyTheTwoWaterSpritesMatch()
    {
        assertTrue(VertexClearWater.isWaterSprite("water_still"));
        assertTrue(VertexClearWater.isWaterSprite("water_flow"));
        assertFalse(VertexClearWater.isWaterSprite("lava_still"));
        assertFalse(VertexClearWater.isWaterSprite("lava_flow"));
        assertFalse(VertexClearWater.isWaterSprite("portal"));
        assertFalse(VertexClearWater.isWaterSprite(null));
    }

    @Test
    public void copyFramesIsADeepCopy()
    {
        int[][] frame = {{1, 2, 3}, {4}};
        List<Object> frames = new ArrayList<Object>(Arrays.<Object>asList((Object)frame));

        int[][][] copy = VertexClearWater.copyFrames(frames);
        frame[0][0] = 99;
        frame[1][0] = 99;

        assertEquals(1, copy[0][0][0]);
        assertEquals(4, copy[0][1][0]);
    }

    @Test
    public void theClearAlphaConstantStaysAtFortyPercent()
    {
        // FEATURES.md documents the 40% figure; a silent change here should fail loudly.
        assertEquals(40, VertexClearWater.ALPHA_PERCENT);
    }
}
