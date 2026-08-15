package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Time override and autosave interval decision logic. */
public final class VertexWorldVisualsTest
{
    @Test
    public void celestialAngleOnlyOverridesForDayAndNight()
    {
        assertEquals(0.37F, VertexWorldVisuals.celestialAngle(0.37F, "default"), 0.0F);
        assertEquals(0.0F, VertexWorldVisuals.celestialAngle(0.37F, "day"), 0.0F);
        assertEquals(0.5F, VertexWorldVisuals.celestialAngle(0.37F, "night"), 0.0F);
        assertEquals("garbage is vanilla", 0.37F, VertexWorldVisuals.celestialAngle(0.37F, "noon"), 0.0F);
        assertEquals(0.37F, VertexWorldVisuals.celestialAngle(0.37F, null), 0.0F);
    }

    @Test
    public void autosaveTicksParsesTheThreeStepsInSecondsTimesTwenty()
    {
        assertEquals(900, VertexWorldVisuals.autosaveTicks("45"));
        assertEquals(3600, VertexWorldVisuals.autosaveTicks("180"));
        assertEquals(36000, VertexWorldVisuals.autosaveTicks("1800"));
        assertEquals("invalid resolves to vanilla", 900, VertexWorldVisuals.autosaveTicks("0"));
        assertEquals(900, VertexWorldVisuals.autosaveTicks(null));
    }

    @Test
    public void menuCyclesWalkEveryValueAndWrap()
    {
        assertEquals("180", VideoMenuLayout.nextAutosave("45"));
        assertEquals("1800", VideoMenuLayout.nextAutosave("180"));
        assertEquals("45", VideoMenuLayout.nextAutosave("1800"));
        assertEquals("Autosave: 45s", VideoMenuLayout.autosaveLabel("45"));
        assertEquals("Autosave: 3min", VideoMenuLayout.autosaveLabel("180"));
        assertEquals("Autosave: 30min", VideoMenuLayout.autosaveLabel("1800"));

        assertEquals("day", VideoMenuLayout.nextTimeOverride("default"));
        assertEquals("night", VideoMenuLayout.nextTimeOverride("day"));
        assertEquals("default", VideoMenuLayout.nextTimeOverride("night"));
        assertEquals("Time: Default", VideoMenuLayout.timeOverrideLabel("default"));
        assertEquals("Time: Day", VideoMenuLayout.timeOverrideLabel("day"));
        assertEquals("Time: Night", VideoMenuLayout.timeOverrideLabel("night"));
    }
}
