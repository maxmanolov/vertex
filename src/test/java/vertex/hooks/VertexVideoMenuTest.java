package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VertexVideoMenuTest
{
    @Test
    public void cloudsAreOnOnlyWhenBothGatesAreOn()
    {
        assertTrue(VertexVideoMenu.effectiveClouds(true, true));
        assertFalse(VertexVideoMenu.effectiveClouds(true, false));
        assertFalse(VertexVideoMenu.effectiveClouds(false, true));
        assertFalse(VertexVideoMenu.effectiveClouds(false, false));
    }
}
