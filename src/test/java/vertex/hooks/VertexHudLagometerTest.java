package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The lagometer's color thresholds: one vsync green, two yellow, beyond red. */
public final class VertexHudLagometerTest
{
    @Test
    public void columnsColorByVsyncBudget()
    {
        assertEquals(0x9000FF00, VertexHud.lagColor(0.8F));
        assertEquals(0x9000FF00, VertexHud.lagColor(16.6F));
        assertEquals(0x90FFFF00, VertexHud.lagColor(16.8F));
        assertEquals(0x90FFFF00, VertexHud.lagColor(33.3F));
        assertEquals(0x90FF0000, VertexHud.lagColor(33.5F));
        assertEquals(0x90FF0000, VertexHud.lagColor(500.0F));
    }
}
