package vertex.benchmark.game;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ScenarioWarmupGateTest
{
    @Test
    public void publishesOnceAfterEachMonotonicWarmup()
    {
        ScenarioWarmupGate gate = new ScenarioWarmupGate(5000L);
        assertFalse(gate.shouldPublish(100L));

        gate.start(100L);
        assertFalse(gate.shouldPublish(5099L));
        assertTrue(gate.shouldPublish(5100L));
        assertFalse(gate.shouldPublish(20000L));

        gate.start(30000L);
        assertFalse(gate.shouldPublish(34999L));
        assertTrue(gate.shouldPublish(35000L));
    }
}
