package vertex.hooks;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VertexDynamicLightsTest
{
    @After
    public void clear()
    {
        VertexDynamicLights.publish(new int[0]);
    }

    @Test
    public void noSourcesIsIdentity()
    {
        assertEquals(0xD00A0, VertexDynamicLights.adjust(0xD00A0, 10, 64, 10));
    }

    @Test
    public void raisesOnlyTheBlockComponentWithLinearFalloff()
    {
        VertexDynamicLights.publish(new int[] {10, 64, 10, 14});
        // At distance 3 (manhattan), level 11 -> tex 176; sky half untouched.
        int adjusted = VertexDynamicLights.adjust(0xD00000, 11, 65, 11);
        assertEquals(0xD0000 << 4 | 176, adjusted);
        // Existing stronger block light wins.
        assertEquals(0xD000F0, VertexDynamicLights.adjust(0xD000F0, 11, 65, 11));
    }

    @Test
    public void outOfRangeSourcesDoNothing()
    {
        VertexDynamicLights.publish(new int[] {0, 64, 0, 14});
        assertEquals(0xA0, VertexDynamicLights.adjust(0xA0, 20, 64, 0));
    }
}
