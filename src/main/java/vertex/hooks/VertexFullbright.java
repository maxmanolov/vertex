package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Fullbright (#116): render everything at maximum brightness and skip the work that only
 * exists to keep baked lighting visually current.
 *
 * Two halves, both client-render-only. The brightness half overrides every
 * getMixedBrightnessForBlock result with max lightmap coordinates, so geometry bakes
 * fully lit at tessellation time. The performance half skips RenderGlobal's light-only
 * re-mark path (markBlockForRenderUpdate): with brightness ignored, light-level changes
 * no longer invalidate geometry, so the section rebuilds they would have triggered -
 * the cave-mining and dusk/dawn rebuild storms - never happen. World light propagation
 * is untouched (it is game logic shared with the integrated server), so mob spawning,
 * crop growth and true light levels keep working.
 *
 * The active flag is a volatile refreshed once per frame; the tessellation-path check in
 * VertexDynamicLights.adjust is a single volatile read when off. Toggle transitions
 * trigger a renderer reload so baked brightness refreshes in both directions.
 */
public final class VertexFullbright
{
    /** Max sky + max block lightmap coordinates, the standard packed fullbright value. */
    public static final int FULLBRIGHT_PACKED = 0xF000F0;

    private static volatile boolean active = false;
    public static long skippedRemarks = 0L;

    private static boolean disabled = false;
    private static boolean resolved = false;
    private static Field theWorld;
    private static Field renderGlobal;
    private static Method loadRenderers;

    /** Tessellation-path check: one volatile read when fullbright is off. */
    public static boolean fullbright()
    {
        return active;
    }

    /** Head guard on RenderGlobal.markBlockForRenderUpdate: true = skip the re-mark. */
    public static boolean interceptLightRemark(Object renderGlobalInstance)
    {
        if (!active || Boolean.getBoolean("vertex.test.disableRemarkSkip"))
        {
            return false;
        }

        ++skippedRemarks;
        return true;
    }

    /** Once per frame from the harness tick: refresh the flag, reload on transitions. */
    public static void tick(Object minecraft)
    {
        if (disabled)
        {
            return;
        }

        boolean next = VertexConfig.enabled("fullbright");

        if (next == active)
        {
            return;
        }

        active = next;
        LogWrapper.info("[Vertex] Fullbright " + (next ? "enabled" : "disabled"));

        try
        {
            // Brightness is baked into display lists at tessellation time, so flipping
            // the flag only affects future builds; reload the grid so the change is
            // immediate in both directions. The multicore pipeline handles this like any
            // loadRenderers (generation invalidation via onRenderersReloaded).
            if (!resolved)
            {
                Class<?> mc = minecraft.getClass();
                theWorld = mc.getDeclaredField(Mappings.MC_THE_WORLD);
                theWorld.setAccessible(true);
                renderGlobal = mc.getDeclaredField(Mappings.MC_RENDER_GLOBAL);
                renderGlobal.setAccessible(true);
                resolved = true;
            }

            Object rg = renderGlobal.get(minecraft);

            if (theWorld.get(minecraft) == null || rg == null)
            {
                // No baked geometry to refresh; the flag alone is enough at the menu.
                return;
            }

            if (loadRenderers == null)
            {
                loadRenderers = rg.getClass().getMethod(Mappings.RG_LOAD_RENDERERS);
            }

            loadRenderers.invoke(rg);
        }
        catch (Exception e)
        {
            // The brightness flag keeps working; only the instant refresh is lost.
            disabled = true;
            LogWrapper.warning("[Vertex] Fullbright transition reload disabled after failure: " + e);
        }
    }

    private VertexFullbright()
    {
    }
}
