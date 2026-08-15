package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;

/**
 * Detail-settings hooks for the sky and cloud passes: sun/moon suppression, star
 * suppression, the cloud-height lift, and the depth-fog (void darkening) kill switch.
 *
 * Sun and moon have no dedicated method to skip, so the reroutes cooperate: every
 * bindTexture call inside renderSky reports its texture, a sun/moon bind arms the gate,
 * and the next tessellator flush either draws (armed pass, feature on) or resets the
 * tessellator without drawing (feature off). Every flush disarms, so the untextured
 * glow/horizon flushes and the End sky never suppress. Stars draw as a display list;
 * the glCallList reroute drops exactly the star list id.
 *
 * Any reflective failure permanently reverts to vanilla behavior (draws and binds keep
 * happening through the resolved handles or the fallback paths); gates never suppress
 * after a failure.
 */
public final class VertexSkyDetails
{
    private static boolean disabled = false;

    private static Method bindTexture;
    private static Method tessDraw;
    private static Method tessReset;
    private static Field tessIsDrawing;
    private static Field starListField;

    /** Non-null between a sun/moon bind and the flush it feeds. */
    private static String armedCelestial;

    private static Object starListOwner;
    private static int starList = Integer.MIN_VALUE;

    public static long suppressedDraws = 0L;

    // ---- renderSky reroutes ---------------------------------------------------------

    /** Reroute of TextureManager.bindTexture inside renderSky. */
    public static void bindSkyTexture(Object textureManager, Object location)
    {
        try
        {
            if (bindTexture == null)
            {
                bindTexture = textureManager.getClass().getMethod(Mappings.TEXTURE_BIND, location.getClass());
                bindTexture.setAccessible(true);
            }

            armedCelestial = celestialTexture(String.valueOf(location)) ? String.valueOf(location) : null;
            bindTexture.invoke(textureManager, location);
        }
        catch (Throwable t)
        {
            armedCelestial = null;
            disable("bindSkyTexture", t);
        }
    }

    /** Reroute of Tessellator.draw inside renderSky; returns the flushed byte count. */
    public static int skyDraw(Object tessellator)
    {
        boolean suppress = !disabled && armedCelestial != null && !VertexConfig.enabled("sunMoon");
        armedCelestial = null;

        try
        {
            if (tessDraw == null)
            {
                tessDraw = tessellator.getClass().getMethod(Mappings.TESS_DRAW);
                tessDraw.setAccessible(true);
                tessReset = tessellator.getClass().getDeclaredMethod(Mappings.TESS_RESET);
                tessReset.setAccessible(true);
                tessIsDrawing = tessellator.getClass().getDeclaredField(Mappings.TESS_IS_DRAWING);
                tessIsDrawing.setAccessible(true);
            }

            if (suppress)
            {
                // Terminate the pending geometry without submitting it: clear the
                // drawing latch first so reset cannot trip "Already tesselating".
                tessIsDrawing.setBoolean(tessellator, false);
                tessReset.invoke(tessellator);
                ++suppressedDraws;
                return 0;
            }

            return ((Integer)tessDraw.invoke(tessellator)).intValue();
        }
        catch (Throwable t)
        {
            disable("skyDraw", t);
            return fallbackDraw(tessellator);
        }
    }

    /** Reroute of GL11.glCallList inside renderSky: drops exactly the star list. */
    public static void skyCallList(int list)
    {
        if (!disabled && list == starList() && !VertexConfig.enabled("stars"))
        {
            return;
        }

        GL11.glCallList(list);
    }

    // ---- cloud pass ----------------------------------------------------------------

    private static boolean cloudLifted = false;

    /** Head of renderClouds: lift the whole pass by the configured percentage. */
    public static void beforeClouds(Object renderGlobal)
    {
        int pct = cloudLiftPercent(VertexConfig.value("cloudHeight", "0"));

        if (pct > 0)
        {
            GL11.glPushMatrix();
            GL11.glTranslatef(0.0F, cloudLiftBlocks(pct), 0.0F);
            cloudLifted = true;
        }
    }

    /** Tail of renderClouds: unwind the lift. */
    public static void afterClouds(Object renderGlobal)
    {
        if (cloudLifted)
        {
            GL11.glPopMatrix();
            cloudLifted = false;
        }
    }

    // ---- depth fog -----------------------------------------------------------------

    /**
     * Return adjuster on WorldProvider.getVoidFogYFactor. The fog-color pass multiplies
     * the fog color by ((eyeY) * factor)^2 whenever the product is below 1; a large
     * factor keeps the product above 1 from bedrock up, so the darkening never engages
     * while blindness (which scales the product down separately) keeps working.
     */
    public static double voidFogFactor(double vanilla)
    {
        return adjustVoidFogFactor(vanilla, VertexConfig.enabled("depthFog"));
    }

    // ---- pure decision logic (unit-tested) -------------------------------------------

    static boolean celestialTexture(String location)
    {
        return location != null && (location.contains("sun.png") || location.contains("moon_phases"));
    }

    static boolean suppressCallList(int list, int starListId, boolean starsEnabled)
    {
        return list == starListId && !starsEnabled;
    }

    static double adjustVoidFogFactor(double vanilla, boolean depthFogEnabled)
    {
        return depthFogEnabled ? vanilla : 16.0D;
    }

    /** Parses the cloudHeight key: one of 0/25/50/75/100, anything else resolves 0. */
    static int cloudLiftPercent(String raw)
    {
        if (raw == null)
        {
            return 0;
        }

        String trimmed = raw.trim();
        return trimmed.equals("25") ? 25 : trimmed.equals("50") ? 50
            : trimmed.equals("75") ? 75 : trimmed.equals("100") ? 100 : 0;
    }

    /** Vanilla clouds sit at y=108; 100% lifts them near the build ceiling. */
    static float cloudLiftBlocks(int pct)
    {
        return pct * 1.48F;
    }

    // ---- plumbing --------------------------------------------------------------------

    private static int starList()
    {
        Object renderGlobal = VertexSkyBridge.current();

        if (renderGlobal == null)
        {
            return Integer.MIN_VALUE;
        }

        try
        {
            if (starListField == null || starListOwner != renderGlobal.getClass())
            {
                starListField = renderGlobal.getClass().getDeclaredField(Mappings.RG_STAR_LIST);
                starListField.setAccessible(true);
                starListOwner = renderGlobal.getClass();
            }

            if (starList == Integer.MIN_VALUE)
            {
                starList = starListField.getInt(renderGlobal);
            }

            return starList;
        }
        catch (Throwable t)
        {
            disable("starList", t);
            return Integer.MIN_VALUE;
        }
    }

    private static int fallbackDraw(Object tessellator)
    {
        try
        {
            if (tessDraw != null)
            {
                return ((Integer)tessDraw.invoke(tessellator)).intValue();
            }
        }
        catch (Throwable ignored)
        {
            // The disable below already logged; a dropped flush costs one sky element.
        }

        return 0;
    }

    private static void disable(String where, Throwable t)
    {
        if (!disabled)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Sky detail hooks disabled after failure in " + where);
            t.printStackTrace();
        }
    }

    private VertexSkyDetails()
    {
    }
}
