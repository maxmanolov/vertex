package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FreelookStateTest
{
    @Test
    public void orbitMirrorsVanillaMouseArithmetic()
    {
        FreelookState state = new FreelookState();
        assertFalse(state.active());

        // Inactive deltas must not seed anything.
        state.consume(500.0F, 500.0F);
        state.activate(100.0F, 20.0F);
        assertTrue(state.active());
        assertEquals(100.0F, state.yaw(), 0.0F);
        assertEquals(20.0F, state.pitch(), 0.0F);

        state.consume(40.0F, -20.0F);
        assertEquals((float)(100.0F + 40.0D * 0.15D), state.yaw(), 0.0F);
        assertEquals((float)(20.0F + 20.0D * 0.15D), state.pitch(), 0.0F);
    }

    @Test
    public void pitchClampsAtNinetyAndYawIsUnbounded()
    {
        FreelookState state = new FreelookState();
        state.activate(0.0F, 0.0F);

        state.consume(0.0F, -100000.0F);
        assertEquals(90.0F, state.pitch(), 0.0F);
        state.consume(0.0F, 200000.0F);
        assertEquals(-90.0F, state.pitch(), 0.0F);

        // A full-circle orbit is the point: yaw accumulates past 360 without wrapping.
        state.consume(100000.0F, 0.0F);
        assertEquals(15000.0F, state.yaw(), 0.001F);
    }

    @Test
    public void reactivationReseedsFromTheHandedOrientation()
    {
        FreelookState state = new FreelookState();
        state.activate(10.0F, 5.0F);
        state.consume(100.0F, 100.0F);
        state.deactivate();
        assertFalse(state.active());

        state.consume(100.0F, 100.0F);
        state.activate(-30.0F, 45.0F);
        assertEquals(-30.0F, state.yaw(), 0.0F);
        assertEquals(45.0F, state.pitch(), 0.0F);
    }
}
