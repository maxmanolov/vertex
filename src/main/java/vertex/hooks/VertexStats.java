package vertex.hooks;

import net.minecraft.launchwrapper.LogWrapper;

/**
 * Lightweight diagnostics: counts Vertex's own activity and, when the "diagnostics" config
 * key is enabled, logs one summary line per minute. Counters are only touched from the
 * client thread; the cost with diagnostics off is a long increment per event and one time
 * check per frame.
 */
public final class VertexStats
{
    private static final long REPORT_INTERVAL_MS = 60000L;
    private static final String[] SKIP_KEYS = {"sky", "clouds", "weather", "voidParticles", "textureAnimations", "fog"};

    public static long emptyReplays = 0L;
    private static long promotions = 0L;
    private static long rebuilds = 0L;
    private static long lastReport = System.currentTimeMillis();

    public static void promotion()
    {
        ++promotions;
    }

    public static void rebuild()
    {
        ++rebuilds;
    }

    /** Called once per frame from consumeImmediates; emits the periodic report when due. */
    public static void tick()
    {
        VertexFrameStats.frame();
        long now = System.currentTimeMillis();

        if (now - lastReport < REPORT_INTERVAL_MS)
        {
            return;
        }

        lastReport = now;

        if (!VertexConfig.enabled("diagnostics"))
        {
            // Drain EVERY counter while disabled, or the first enabled report would carry
            // data accumulated since forever, labelled as one minute (kyrofx #37).
            drainAll();
            return;
        }

        StringBuilder skips = new StringBuilder();

        for (String key : SKIP_KEYS)
        {
            if (!VertexConfig.enabled(key))
            {
                if (skips.length() > 0)
                {
                    skips.append(",");
                }

                skips.append(key);
            }
        }

        long[] gl = VertexGLStats.drain();
        long redundantPct = gl[0] > 0 ? gl[1] * 100L / gl[0] : 0L;
        long reportPromotions = promotions;
        long reportRebuilds = rebuilds;
        LogWrapper.info("[Vertex] Last 60s: immediate promotions=" + reportPromotions + " rebuilds=" + reportRebuilds
            + " glStateCalls=" + gl[0] + " glRedundant=" + gl[1] + " redundantPct=" + redundantPct
            + " ctmApplied=" + VertexCtm.applied + " skyDraws=" + VertexSky.draws + " entityVariants=" + VertexRandomEntities.applied + " emptyReplays=" + emptyReplays + " naturalVariants=" + VertexIcons.naturalVariants + " iconHits=" + VertexIcons.hits + " iconSideHits=" + VertexIcons.sideHits
            + " buildQ=" + VertexMulticore.pendingDepth()
            + " fbSkippedRemarks=" + VertexFullbright.skippedRemarks
            + VertexFrameStats.drainReport()
            + " skippedPasses=" + (skips.length() > 0 ? skips.toString() : "none"));
        drainAll();
    }

    private static void drainAll()
    {
        promotions = 0L;
        rebuilds = 0L;
        emptyReplays = 0L;
        VertexIcons.hits = 0L;
        VertexIcons.sideHits = 0L;
        VertexIcons.naturalVariants = 0L;
        VertexRandomEntities.applied = 0L;
        VertexSky.draws = 0L;
        VertexCtm.applied = 0L;
        VertexGLStats.drain();
        VertexFrameStats.resetWindow();
        VertexFullbright.skippedRemarks = 0L;
    }

    private VertexStats()
    {
    }
}
