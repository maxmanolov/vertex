package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VertexRendererModeTest
{
    @Test
    public void modeParsingTable()
    {
        assertEquals(VertexRenderer.DISPLAY_LIST, VertexRenderer.parseMode("displaylist"));
        assertEquals(VertexRenderer.DISPLAY_LIST, VertexRenderer.parseMode("dl"));
        assertEquals(VertexRenderer.DISPLAY_LIST, VertexRenderer.parseMode(" DisplayList "));
        assertEquals(VertexRenderer.VBO, VertexRenderer.parseMode("vbo"));
        assertEquals(VertexRenderer.VBO, VertexRenderer.parseMode(" VBO "));
        assertEquals(VertexRenderer.ARENA, VertexRenderer.parseMode("arena"));
        assertEquals(VertexRenderer.ARENA, VertexRenderer.parseMode(" Arena "));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("legacy"));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("LEGACY"));
        // Invalid VALUES never surprise-upgrade: unknown, empty and null stay legacy.
        // The arena default flows from the declared config default below, not from here.
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("fancy"));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode(""));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("  "));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode(null));
    }

    @Test
    public void declaredDefaultIsArena()
    {
        // Promotion gate closed 2026-08-08 (structural parity + zero-disable gauntlets
        // at 0.4.0, then real-session miles): a missing or blank renderer key resolves
        // to arena, while a corrupted value still drops to legacy above.
        assertEquals("arena", VertexConfig.value("renderer", "legacy"));
    }
}
