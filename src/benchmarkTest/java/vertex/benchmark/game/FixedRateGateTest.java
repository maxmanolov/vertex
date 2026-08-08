package vertex.benchmark.game;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FixedRateGateTest
{
    @Test
    public void allowsOnePollForEachTimeInterval()
    {
        FixedRateGate gate = new FixedRateGate(50L);
        gate.reset(1000L);

        assertTrue(gate.poll(1000L));
        assertFalse(gate.poll(1049L));
        assertTrue(gate.poll(1050L));
        assertFalse(gate.poll(1099L));
        assertTrue(gate.poll(1100L));
    }

    @Test
    public void skipsMissedIntervalsWithoutCatchUpBursts()
    {
        FixedRateGate gate = new FixedRateGate(50L);
        gate.reset(1000L);

        assertTrue(gate.poll(1000L));
        assertTrue(gate.poll(1300L));
        assertFalse(gate.poll(1300L));
    }

    @Test
    public void resetStartsASeparatePhaseSchedule()
    {
        FixedRateGate gate = new FixedRateGate(50L);
        gate.reset(1000L);
        assertTrue(gate.poll(1000L));

        gate.reset(2000L);
        assertTrue(gate.poll(2000L));
    }
}
