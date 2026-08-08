package vertex.benchmark.quick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QuickPresetTest
{
    @Test
    public void standardIsTheDefault()
    {
        assertEquals(QuickPreset.STANDARD, QuickPreset.parse(null));
        assertEquals(QuickPreset.STANDARD, QuickPreset.parse("standard"));
    }

    @Test
    public void fastIsExplicit()
    {
        assertEquals(QuickPreset.FAST, QuickPreset.parse("FAST"));
    }

    @Test
    public void rejectsUnknownPreset()
    {
        try
        {
            QuickPreset.parse("long");
            fail("Expected an invalid preset to fail.");
        }
        catch (IllegalArgumentException expected)
        {
            assertEquals("--preset must be fast or standard.", expected.getMessage());
        }
    }
}
