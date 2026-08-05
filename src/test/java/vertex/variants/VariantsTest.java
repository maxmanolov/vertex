package vertex.variants;

import java.util.Properties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VariantsTest
{
    @Test
    public void hashingIsDeterministicAndSaltSeparated()
    {
        assertEquals(DeterministicVariants.hash(10, 64, -3, 7), DeterministicVariants.hash(10, 64, -3, 7));
        assertTrue(DeterministicVariants.hash(10, 64, -3, 7) != DeterministicVariants.hash(10, 64, -3, 8));
    }

    @Test
    public void allVariantsAreReachableAndRoughlyUniform()
    {
        int[] buckets = new int[8];

        for (int x = 0; x < 64; ++x)
        {
            for (int z = 0; z < 64; ++z)
            {
                ++buckets[DeterministicVariants.pick(DeterministicVariants.hash(x, 70, z, 1), 8)];
            }
        }

        for (int bucket : buckets)
        {
            assertTrue("bucket starved: " + bucket, bucket > 64 * 64 / 8 / 2);
        }
    }

    @Test
    public void weightedPickRespectsProportions()
    {
        int[] weights = {9, 1};
        int heavy = 0;

        for (int i = 0; i < 10000; ++i)
        {
            if (DeterministicVariants.pickWeighted(DeterministicVariants.hash(i, 0, 0, 2), weights) == 0)
            {
                ++heavy;
            }
        }

        assertTrue("expected ~9000 heavy picks, got " + heavy, heavy > 8500 && heavy < 9500);
    }

    @Test
    public void naturalSpecsParseDecodeAndRejectInvalid()
    {
        Properties props = new Properties();
        props.setProperty("sand", "4");
        props.setProperty("stone", "2F");
        props.setProperty("dirt", "F");
        props.setProperty("bad", "3");
        props.setProperty("worse", "banana");
        NaturalProperties natural = new NaturalProperties(props);
        assertEquals(3, natural.size());
        assertNull(natural.spec("bad"));
        assertNull(natural.spec("worse"));
        NaturalProperties.Spec stone = natural.spec("stone");
        assertEquals(4, stone.variantCount());
        assertEquals(1, stone.rotationSteps(3));
        assertTrue(stone.flipped(2));
        assertTrue(!stone.flipped(1));
        assertEquals(2, natural.spec("dirt").variantCount());
    }
}
