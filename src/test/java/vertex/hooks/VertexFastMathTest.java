package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * With fastMath off (the test JVM's declared default) the replacement table must be
 * bit-faithful to vanilla's sampling: same size, same indexer, same quarter-turn cosine
 * offset. The fast path's coarser table is exercised in-game; its correctness bound
 * (worst-case argument error under one 4096th of a turn) is documented at the source.
 */
public final class VertexFastMathTest
{
    @Test
    public void vanillaModeMatchesTheRealSineWithinTablePrecision()
    {
        for (int i = 0; i <= 720; ++i)
        {
            float radians = (float)(i * Math.PI / 360.0D);
            assertEquals("sin(" + radians + ")", (float)Math.sin(radians),
                VertexFastMath.sin(radians), 0.0002F);
            assertEquals("cos(" + radians + ")", (float)Math.cos(radians),
                VertexFastMath.cos(radians), 0.0002F);
        }
    }

    @Test
    public void vanillaModeReproducesVanillaTableSamplingExactly()
    {
        // Vanilla: table[(int)(f * 10430.378) & 65535] with table[i] = sin(i * 2pi / 65536).
        float f = 1.2345F;
        float expected = (float)Math.sin((int)(f * 10430.378F) * Math.PI * 2.0D / 65536.0D);
        assertEquals(expected, VertexFastMath.sin(f), 0.0F);
    }
}
