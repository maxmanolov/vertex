package vertex.natural;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NaturalVariantsTest
{
    @Test
    public void fourRotationSpecsCollapseToTheExpressibleTwo()
    {
        assertEquals(2, NaturalVariants.variantCount(4, false));
        assertEquals(4, NaturalVariants.variantCount(4, true));
        assertEquals(2, NaturalVariants.variantCount(1, true));
        assertEquals(1, NaturalVariants.variantCount(1, false));
    }

    @Test
    public void variantsDecodeToDistinctMirrorCombinations()
    {
        assertFalse(NaturalVariants.flipU(0));
        assertFalse(NaturalVariants.flipV(0));
        assertTrue(NaturalVariants.flipU(1));
        assertTrue(NaturalVariants.flipV(1));
        assertTrue(NaturalVariants.flipU(2));
        assertFalse(NaturalVariants.flipV(2));
        assertFalse(NaturalVariants.flipU(3));
        assertTrue(NaturalVariants.flipV(3));
    }
}
