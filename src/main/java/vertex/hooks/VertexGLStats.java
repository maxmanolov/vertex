package vertex.hooks;

import org.lwjgl.opengl.GL11;

/**
 * Fast Render investigation instrumentation (docs/ROADMAP.md #5): every game call to the
 * three highest-frequency GL state functions routes through these wrappers (woven
 * class-wide by GLCallCountPatch), counting total and redundant transitions before
 * forwarding. Redundant means a call that sets state to its current value - the ratio
 * measured in real play is the upper bound on state-batching wins.
 *
 * The model behind the counts lives in {@link GLStateTracker}: per-unit sampler and
 * texgen capabilities, per-(unit, target) bindings, and invalidation at the two points
 * GL state changes behind the wrappers' back (glPopAttrib, glDeleteTextures - both also
 * woven), so the redundancy figure stays honest across those events.
 *
 * Actually skipping the redundant calls was built and measured on 2026-08-15: with
 * 4.5M skips per minute engaged, frame times did not move on macOS (ftP50/ftP99
 * identical), so the skip does not exist - the counters stay because they price the
 * opportunity for platforms where drivers are not this cheap.
 */
public final class VertexGLStats
{
    private static final GLStateTracker TRACKER = new GLStateTracker();

    private static long stateCalls;
    private static long redundantCalls;

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
        }

        GL11.glEnable(cap);
    }

    public static void disable(int cap)
    {
        ++stateCalls;

        if (TRACKER.redundantDisable(cap))
        {
            ++redundantCalls;
        }

        GL11.glDisable(cap);
    }

    public static void bindTexture(int target, int texture)
    {
        ++stateCalls;

        if (TRACKER.redundantBind(target, texture))
        {
            ++redundantCalls;
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
