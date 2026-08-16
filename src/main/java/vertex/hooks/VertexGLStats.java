package vertex.hooks;

import org.lwjgl.opengl.GL11;

/**
 * Every game call to the three highest-frequency GL state functions routes through these
 * wrappers (woven by GLCallCountPatch across all game classes). Two roles:
 *
 * Counting (always on): total and redundant transitions per minute ride the diagnostics
 * line - the redundancy ratio measured in real play is the upper bound on state-batching
 * wins (docs/ROADMAP.md #5).
 *
 * Skipping (glStateCache=true, restart required): a call the tracker knows to be a
 * no-op returns without touching the driver. The tracker is conservative - unknown
 * state always forwards - and it invalidates wherever GL can change behind it:
 * glPopAttrib clears everything, glDeleteTextures forgets the ids (glGenTextures can
 * reissue them). Correctness gate before any default flip: bit-identical frame captures
 * against a cache-off run of the same fixture.
 */
public final class VertexGLStats
{
    /** Resolved once at class load, like the renderer selector: zero per-call cost. */
    private static final boolean SKIP = VertexConfig.enabled("glStateCache");

    private static final GLStateTracker TRACKER = new GLStateTracker();

    private static long stateCalls;
    private static long redundantCalls;
    private static long skippedCalls;

    public static void activeTexture(int unit)
    {
        TRACKER.setActiveUnit(unit);
        org.lwjgl.opengl.GL13.glActiveTexture(unit);
    }

    public static void activeTextureArb(int unit)
    {
        TRACKER.setActiveUnit(unit);
        org.lwjgl.opengl.ARBMultitexture.glActiveTextureARB(unit);
    }

    public static void enable(int cap)
    {
        ++stateCalls;

        if (TRACKER.redundantEnable(cap))
        {
            ++redundantCalls;

            if (SKIP)
            {
                ++skippedCalls;
                return;
            }
        }

        GL11.glEnable(cap);
    }

    public static void disable(int cap)
    {
        ++stateCalls;

        if (TRACKER.redundantDisable(cap))
        {
            ++redundantCalls;

            if (SKIP)
            {
                ++skippedCalls;
                return;
            }
        }

        GL11.glDisable(cap);
    }

    public static void bindTexture(int target, int texture)
    {
        ++stateCalls;

        if (TRACKER.redundantBind(target, texture))
        {
            ++redundantCalls;

            if (SKIP)
            {
                ++skippedCalls;
                return;
            }
        }

        GL11.glBindTexture(target, texture);
    }

    public static void popAttrib()
    {
        TRACKER.invalidateAll();
        GL11.glPopAttrib();
    }

    public static void deleteTexture(int texture)
    {
        TRACKER.forgetTexture(texture);
        GL11.glDeleteTextures(texture);
    }

    public static void deleteTextures(java.nio.IntBuffer textures)
    {
        for (int i = textures.position(); i < textures.limit(); ++i)
        {
            TRACKER.forgetTexture(textures.get(i));
        }

        GL11.glDeleteTextures(textures);
    }

    /** Drained by the per-minute diagnostics report; returns {total, redundant, skipped}. */
    public static long[] drain()
    {
        long[] out = {stateCalls, redundantCalls, skippedCalls};
        stateCalls = 0L;
        redundantCalls = 0L;
        skippedCalls = 0L;
        return out;
    }

    private VertexGLStats()
    {
    }
}
