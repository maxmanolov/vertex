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
    private static final byte[] capState = new byte[CAP_TRACK];

    private static long stateCalls;
    private static long redundantCalls;
    private static int lastTexture = -1;

    public static void enable(int cap)
    {
        ++stateCalls;

        if (cap >= 0 && cap < CAP_TRACK)
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

        if (cap >= 0 && cap < CAP_TRACK)
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

        if (texture == lastTexture)
        {
            ++redundantCalls;
        }

        lastTexture = texture;
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
