package vertex.hooks;

import org.junit.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.Assert.assertEquals;

/** Mipmap filter selection: only mipmapped filters ever change, and only between the two. */
public final class VertexQualityTest
{
    @Test
    public void mipmappedFiltersSwapBetweenNearestAndLinear()
    {
        assertEquals(GL11.GL_LINEAR_MIPMAP_LINEAR,
            VertexQuality.mipmapFilter("linear", GL11.GL_NEAREST_MIPMAP_LINEAR));
        assertEquals(GL11.GL_NEAREST_MIPMAP_LINEAR,
            VertexQuality.mipmapFilter("nearest", GL11.GL_LINEAR_MIPMAP_LINEAR));
        assertEquals("invalid mode is vanilla (nearest)", GL11.GL_NEAREST_MIPMAP_LINEAR,
            VertexQuality.mipmapFilter("garbage", GL11.GL_LINEAR_MIPMAP_LINEAR));
        assertEquals(GL11.GL_NEAREST_MIPMAP_LINEAR,
            VertexQuality.mipmapFilter(null, GL11.GL_NEAREST_MIPMAP_LINEAR));
    }

    @Test
    public void nonMipmappedFiltersNeverChange()
    {
        // An atlas built with zero mipmap levels samples GL_NEAREST/GL_LINEAR; forcing
        // a mipmap filter there would break sampling entirely.
        assertEquals(GL11.GL_NEAREST, VertexQuality.mipmapFilter("linear", GL11.GL_NEAREST));
        assertEquals(GL11.GL_LINEAR, VertexQuality.mipmapFilter("linear", GL11.GL_LINEAR));
    }

    @Test
    public void menuCycleAndLabels()
    {
        assertEquals("linear", VideoMenuLayout.nextMipmapType("nearest"));
        assertEquals("nearest", VideoMenuLayout.nextMipmapType("linear"));
        assertEquals("linear", VideoMenuLayout.nextMipmapType("garbage"));
        assertEquals("Mipmap Type: Nearest", VideoMenuLayout.mipmapTypeLabel("nearest"));
        assertEquals("Mipmap Type: Linear", VideoMenuLayout.mipmapTypeLabel("linear"));
        assertEquals("Mipmap Type: Nearest", VideoMenuLayout.mipmapTypeLabel(null));
    }
}
