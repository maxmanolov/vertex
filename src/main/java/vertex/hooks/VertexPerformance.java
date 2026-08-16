package vertex.hooks;

import org.lwjgl.opengl.GL11;

/**
 * Performance-page hooks: the per-frame finished-build budget (Chunk Updates), its
 * stationary-player boost (Dynamic Updates), and the Smooth FPS frame-pacing drain.
 *
 * Fast Render is not a hook - it aliases the renderer backend selector in the menu
 * (arena on, display-list pipeline off; restart applies it, same as Chunk Loading).
 */
public final class VertexPerformance
{
    /** Tail of runGameLoop: glFinish drains the driver queue so frame times even out. */
    public static void afterFrame(Object minecraft)
    {
        if (VertexConfig.enabled("smoothFps"))
        {
            GL11.glFinish();
        }
    }

    /** Per-frame finished-build applications for the multicore drain. */
    public static int drainBudget()
    {
        return drainBudget(VertexConfig.value("chunkUpdates", "4"),
            VertexConfig.enabled("dynamicUpdates") && VertexHooks.playerStationary());
    }

    // ---- pure decision logic (unit-tested) -------------------------------------------

    /** chunkUpdates parses 1..5 (anything else is the shipped default 4); idle triples it. */
    static int drainBudget(String raw, boolean stationaryBoost)
    {
        String trimmed = raw == null ? "" : raw.trim();
        int base = 4;

        if (trimmed.length() == 1 && trimmed.charAt(0) >= '1' && trimmed.charAt(0) <= '5')
        {
            base = trimmed.charAt(0) - '0';
        }

        return stationaryBoost ? base * 3 : base;
    }

    /** The Fast Render alias reads the renderer key: batching backends count as ON. */
    static boolean fastRenderOn(String renderer)
    {
        String trimmed = renderer == null ? "" : renderer.trim();
        return trimmed.equals("arena") || trimmed.equals("vbo") || trimmed.isEmpty();
    }

    /** Toggling writes the plain ladder ends: ON goes displaylist, OFF goes arena. */
    static String nextFastRender(String renderer)
    {
        return fastRenderOn(renderer) ? "displaylist" : "arena";
    }

    private VertexPerformance()
    {
    }
}
