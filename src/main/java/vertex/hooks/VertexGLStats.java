package vertex.hooks;

import org.lwjgl.opengl.GL11;

/**
 * Fast Render investigation instrumentation (docs/ROADMAP.md #5): every game call to the
 * three highest-frequency GL state functions is routed through these wrappers, which count
 * total and redundant transitions before forwarding. Redundant means a call that sets state
 * to its current value - exactly what OptiFine-style state batching would eliminate, so the
 * redundancy ratio measured in real play is the upper bound on Fast Render's possible win.
 * Counting costs one array read per call; reporting rides the per-minute diagnostics line.
 */
public final class VertexGLStats
{
    private static final int CAP_TRACK = 65536;
    private static final int GL_TEXTURE_2D_CAP = 3553;
    private static final byte[] capState = new byte[CAP_TRACK];

    private static long stateCalls;
    private static long redundantCalls;
    // OpenGL keeps one binding per (texture unit, target) and one GL_TEXTURE_2D enable
    // per unit (kyrofx #38); redundancy must be judged against that state, not a single
    // last-id. Keys are (unit << 32 | target).
    private static final java.util.HashMap<Long, Integer> lastBound = new java.util.HashMap<Long, Integer>();
    private static final java.util.HashMap<Integer, Byte> textureCapByUnit = new java.util.HashMap<Integer, Byte>();
    private static int activeUnit = 0;

    public static void activeTexture(int unit)
    {
        activeUnit = unit;
        org.lwjgl.opengl.GL13.glActiveTexture(unit);
    }

    public static void activeTextureArb(int unit)
    {
        activeUnit = unit;
        org.lwjgl.opengl.ARBMultitexture.glActiveTextureARB(unit);
    }

    public static void enable(int cap)
    {
        ++stateCalls;

        if (cap == GL_TEXTURE_2D_CAP)
        {
            Byte previous = textureCapByUnit.put(Integer.valueOf(activeUnit), Byte.valueOf((byte)1));

            if (previous != null && previous.byteValue() == 1)
            {
                ++redundantCalls;
            }
        }
        else if (cap >= 0 && cap < CAP_TRACK)
        {
            if (capState[cap] == 1)
            {
                ++redundantCalls;
            }

            capState[cap] = 1;
        }

        GL11.glEnable(cap);
    }

    public static void disable(int cap)
    {
        ++stateCalls;

        if (cap == GL_TEXTURE_2D_CAP)
        {
            Byte previous = textureCapByUnit.put(Integer.valueOf(activeUnit), Byte.valueOf((byte)2));

            if (previous != null && previous.byteValue() == 2)
            {
                ++redundantCalls;
            }
        }
        else if (cap >= 0 && cap < CAP_TRACK)
        {
            if (capState[cap] == 2)
            {
                ++redundantCalls;
            }

            capState[cap] = 2;
        }

        GL11.glDisable(cap);
    }

    public static void bindTexture(int target, int texture)
    {
        ++stateCalls;
        Long key = Long.valueOf((long)activeUnit << 32 | (target & 0xFFFFFFFFL));
        Integer previous = lastBound.put(key, Integer.valueOf(texture));

        if (previous != null && previous.intValue() == texture)
        {
            ++redundantCalls;
        }

        GL11.glBindTexture(target, texture);
    }

    /** Drained by the per-minute diagnostics report; returns {total, redundant}. */
    public static long[] drain()
    {
        long[] out = {stateCalls, redundantCalls};
        stateCalls = 0L;
        redundantCalls = 0L;
        return out;
    }

    private VertexGLStats()
    {
    }
}
