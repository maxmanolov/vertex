package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Sample-count parsing and the menu cycle for the restart-gated MSAA setting. */
public final class VertexAntialiasTest
{
    @Test
    public void samplesParseTheThreeLevelsAndDefaultEverythingElse()
    {
        assertEquals(0, VertexAntialias.samples("0"));
        assertEquals(2, VertexAntialias.samples("2"));
        assertEquals(4, VertexAntialias.samples(" 4 "));
        assertEquals(8, VertexAntialias.samples("8"));
        assertEquals("invalid resolves to off", 0, VertexAntialias.samples("16"));
        assertEquals(0, VertexAntialias.samples("on"));
        assertEquals(0, VertexAntialias.samples(null));
    }

    @Test
    public void menuCycleWalksTheLevelsAndWraps()
    {
        assertEquals("2", VideoMenuLayout.nextAntialias("0"));
        assertEquals("4", VideoMenuLayout.nextAntialias("2"));
        assertEquals("8", VideoMenuLayout.nextAntialias("4"));
        assertEquals("0", VideoMenuLayout.nextAntialias("8"));
        assertEquals("a bad stored value restarts the cycle", "0",
            VideoMenuLayout.nextAntialias("garbage"));

        assertEquals("Antialiasing: §cOFF", VideoMenuLayout.antialiasLabel("0"));
        assertEquals("Antialiasing: 4x", VideoMenuLayout.antialiasLabel("4"));
        assertEquals("Antialiasing: §cOFF", VideoMenuLayout.antialiasLabel(null));
    }
}
