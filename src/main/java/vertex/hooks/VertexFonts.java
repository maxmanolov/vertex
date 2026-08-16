package vertex.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Custom Fonts: resource packs following the MCPatcher/OptiFine convention ship HD
 * fonts at mcpatcher/font/..., a path vanilla never consults. With customFonts=true
 * every FontRenderer read of its font-texture location reroutes here; when the active
 * packs actually provide the mcpatcher counterpart, the reads (texture load and every
 * per-draw bind alike) resolve to it. Vanilla's own reader derives the glyph grid and
 * widths from the real image size, so HD fonts need no extra math.
 *
 * The decision is cached per FontRenderer instance and invalidated on each font
 * reload; a menu flip re-runs the vanilla reload listener so the swap is live both
 * ways. Unicode pages and font.properties stay on the vanilla path. Both vanilla
 * fonts (ascii and the enchantment table's ascii_sga) follow the same rule.
 */
public final class VertexFonts
{
    private static boolean disabled = false;

    private static Field locationField;
    private static Method getMinecraft;
    private static Method getResourceManager;
    private static Method getResource;
    private static Method fontReload;
    private static Constructor<?> locationCtor;

    /** FontRenderer -> resolved location (the override or the vanilla one). */
    private static final Map<Object, Object> resolved = new IdentityHashMap<Object, Object>();
    /** Every FontRenderer that ever read its location, for menu-flip reloads. */
    private static final Map<Object, Object> seen = new IdentityHashMap<Object, Object>();
    private static boolean overrideLogged = false;

    /** Class-wide reroute of FontRenderer's font-location reads. */
    public static Object fontLocation(Object fontRenderer)
    {
        try
        {
            if (locationField == null)
            {
                locationField = fontRenderer.getClass().getDeclaredField(Mappings.FR_FONT_LOCATION);
                locationField.setAccessible(true);
            }

            Object vanilla = locationField.get(fontRenderer);
            seen.put(fontRenderer, fontRenderer);

            if (disabled || !VertexConfig.enabled("customFonts"))
            {
                return vanilla;
            }

            Object cached = resolved.get(fontRenderer);

            if (cached != null)
            {
                return cached;
            }

            Object answer = probe(vanilla);
            resolved.put(fontRenderer, answer);
            return answer;
        }
        catch (Throwable t)
        {
            disable(t);

            try
            {
                return locationField.get(fontRenderer);
            }
            catch (Throwable unrecoverable)
            {
                return null; // unreachable in practice: the field resolved above
            }
        }
    }

    /** Head of FontRenderer.onResourceManagerReload: packs changed, decide afresh. */
    public static void onFontReload(Object fontRenderer)
    {
        resolved.remove(fontRenderer);
    }

    /** Menu flip: re-run each known font's own reload so the swap applies now. */
    static void applyFromMenu()
    {
        if (disabled || seen.isEmpty())
        {
            return;
        }

        try
        {
            resolveManagerHandles();
            Object manager = getResourceManager.invoke(getMinecraft.invoke(null));
            Object[] fonts = seen.keySet().toArray();

            for (Object font : fonts)
            {
                if (fontReload == null)
                {
                    Class<?> managerIface = getResourceManager.getReturnType();
                    fontReload = font.getClass().getMethod(Mappings.FR_RELOAD, managerIface);
                    fontReload.setAccessible(true);
                }

                resolved.remove(font);
                fontReload.invoke(font, manager);
            }
        }
        catch (Throwable t)
        {
            disable(t);
        }
    }

    /** The pack's mcpatcher location when it exists, the vanilla location otherwise. */
    private static Object probe(Object vanillaLocation) throws Exception
    {
        String candidatePath = overridePath(String.valueOf(vanillaLocation));

        if (candidatePath == null)
        {
            return vanillaLocation;
        }

        resolveManagerHandles();
        Object candidate = locationCtor.newInstance(candidatePath);

        try
        {
            getResource.invoke(getResourceManager.invoke(getMinecraft.invoke(null)), candidate);
        }
        catch (Throwable missing)
        {
            return vanillaLocation; // no pack provides it
        }

        if (!overrideLogged)
        {
            overrideLogged = true;
            LogWrapper.info("[Vertex] Custom font loaded from " + candidatePath);
        }

        return candidate;
    }

    // ---- pure decision logic (unit-tested) -----------------------------------------------

    /**
     * Maps a vanilla font location ("domain:textures/font/NAME") to the MCPatcher
     * convention path ("mcpatcher/font/NAME"); null when the location is not a font
     * texture (leave such reads alone).
     */
    static String overridePath(String vanillaLocation)
    {
        String text = vanillaLocation == null ? "" : vanillaLocation;
        int colon = text.indexOf(':');
        String path = colon < 0 ? text : text.substring(colon + 1);

        if (!path.startsWith("textures/font/") || path.length() == "textures/font/".length())
        {
            return null;
        }

        return "mcpatcher/font/" + path.substring("textures/font/".length());
    }

    // ---- plumbing -----------------------------------------------------------------------

    private static void resolveManagerHandles() throws Exception
    {
        if (getResource != null)
        {
            return;
        }

        Class<?> minecraft = net.minecraft.launchwrapper.Launch.classLoader
            .loadClass(Mappings.MINECRAFT);
        getMinecraft = minecraft.getMethod(Mappings.MINECRAFT_GET_MINECRAFT);
        getMinecraft.setAccessible(true);
        getResourceManager = minecraft.getMethod(Mappings.MC_GET_RESOURCE_MANAGER);
        getResourceManager.setAccessible(true);

        Class<?> location = net.minecraft.launchwrapper.Launch.classLoader
            .loadClass(Mappings.RESOURCE_LOCATION);
        locationCtor = location.getConstructor(String.class);
        getResource = getResourceManager.getReturnType()
            .getMethod(Mappings.RM_GET_RESOURCE, location);
        getResource.setAccessible(true);
    }

    private static void disable(Throwable t)
    {
        if (!disabled)
        {
            disabled = true;
            resolved.clear();
            LogWrapper.severe("[Vertex] Custom fonts disabled after failure");
            t.printStackTrace();
        }
    }

    /** Test seam. */
    static void resetForTest()
    {
        resolved.clear();
        seen.clear();
        overrideLogged = false;
    }

    private VertexFonts()
    {
    }
}
