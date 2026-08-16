package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Custom Fonts path mapping: vanilla font locations to the MCPatcher convention. */
public final class VertexFontsTest
{
    @Test
    public void fontTexturesMapToTheMcpatcherPath()
    {
        assertEquals("mcpatcher/font/ascii.png",
            VertexFonts.overridePath("minecraft:textures/font/ascii.png"));
        assertEquals("mcpatcher/font/ascii_sga.png",
            VertexFonts.overridePath("minecraft:textures/font/ascii_sga.png"));
        // A domainless string still maps on its path part.
        assertEquals("mcpatcher/font/ascii.png",
            VertexFonts.overridePath("textures/font/ascii.png"));
    }

    @Test
    public void nonFontLocationsAreLeftAlone()
    {
        assertNull(VertexFonts.overridePath("minecraft:textures/blocks/stone.png"));
        assertNull(VertexFonts.overridePath("minecraft:textures/font/"));
        assertNull(VertexFonts.overridePath("minecraft:"));
        assertNull(VertexFonts.overridePath(""));
        assertNull(VertexFonts.overridePath(null));
    }
}
