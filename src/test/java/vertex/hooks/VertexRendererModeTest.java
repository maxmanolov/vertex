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
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("legacy"));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("LEGACY"));
        // Never surprise-enable: unknown, empty and null all stay legacy.
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("fancy"));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode(""));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode("  "));
        assertEquals(VertexRenderer.LEGACY, VertexRenderer.parseMode(null));
    }
}
