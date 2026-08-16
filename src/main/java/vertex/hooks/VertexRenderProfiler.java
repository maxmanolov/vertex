package vertex.hooks;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Opt-in render-phase profiler (-Dvertex.profileRender=true): times the five client-thread
 * phases of the vanilla terrain renderer via {@link vertex.transform.BracketPatch} brackets
 * and logs a 10-second summary. The phases nest as clip / sortAndRender(submit) /
 * updateRenderers, so "traversal" is derived as sortAndRender minus submit. With the flag
 * off no bracket is woven and patched classes are byte-identical; the only residue is one
 * static-final-false branch per frame in consumeImmediates.
 *
 * All entry points run on the client thread; a sample whose exit never fires (an exception
 * escaped the bracket) is silently replaced by the next enter.
 */
public final class VertexRenderProfiler
{
    public static final boolean ACTIVE = Boolean.getBoolean("vertex.profileRender");

    /** Bracket ids, shared with the transformer wiring. */
    public static final int PHASE_CLIP = 0;
    public static final int PHASE_SORT = 1;
    public static final int PHASE_SUBMIT = 2;
    public static final int PHASE_UPDATE = 3;
    public static final int PHASE_CLOUD = 4;

    private static final String[] NAMES = {"clip", "sort", "submit", "update", "cloud"};
    private static final long REPORT_NANOS = 10_000_000_000L;

    private static final long[] openSince = new long[NAMES.length];
    private static final long[] windowNanos = new long[NAMES.length];
    private static final long[] windowCalls = new long[NAMES.length];
    private static long windowStart = 0L;
    private static long frames = 0L;

    private static Object renderGlobal;
    private static boolean countersResolved = false;
    private static Field renderersLoaded;
    private static Field renderersClipped;
    private static Field renderersOccluded;
    private static Field renderersRendered;
    private static Field renderersSkippingPass;
    private static Field glRenderLists;

    public static void enter(int phase)
    {
        openSince[phase] = System.nanoTime();
    }

    public static void exit(int phase)
    {
        long since = openSince[phase];

        if (since != 0L)
        {
            openSince[phase] = 0L;
            windowNanos[phase] += System.nanoTime() - since;
            ++windowCalls[phase];
        }
    }

    /** Once per updateRenderers call, from consumeImmediates; drives the report cadence. */
    public static void frame(Object renderGlobalInstance)
    {
        renderGlobal = renderGlobalInstance;
        ++frames;
        long now = System.nanoTime();

        if (windowStart == 0L)
        {
            windowStart = now;
            return;
        }

        if (now - windowStart >= REPORT_NANOS)
        {
            report(now - windowStart);
            windowStart = now;
        }
    }

    private static void report(long windowSpan)
    {
        double windowMs = windowSpan / 1_000_000.0D;
        StringBuilder line = new StringBuilder("[VertexProf] window=");
        line.append(String.format("%.1fs frames=%d", windowSpan / 1_000_000_000.0D, frames));

        for (int phase = 0; phase < NAMES.length; ++phase)
        {
            double totalMs = windowNanos[phase] / 1_000_000.0D;
            line.append(String.format(" %s=%.1fms/%d", NAMES[phase], totalMs, windowCalls[phase]));
        }

        double traversalMs = (windowNanos[PHASE_SORT] - windowNanos[PHASE_SUBMIT]) / 1_000_000.0D;
        line.append(String.format(" traversal=%.1fms", traversalMs));
        line.append(String.format(" busyPct=%.1f",
            (windowNanos[PHASE_CLIP] + windowNanos[PHASE_SORT] + windowNanos[PHASE_UPDATE]
                + windowNanos[PHASE_CLOUD]) * 100.0D / windowSpan));
        appendSectionCounters(line);
        LogWrapper.info(line.toString());

        for (int phase = 0; phase < NAMES.length; ++phase)
        {
            windowNanos[phase] = 0L;
            windowCalls[phase] = 0L;
        }

        frames = 0L;
    }

    /** Vanilla per-frame renderer counters, read reflectively at report time only. */
    private static void appendSectionCounters(StringBuilder line)
    {
        Object instance = renderGlobal;

        if (instance == null)
        {
            return;
        }

        try
        {
            if (!countersResolved)
            {
                Class<?> cls = instance.getClass();
                renderersLoaded = accessible(cls, Mappings.RG_DBG_LOADED);
                renderersClipped = accessible(cls, Mappings.RG_DBG_CLIPPED);
                renderersOccluded = accessible(cls, Mappings.RG_DBG_OCCLUDED);
                renderersRendered = accessible(cls, Mappings.RG_DBG_RENDERED);
                renderersSkippingPass = accessible(cls, Mappings.RG_DBG_SKIPPED_PASS);
                glRenderLists = accessible(cls, Mappings.RG_GL_RENDER_LISTS);
                countersResolved = true;
            }

            line.append(" sections[loaded=").append(renderersLoaded.getInt(instance))
                .append(" rendered=").append(renderersRendered.getInt(instance))
                .append(" clipped=").append(renderersClipped.getInt(instance))
                .append(" occluded=").append(renderersOccluded.getInt(instance))
                .append(" emptyPass=").append(renderersSkippingPass.getInt(instance))
                .append(" lastPassLists=").append(((List<?>)glRenderLists.get(instance)).size())
                .append(" pendingDirty=").append(VertexHooks.pendingUpdates(instance))
                .append(" buildQ=").append(VertexMulticore.pendingDepth())
                .append(']');
        }
        catch (Exception e)
        {
            // Profiling must never take the session down; drop the counters, keep timing.
            line.append(" sections[unavailable]");
        }
    }

    private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private VertexRenderProfiler()
    {
    }
}
