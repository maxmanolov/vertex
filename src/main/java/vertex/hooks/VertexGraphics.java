package vertex.hooks;

import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Fancy/fast decoupling: settings that vanilla derives from the single fancyGraphics
 * flag get their own tri-state keys (default follows Graphics, fast forces off, fancy
 * forces on).
 *
 * Trees: loadRenderers pushes fancyGraphics into both leaf blocks; the reroute applies
 * the override and captures the leaf-block instances so a menu flip can re-push without
 * waiting for the next renderer reload. Dropped items: RenderItem's fancyGraphics reads
 * reroute through the override per frame. Smooth lighting level scales the ambient
 * occlusion corner darkening; dynamic FOV pins the player FOV multiplier to 1.
 */
public final class VertexGraphics
{
    private static boolean disabled = false;

    private static Method leavesSetGraphics;
    private static final Object[] leafBlocks = new Object[2];
    private static java.lang.reflect.Field fancyGraphicsField;

    // ---- trees ------------------------------------------------------------------------

    /** Reroute of BlockLeaves.setGraphicsLevel inside loadRenderers. */
    public static void setLeavesGraphics(Object leaves, boolean vanillaFancy)
    {
        try
        {
            if (leavesSetGraphics == null)
            {
                leavesSetGraphics = leaves.getClass().getMethod(
                    Mappings.LEAVES_SET_GRAPHICS, boolean.class);
                leavesSetGraphics.setAccessible(true);
            }

            capture(leaves);
            leavesSetGraphics.invoke(leaves,
                Boolean.valueOf(overrideFancy(VertexConfig.value("trees", "default"), vanillaFancy)));
        }
        catch (Throwable t)
        {
            disable("setLeavesGraphics", t);

            try
            {
                if (leavesSetGraphics != null)
                {
                    leavesSetGraphics.invoke(leaves, Boolean.valueOf(vanillaFancy));
                }
            }
            catch (Throwable ignored)
            {
                // Leaves keep their previous graphics level until the next reload.
            }
        }
    }

    /** Menu flip entry: derive the vanilla flag from the live settings, then re-push. */
    static void applyTrees(Object settings)
    {
        applyTrees(readFancy(settings));
    }

    /** Re-push the current override into the captured leaf blocks. */
    static void applyTrees(boolean vanillaFancy)
    {
        if (disabled || leavesSetGraphics == null)
        {
            return;
        }

        boolean fancy = overrideFancy(VertexConfig.value("trees", "default"), vanillaFancy);

        for (Object leaves : leafBlocks)
        {
            if (leaves != null)
            {
                try
                {
                    leavesSetGraphics.invoke(leaves, Boolean.valueOf(fancy));
                }
                catch (Throwable t)
                {
                    disable("applyTrees", t);
                }
            }
        }
    }

    // ---- dropped items ------------------------------------------------------------------

    /** Reroute of RenderItem's gameSettings.fancyGraphics reads. */
    public static boolean fancyItems(Object settings)
    {
        boolean vanilla = readFancy(settings);
        return disabled ? vanilla
            : overrideFancy(VertexConfig.value("droppedItems", "default"), vanilla);
    }

    // ---- grass sides ---------------------------------------------------------------------

    private static java.lang.reflect.Field fancyGrassField;
    private static boolean grassOverrideLogged = false;

    /**
     * Reroute of the per-frame RenderBlocks.fancyGrass derivation: the tri-state
     * override lands in the static gate instead of the raw fancyGraphics value. Runs
     * once per frame, so menu flips apply on the next frame with no captured state.
     */
    public static void applyFancyGrass(boolean vanillaFancy)
    {
        try
        {
            if (fancyGrassField == null)
            {
                Class<?> renderBlocks = net.minecraft.launchwrapper.Launch.classLoader
                    .loadClass(Mappings.RENDER_BLOCKS);
                fancyGrassField = renderBlocks.getDeclaredField(Mappings.RB_FANCY_GRASS);
                fancyGrassField.setAccessible(true);
            }

            boolean value = overrideFancy(VertexConfig.value("grass", "default"), vanillaFancy);
            fancyGrassField.setBoolean(null, value);

            if (!grassOverrideLogged && value != vanillaFancy)
            {
                grassOverrideLogged = true;
                LogWrapper.info("[Vertex] Grass override active: "
                    + (value ? "fancy" : "fast") + " against Graphics " + (vanillaFancy ? "fancy" : "fast"));
            }
        }
        catch (Throwable t)
        {
            disable("applyFancyGrass", t);
        }
    }

    // ---- smooth lighting level / dynamic FOV -------------------------------------------

    /** Return adjuster on Block.getAmbientOcclusionLightValue. */
    public static float aoLightValue(float vanilla)
    {
        return scaleAoValue(vanilla, aoLevelPercent(VertexConfig.value("aoLevel", "100")));
    }

    /** Return adjuster on EntityPlayerSP.getFOVMultiplier. */
    public static float fovMultiplier(float vanilla)
    {
        return VertexConfig.enabled("dynamicFov") ? vanilla : 1.0F;
    }

    // ---- pure decision logic (unit-tested) -----------------------------------------------

    /** Tri-state: "fast" forces false, "fancy" forces true, anything else follows vanilla. */
    static boolean overrideFancy(String mode, boolean vanillaFancy)
    {
        String trimmed = mode == null ? "" : mode.trim();
        return trimmed.equals("fast") ? false : trimmed.equals("fancy") ? true : vanillaFancy;
    }

    /** aoLevel parses to 0/50/100; anything else is vanilla (100). */
    static int aoLevelPercent(String raw)
    {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.equals("0") ? 0 : trimmed.equals("50") ? 50 : 100;
    }

    /**
     * Corner darkening interpolates toward none: level 100 keeps the vanilla value,
     * level 0 removes the darkening entirely (factor 1).
     */
    static float scaleAoValue(float vanilla, int levelPercent)
    {
        return 1.0F - levelPercent / 100.0F * (1.0F - vanilla);
    }

    // ---- plumbing --------------------------------------------------------------------------

    private static void capture(Object leaves)
    {
        for (int i = 0; i < leafBlocks.length; ++i)
        {
            if (leafBlocks[i] == leaves)
            {
                return;
            }

            if (leafBlocks[i] == null)
            {
                leafBlocks[i] = leaves;
                return;
            }
        }
    }

    private static boolean readFancy(Object settings)
    {
        try
        {
            if (fancyGraphicsField == null)
            {
                fancyGraphicsField = settings.getClass().getDeclaredField(Mappings.GS_FANCY_GRAPHICS);
                fancyGraphicsField.setAccessible(true);
            }

            return fancyGraphicsField.getBoolean(settings);
        }
        catch (Throwable t)
        {
            disable("readFancy", t);
            return true;
        }
    }

    private static void disable(String where, Throwable t)
    {
        if (!disabled)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Graphics decoupling disabled after failure in " + where);
            t.printStackTrace();
        }
    }

    private VertexGraphics()
    {
    }
}
