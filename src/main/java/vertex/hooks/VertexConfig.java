package vertex.hooks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LogWrapper;

/**
 * vertex.properties in the game directory, hot-reloaded: hooks poll {@link #enabled} every
 * frame, and the file's mtime is checked at most once per second, so edits apply in-game
 * within a second without a restart. Every key defaults to vanilla behavior (true = the
 * feature renders as vanilla does; false = Vertex skips it). Any parse failure falls back
 * to defaults - configuration must never take the game down.
 */
public final class VertexConfig
{
    private static final String[][] KEYS = {
        {"sky", "true", "Render the sky pass (sky color, stars, sun and moon)"},
        {"clouds", "true", "Render clouds"},
        {"weather", "true", "Render rain and snow, and spawn rain splash particles"},
        {"voidParticles", "true", "Spawn void fog depth particles"},
        {"textureAnimations", "true", "Animate block textures (water, lava, fire, portals)"},
        {"fog", "true", "Render distance fog (lava, water and blindness fog always stay)"},
        {"interactiveRenderPriority", "true", "Rebuild the chunk section you just edited ahead of the update queue"},
        {"dynamicLights", "true", "Dynamic light sources illuminate surroundings"},
        {"customColors", "true", "Resource-pack custom colors (grass and foliage colormaps)"},
        {"diagnostics", "false", "Log a Vertex activity summary once per minute"},
    };

    private static final Properties values = new Properties();
    private static File file;
    private static long lastCheck = 0L;
    private static long lastModified = -1L;

    public static synchronized boolean enabled(String key)
    {
        refresh();
        String value = values.getProperty(key);
        return value == null || !value.trim().equalsIgnoreCase("false");
    }

    /** Inverse convenience for the skip patches: true means "suppress this render pass". */
    public static boolean skip(String key)
    {
        return !enabled(key);
    }

    private static void refresh()
    {
        long now = System.currentTimeMillis();

        if (now - lastCheck < 1000L)
        {
            return;
        }

        lastCheck = now;

        try
        {
            if (file == null)
            {
                if (Launch.minecraftHome == null)
                {
                    // No game directory (unit tests, tooling): defaults only, never write files.
                    return;
                }

                file = new File(Launch.minecraftHome, "vertex.properties");
            }

            if (!file.isFile())
            {
                writeDefaults();
            }

            long modified = file.lastModified();

            if (modified != lastModified)
            {
                lastModified = modified;
                Properties loaded = new Properties();
                FileInputStream in = new FileInputStream(file);

                try
                {
                    loaded.load(in);
                }
                finally
                {
                    in.close();
                }

                values.clear();
                values.putAll(loaded);
                LogWrapper.info("[Vertex] Loaded configuration from " + file.getName());
            }
        }
        catch (Exception e)
        {
            LogWrapper.warning("[Vertex] Could not read vertex.properties, using defaults: " + e);
        }
    }

    private static void writeDefaults() throws Exception
    {
        Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);

        try
        {
            out.write("# Vertex configuration. true = vanilla behavior, false = skip for performance.\n");
            out.write("# Edits apply in-game within about a second; no restart needed.\n\n");

            for (String[] key : KEYS)
            {
                out.write("# " + key[2] + "\n");
                out.write(key[0] + "=" + key[1] + "\n\n");
            }
        }
        finally
        {
            out.close();
        }
    }

    private VertexConfig()
    {
    }
}
