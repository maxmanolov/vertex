package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ToggleSprintStateTest
{
    @Test
    public void startsArmedAndTapsPauseAndResume()
    {
        ToggleSprintState state = new ToggleSprintState();
        assertTrue("enabling the feature is choosing auto-sprint", state.latched());

        // Tap: one rising edge pauses the latch, holding produces no further flips.
        assertTrue(state.sample(true));
        assertFalse(state.latched());
        assertFalse("held key must not re-flip", state.sample(true));
        assertFalse(state.latched());
        assertFalse("release is not an edge", state.sample(false));

        // Second tap resumes.
        assertTrue(state.sample(true));
        assertTrue(state.latched());
    }

    @Test
    public void quickTapsFlipOncePerPress()
    {
        ToggleSprintState state = new ToggleSprintState();

        for (int press = 0; press < 5; ++press)
        {
            state.sample(true);
            state.sample(false);
        }

        // Five presses from an armed start: odd count of flips lands on paused.
        assertFalse(state.latched());
    }

    @Test
    public void resetRearmsAndClearsEdgeMemory()
    {
        ToggleSprintState state = new ToggleSprintState();
        state.sample(true);
        assertFalse(state.latched());
        state.reset();
        assertTrue(state.latched());

        // The first sample after reset is a fresh edge by design: a key physically held
        // across a world join reads as a deliberate tap.
        assertTrue(state.sample(true));
        assertFalse(state.latched());
    }
}
