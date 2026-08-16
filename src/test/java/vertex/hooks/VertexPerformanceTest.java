package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Performance-page decision logic: the drain budget, its idle boost, and Fast Render. */
public final class VertexPerformanceTest
{
    @Test
    public void drainBudgetParsesOneToFiveAndDefaultsTheRest()
    {
        assertEquals(1, VertexPerformance.drainBudget("1", false));
        assertEquals(5, VertexPerformance.drainBudget("5", false));
        assertEquals("shipped default", 4, VertexPerformance.drainBudget("4", false));
        assertEquals("invalid resolves to the shipped default", 4, VertexPerformance.drainBudget("9", false));
        assertEquals(4, VertexPerformance.drainBudget("fast", false));
        assertEquals(4, VertexPerformance.drainBudget(null, false));
    }

    @Test
    public void stationaryBoostTriplesTheBudget()
    {
        assertEquals(3, VertexPerformance.drainBudget("1", true));
        assertEquals(12, VertexPerformance.drainBudget("4", true));
        assertEquals(15, VertexPerformance.drainBudget("5", true));
    }

    @Test
    public void fastRenderAliasMapsBatchingBackendsToOn()
    {
        assertTrue(VertexPerformance.fastRenderOn("arena"));
        assertTrue(VertexPerformance.fastRenderOn("vbo"));
        assertTrue("a missing value means the declared default (arena)",
            VertexPerformance.fastRenderOn(""));
        assertFalse(VertexPerformance.fastRenderOn("displaylist"));
        assertFalse(VertexPerformance.fastRenderOn("legacy"));

        assertEquals("displaylist", VertexPerformance.nextFastRender("arena"));
        assertEquals("displaylist", VertexPerformance.nextFastRender("vbo"));
        assertEquals("arena", VertexPerformance.nextFastRender("displaylist"));
        assertEquals("arena", VertexPerformance.nextFastRender("legacy"));
    }

    @Test
    public void menuCycleWalksOneToFiveAndLabels()
    {
        assertEquals("2", VideoMenuLayout.nextChunkUpdates("1"));
        assertEquals("5", VideoMenuLayout.nextChunkUpdates("4"));
        assertEquals("1", VideoMenuLayout.nextChunkUpdates("5"));
        assertEquals("a bad stored value restarts at the default's successor", "5",
            VideoMenuLayout.nextChunkUpdates("garbage"));
        assertEquals("Chunk Updates: 3", VideoMenuLayout.chunkUpdatesLabel("3"));
        assertEquals("Chunk Updates: 4", VideoMenuLayout.chunkUpdatesLabel("garbage"));
        assertEquals("Fast Render: §aON", VideoMenuLayout.fastRenderLabel(true));
        assertEquals("Fast Render: §cOFF", VideoMenuLayout.fastRenderLabel(false));
    }
}
