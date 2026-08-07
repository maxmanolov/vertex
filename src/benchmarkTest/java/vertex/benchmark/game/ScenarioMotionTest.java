package vertex.benchmark.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class ScenarioMotionTest
{
    @Test
    public void chunkTravelUsesElapsedTime()
    {
        assertEquals(10.0D, ScenarioMotion.chunkX(10.0D, 0L), 0.0001D);
        assertEquals(34.0D, ScenarioMotion.chunkX(10.0D, 1000000000L), 0.0001D);
        assertEquals(58.0D, ScenarioMotion.chunkX(10.0D, 2000000000L), 0.0001D);
    }

    @Test
    public void entityPositionsAreRepeatableForTheSameTime()
    {
        double first = ScenarioMotion.entityX(5.0D, 17, 160, 3000000000L);
        double second = ScenarioMotion.entityX(5.0D, 17, 160, 3000000000L);
        double later = ScenarioMotion.entityX(5.0D, 17, 160, 3500000000L);

        assertEquals(first, second, 0.0D);
        assertNotEquals(first, later, 0.0001D);
    }

    @Test
    public void negativeElapsedTimeUsesTheInitialPosition()
    {
        assertEquals(ScenarioMotion.entityZ(0.0D, 3, 16, 0L),
            ScenarioMotion.entityZ(0.0D, 3, 16, -1L), 0.0D);
    }
}
