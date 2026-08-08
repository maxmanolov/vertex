package vertex.hooks;

import net.minecraft.launchwrapper.LogWrapper;

/**
 * Forensic attribution for section re-marks, active only with -Dvertex.test.markAudit=true
 * (#118). RenderGlobal has exactly three public entry points that can dirty a section -
 * markBlockForUpdate, markBlockForRenderUpdate and markBlockRangeForRenderUpdate - plus the
 * private markBlocksForUpdate funnel they all share. Head hooks count each entry and
 * periodically sample a caller stack, which is what actually identifies a re-mark loop:
 * counters say how much, stacks say who. Hooks are only injected when the flag is set, so
 * the shipped classes are byte-identical for normal players.
 */
public final class VertexMarkAudit
{
    public static final boolean ACTIVE = Boolean.getBoolean("vertex.test.markAudit");

    private static final int STACK_SAMPLE_INTERVAL = 512;
    private static final int LIST_STACK_SAMPLE_INTERVAL = 64;
    private static final int STACK_DEPTH = 12;

    private static long markUpdate = 0L;
    private static long markLight = 0L;
    private static long markRange = 0L;
    private static long funnel = 0L;
    private static long listAdds = 0L;
    private static long sinceStack = 0L;
    private static long sinceListStack = 0L;
    private static long lastReport = System.currentTimeMillis();
    private static java.lang.reflect.Field toUpdateField;

    /**
     * The four mark methods are not the only writers: markRenderersForNewPosition and
     * loadRenderers add to worldRenderersToUpdate directly. Swapping the plain ArrayList
     * for a counting subclass attributes EVERY addition, whatever the path. Re-checked
     * each frame because loadRenderers replaces the list object wholesale.
     */
    public static void ensureWrapped(Object renderGlobal)
    {
        if (!ACTIVE || renderGlobal == null)
        {
            return;
        }

        try
        {
            if (toUpdateField == null)
            {
                toUpdateField = renderGlobal.getClass().getDeclaredField(vertex.Mappings.RG_WORLD_RENDERERS_TO_UPDATE);
                toUpdateField.setAccessible(true);
            }

            Object current = toUpdateField.get(renderGlobal);

            if (current != null && !(current instanceof CountingList))
            {
                CountingList wrapped = new CountingList();
                wrapped.addAll((java.util.List<?>)current);
                toUpdateField.set(renderGlobal, wrapped);
                LogWrapper.info("[VertexMarkAudit] wrapped worldRenderersToUpdate, size=" + wrapped.size());
            }
        }
        catch (Exception e)
        {
            LogWrapper.warning("[VertexMarkAudit] list wrap failed: " + e);
        }
    }

    private static final class CountingList extends java.util.ArrayList<Object>
    {
        @Override
        public boolean add(Object element)
        {
            ++listAdds;

            if (++sinceListStack >= LIST_STACK_SAMPLE_INTERVAL)
            {
                sinceListStack = 0L;
                StackTraceElement[] stack = new Throwable().getStackTrace();
                StringBuilder line = new StringBuilder("[VertexMarkAudit] sample listAdd:");

                for (int i = 1; i < stack.length && i < 1 + STACK_DEPTH; ++i)
                {
                    line.append(' ').append(stack[i].getClassName()).append('.').append(stack[i].getMethodName());
                }

                LogWrapper.info(line.toString());
            }

            return super.add(element);
        }
    }

    /** Head hook on markBlockForUpdate(III): count only, never skip. */
    public static boolean onMarkUpdate(Object renderGlobal)
    {
        ++markUpdate;
        sampleStack("markBlockForUpdate");
        return false;
    }

    /** Head hook on markBlockForRenderUpdate(III): count only, never skip. */
    public static boolean onMarkLight(Object renderGlobal)
    {
        ++markLight;
        sampleStack("markBlockForRenderUpdate");
        return false;
    }

    /** Head hook on markBlockRangeForRenderUpdate(IIIIII): count only, never skip. */
    public static boolean onMarkRange(Object renderGlobal)
    {
        ++markRange;
        sampleStack("markBlockRangeForRenderUpdate");
        return false;
    }

    /** Head hook on the private markBlocksForUpdate funnel: cross-check total. */
    public static boolean onFunnel(Object renderGlobal)
    {
        ++funnel;
        report();
        return false;
    }

    private static void sampleStack(String entry)
    {
        if (++sinceStack < STACK_SAMPLE_INTERVAL)
        {
            return;
        }

        sinceStack = 0L;
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder line = new StringBuilder("[VertexMarkAudit] sample ").append(entry).append(':');

        for (int i = 2; i < stack.length && i < 2 + STACK_DEPTH; ++i)
        {
            line.append(' ').append(stack[i].getClassName()).append('.').append(stack[i].getMethodName());
        }

        LogWrapper.info(line.toString());
    }

    private static void report()
    {
        long now = System.currentTimeMillis();

        if (now - lastReport < 10000L)
        {
            return;
        }

        lastReport = now;
        LogWrapper.info("[VertexMarkAudit] last 10s: markBlockForUpdate=" + markUpdate
            + " markBlockForRenderUpdate=" + markLight
            + " markBlockRangeForRenderUpdate=" + markRange
            + " funnel=" + funnel);
        markUpdate = 0L;
        markLight = 0L;
        markRange = 0L;
        funnel = 0L;
    }

    private VertexMarkAudit()
    {
    }
}
