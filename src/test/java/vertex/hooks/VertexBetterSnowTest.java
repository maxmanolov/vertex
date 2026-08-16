package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The better-snow render-type set: blocks that sit on the ground without covering it
 * qualify; full cubes, fluids and every special renderer stay out so the prepend can
 * never double-draw ground cover.
 */
public final class VertexBetterSnowTest
{
    @Test
    public void groundSittingTypesQualify()
    {
        assertTrue("crossed plants", VertexBetterSnow.snowyRenderType(1));
        assertTrue("torches", VertexBetterSnow.snowyRenderType(2));
        assertTrue("ladders", VertexBetterSnow.snowyRenderType(8));
        assertTrue("fences", VertexBetterSnow.snowyRenderType(11));
        assertTrue("panes", VertexBetterSnow.snowyRenderType(18));
        assertTrue("walls", VertexBetterSnow.snowyRenderType(32));
        assertTrue("double plants", VertexBetterSnow.snowyRenderType(40));
    }

    @Test
    public void coveringAndSpecialTypesStayOut()
    {
        assertFalse("standard cubes", VertexBetterSnow.snowyRenderType(0));
        assertFalse("fluids", VertexBetterSnow.snowyRenderType(4));
        assertFalse("crops", VertexBetterSnow.snowyRenderType(6));
        assertFalse("doors", VertexBetterSnow.snowyRenderType(7));
        assertFalse("stairs", VertexBetterSnow.snowyRenderType(10));
        assertFalse("chests", VertexBetterSnow.snowyRenderType(22));
        assertFalse("unknown renderer ids", VertexBetterSnow.snowyRenderType(-1));
        assertFalse(VertexBetterSnow.snowyRenderType(66));
    }
}
