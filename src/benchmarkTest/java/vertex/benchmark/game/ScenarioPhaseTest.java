package vertex.benchmark.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ScenarioPhaseTest
{
    @Test
    public void parsesStableIdsWithoutCaseSensitivity()
    {
        assertEquals(ScenarioPhase.STATIC, ScenarioPhase.fromId("static"));
        assertEquals(ScenarioPhase.CHUNKS, ScenarioPhase.fromId(" CHUNKS "));
        assertEquals(ScenarioPhase.BLOCKS, ScenarioPhase.fromId("blocks"));
        assertEquals(ScenarioPhase.ENTITIES, ScenarioPhase.fromId("entities"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownIds()
    {
        ScenarioPhase.fromId("combat");
    }
}
