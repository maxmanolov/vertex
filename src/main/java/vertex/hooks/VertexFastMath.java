package vertex.hooks;

import net.minecraft.launchwrapper.LogWrapper;

/**
 * Table-backed replacements for MathHelper's sin/cos. Vanilla's table holds 65,536
 * floats (256 KB) - far past L1 and most of L2 for a hot lookup. The fast variant is a
 * 4,096-entry table (16 KB, L1-resident) sampled at bucket centers, worst-case argument
 * error under 0.0008 radians - visually indistinguishable, measurably friendlier to the
 * cache during AO-heavy tessellation.
 *
 * The mode is resolved once at class load (like the renderer and multicore selectors)
 * so the branch below is a JIT constant: fastMath=true needs a restart, and per-call
 * config lookups - which would cost more than the sine itself - never happen. With the
 * key off, a bit-exact copy of vanilla's table (same size, same sampling) keeps the
 * replaced methods byte-for-byte faithful.
 */
public final class VertexFastMath
{
    private static final boolean FAST = VertexConfig.enabled("fastMath");

    private static final int FAST_BITS = 12;
    private static final int FAST_SIZE = 1 << FAST_BITS;             // 4096
    private static final int FAST_MASK = FAST_SIZE - 1;
    private static final float FAST_INDEXER = FAST_SIZE / ((float)Math.PI * 2.0F);

    private static final int VANILLA_SIZE = 65536;
    private static final float VANILLA_INDEXER = 10430.378F;         // vanilla's 65536 / 2pi

    private static final float[] TABLE;

    static
    {
        if (FAST)
        {
            TABLE = new float[FAST_SIZE];

            for (int i = 0; i < FAST_SIZE; ++i)
            {
                // Bucket centers halve the worst-case error vs. sampling bucket starts.
                TABLE[i] = (float)Math.sin((i + 0.5D) * Math.PI * 2.0D / FAST_SIZE);
            }

            LogWrapper.info("[Vertex] Fast math active: 4096-entry trig table");
        }
        else
        {
            TABLE = new float[VANILLA_SIZE];

            for (int i = 0; i < VANILLA_SIZE; ++i)
            {
                TABLE[i] = (float)Math.sin(i * Math.PI * 2.0D / VANILLA_SIZE);
            }
        }
    }

    /** Body replacement for MathHelper.sin. */
    public static float sin(float radians)
    {
        if (FAST)
        {
            return TABLE[(int)(radians * FAST_INDEXER) & FAST_MASK];
        }

        return TABLE[(int)(radians * VANILLA_INDEXER) & 65535];
    }

    /** Body replacement for MathHelper.cos: vanilla offsets by a quarter turn. */
    public static float cos(float radians)
    {
        if (FAST)
        {
            return TABLE[(int)(radians * FAST_INDEXER + FAST_SIZE / 4.0F) & FAST_MASK];
        }

        return TABLE[(int)(radians * VANILLA_INDEXER + 16384.0F) & 65535];
    }

    private VertexFastMath()
    {
    }
}
