package vertex.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CloudCacheStateTest
{
    @Test
    public void reusesOnlyTheSameSceneWithinTheRebuildWindow()
    {
        CloudCacheState state = new CloudCacheState();
        Object owner = new Object();
        Object world = new Object();
        state.capture(owner, world, 3, 100, 0.25F, 1.0D, 2.0D, 3.0D);

        assertTrue(state.reusable(owner, world, 3, 100));
        assertTrue(state.reusable(owner, world, 3, 119));
        assertFalse(state.reusable(owner, world, 3, 120));
        assertFalse(state.reusable(owner, world, 3, 99));
        assertFalse(state.reusable(new Object(), world, 3, 100));
        assertFalse(state.reusable(owner, new Object(), 3, 100));
        assertFalse(state.reusable(owner, world, 2, 100));
    }

    @Test
    public void compensatesForCameraMotionAndCloudDrift()
    {
        CloudCacheState state = new CloudCacheState();
        Object owner = new Object();
        Object world = new Object();
        state.capture(owner, world, 0, 40, 0.25F, 10.0D, 20.0D, 30.0D);

        // 2.5 ticks add 0.075 blocks of eastward drift on top of camera motion.
        assertEquals(4.075D, state.deltaX(42, 0.75F, 14.0D), 0.0000001D);
        assertEquals(-2.0D, state.deltaY(18.0D), 0.0D);
        assertEquals(6.0D, state.deltaZ(36.0D), 0.0D);
    }

    @Test
    public void clearInvalidatesTheCapturedScene()
    {
        CloudCacheState state = new CloudCacheState();
        Object owner = new Object();
        Object world = new Object();
        state.capture(owner, world, 0, 1, 0.0F, 0.0D, 0.0D, 0.0D);
        state.clear();
        assertFalse(state.reusable(owner, world, 0, 1));
    }
}
