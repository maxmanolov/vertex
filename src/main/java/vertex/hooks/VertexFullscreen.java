package vertex.hooks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Fullscreen Mode: vanilla's toggle always adopts the desktop display mode (with a
 * macOS half-resolution fallback). With fullscreenMode=WxH the head guard on that
 * chooser sets the selected fullscreen-capable mode instead and skips the vanilla
 * body; toggleFullscreen re-reads the live mode for its resize immediately after,
 * so the rest of the vanilla flow adopts the override untouched. "default" keeps
 * the vanilla path bit for bit. A change applies on the next fullscreen switch.
 */
public final class VertexFullscreen
{
    private static String warnedValue = null;
    private static List<String> cachedLabels = null;

    /** Head guard on the fullscreen mode chooser: true = mode set here, skip vanilla. */
    public static boolean overrideFullscreenMode(Object minecraft)
    {
        String value = VertexConfig.value("fullscreenMode", "default");
        int[] parsed = parseMode(value);

        if (parsed == null)
        {
            return false; // default (or malformed, which value validation rejects anyway)
        }

        try
        {
            DisplayMode[] available = Display.getAvailableDisplayModes();
            int index = chooseIndex(rowsFrom(available), parsed[0], parsed[1]);

            if (index < 0)
            {
                warnOnce(value, "is not among the available display modes");
                return false;
            }

            DisplayMode chosen = available[index];

            Display.setDisplayMode(chosen);
            LogWrapper.info("[Vertex] Fullscreen mode " + chosen.getWidth() + "x"
                + chosen.getHeight() + " applied");
            return true;
        }
        catch (Throwable t)
        {
            warnOnce(value, "failed to apply: " + t);
            return false;
        }
    }

    /** Menu cycle: default -> each available mode ascending -> default. */
    public static String nextMode(String current)
    {
        return nextIn(availableModeLabels(), current);
    }

    /** The deduped WxH labels, smallest first; enumerated once per session. */
    static List<String> availableModeLabels()
    {
        if (cachedLabels == null)
        {
            try
            {
                cachedLabels = labelsFrom(Display.getAvailableDisplayModes());
            }
            catch (Throwable t)
            {
                LogWrapper.severe("[Vertex] Display mode enumeration failed");
                t.printStackTrace();
                cachedLabels = new ArrayList<String>();
            }
        }

        return cachedLabels;
    }

    // ---- pure decision logic (unit-tested) -----------------------------------------------

    /** "WxH" for a real resolution; null for "default", malformed or non-positive input. */
    static int[] parseMode(String value)
    {
        String trimmed = value == null ? "" : value.trim();
        int split = trimmed.indexOf('x');

        if (split <= 0 || split != trimmed.lastIndexOf('x') || split == trimmed.length() - 1)
        {
            return null;
        }

        try
        {
            int width = Integer.parseInt(trimmed.substring(0, split));
            int height = Integer.parseInt(trimmed.substring(split + 1));
            return width > 0 && height > 0 ? new int[] {width, height} : null;
        }
        catch (NumberFormatException malformed)
        {
            return null;
        }
    }

    /**
     * The exact-resolution match with the highest color depth, then refresh rate;
     * -1 when the resolution is not offered. Rows are {width, height, bpp, frequency}.
     */
    static int chooseIndex(int[][] rows, int width, int height)
    {
        int best = -1;

        for (int i = 0; i < rows.length; ++i)
        {
            if (rows[i][0] != width || rows[i][1] != height)
            {
                continue;
            }

            if (best < 0
                || rows[i][2] > rows[best][2]
                || (rows[i][2] == rows[best][2] && rows[i][3] > rows[best][3]))
            {
                best = i;
            }
        }

        return best;
    }

    static int[][] rowsFrom(DisplayMode[] available)
    {
        int[][] rows = new int[available.length][];

        for (int i = 0; i < available.length; ++i)
        {
            rows[i] = new int[] {available[i].getWidth(), available[i].getHeight(),
                available[i].getBitsPerPixel(), available[i].getFrequency()};
        }

        return rows;
    }

    /** Distinct WxH labels sorted by area, then width. */
    static List<String> labelsFrom(DisplayMode[] available)
    {
        List<int[]> sizes = new ArrayList<int[]>();

        for (DisplayMode mode : available)
        {
            boolean seen = false;

            for (int[] size : sizes)
            {
                if (size[0] == mode.getWidth() && size[1] == mode.getHeight())
                {
                    seen = true;
                    break;
                }
            }

            if (!seen)
            {
                sizes.add(new int[] {mode.getWidth(), mode.getHeight()});
            }
        }

        for (int i = 1; i < sizes.size(); ++i)
        {
            for (int j = i; j > 0 && smaller(sizes.get(j), sizes.get(j - 1)); --j)
            {
                int[] swap = sizes.get(j);
                sizes.set(j, sizes.get(j - 1));
                sizes.set(j - 1, swap);
            }
        }

        List<String> labels = new ArrayList<String>();

        for (int[] size : sizes)
        {
            labels.add(size[0] + "x" + size[1]);
        }

        return labels;
    }

    /** Cycle default -> labels in order -> default; unknown values restart at default. */
    static String nextIn(List<String> labels, String current)
    {
        String trimmed = current == null ? "" : current.trim();

        if (trimmed.equals("default"))
        {
            return labels.isEmpty() ? "default" : labels.get(0);
        }

        int index = labels.indexOf(trimmed);

        if (index < 0 || index == labels.size() - 1)
        {
            return "default";
        }

        return labels.get(index + 1);
    }

    private static boolean smaller(int[] a, int[] b)
    {
        long areaA = (long)a[0] * a[1];
        long areaB = (long)b[0] * b[1];
        return areaA != areaB ? areaA < areaB : a[0] < b[0];
    }

    private static void warnOnce(String value, String why)
    {
        if (!value.equals(warnedValue))
        {
            warnedValue = value;
            LogWrapper.info("[Vertex] Fullscreen mode " + value + " " + why
                + "; using the desktop mode");
        }
    }

    /** Test seam: reset the enumeration cache. */
    static void resetForTest()
    {
        cachedLabels = null;
        warnedValue = null;
    }

    private VertexFullscreen()
    {
    }
}
