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
        {"betterGrass", "false", "Render grass block sides as grass when the terrain continues below"},
        {"naturalTextures", "true", "Mirror tile variants from a pack's natural.properties"},
        {"randomEntities", "true", "Per-mob texture variants when a pack supplies numbered siblings"},
        {"customSky", "true", "Draw pack-defined custom sky layers"},
        {"connectedTextures", "true", "Connected textures from a pack's mcpatcher/ctm rules"},
        {"multicore", "true", "Build chunk geometry on CPU worker threads (restart required)"},
        {"renderer", "legacy", "Terrain renderer backend: legacy (vanilla display lists), displaylist (managed section-mesh pipeline, same visuals), vbo (per-section vertex buffers), or arena (shared buffers with batched submission); restart required"},
        {"fullbright", "false", "Render everything at full brightness and skip light-triggered chunk rebuilds"},
        {"chatBackground", "true", "Draw the translucent background behind chat lines"},
        {"scoreboardBackground", "true", "Draw the translucent background behind the scoreboard sidebar"},
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

        if (value == null)
        {
            // A missing key means its declared default, never a blanket true: default-off
            // features (betterGrass, diagnostics) must not switch on because a file is
            // hand-written, predates the key, or failed to parse (kyrofx #28). Undeclared
            // keys resolve to false so a typo can never enable anything.
            return declaredDefault(key);
        }

        String normalized = value.trim();

        if (normalized.equalsIgnoreCase("true"))
        {
            return true;
        }

        if (normalized.equalsIgnoreCase("false"))
        {
            return false;
        }

        // Invalid text must not enable an option by accident. Use the same declared
        // default as an absent value until the user corrects the file (#85).
        return declaredDefault(key);
    }

    private static boolean declaredDefault(String key)
    {
        for (String[] entry : KEYS)
        {
            if (entry[0].equals(key))
            {
                return entry[1].equals("true");
            }
        }

        return false;
    }

    /** Inverse convenience for the skip patches: true means "suppress this render pass". */
    public static boolean skip(String key)
    {
        return !enabled(key);
    }

    /**
     * Free-form string keys (e.g. the renderer backend selector). A missing or blank
     * value resolves to the key's declared default, falling back to the caller's default
     * for undeclared keys - the same never-enable-by-accident posture as enabled().
     */
    public static synchronized String value(String key, String fallback)
    {
        refresh();
        String value = values.getProperty(key);

        if (value == null || value.trim().isEmpty())
        {
            for (String[] entry : KEYS)
            {
                if (entry[0].equals(key))
                {
                    return entry[1];
                }
            }

            return fallback;
        }

        return value.trim();
    }

    /**
     * In-game toggle support: set one key and rewrite the file in the same commented
     * format, carrying over current values and preserving keys Vertex doesn't declare.
     * lastModified is re-read after the write so our own save doesn't trigger a reload.
     */
    public static synchronized void setAndSave(String key, boolean value)
    {
        refresh();
        values.setProperty(key, String.valueOf(value));

        if (file == null)
        {
            return;
        }

        try
        {
            writeCurrent();
            lastModified = file.lastModified();
            LogWrapper.info("[Vertex] Saved " + key + "=" + value + " to " + file.getName());
        }
        catch (Exception e)
        {
            // The in-memory toggle already applied; a failed save only loses persistence.
            LogWrapper.warning("[Vertex] Could not save vertex.properties: " + e);
        }
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
            // The log promises declared defaults. Do not leave the last valid file's
            // values active after a malformed hot reload (#101). lastModified already
            // records this file version, so unchanged bad content logs only once.
            values.clear();
            LogWrapper.warning("[Vertex] Could not read vertex.properties, using defaults: " + e);
        }
    }

    private static void writeDefaults() throws Exception
    {
        values.clear();
        writeCurrent();
    }

    private static void writeCurrent() throws Exception
    {
        Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);

        try
        {
            out.write("# Vertex configuration. true = vanilla behavior, false = skip for performance.\n");
            out.write("# Edits apply in-game within about a second; no restart needed.\n\n");

            for (String[] key : KEYS)
            {
                String value = values.getProperty(key[0], key[1]).trim();
                out.write("# " + key[2] + "\n");
                out.write(key[0] + "=" + value + "\n\n");
            }

            // Keys we don't declare (hand-added or from a newer/older version) survive saves.
            for (String name : values.stringPropertyNames())
            {
                if (!declaresKey(name))
                {
                    out.write(name + "=" + values.getProperty(name) + "\n");
                }
            }
        }
        finally
        {
            out.close();
        }
    }

    private static boolean declaresKey(String name)
    {
        for (String[] entry : KEYS)
        {
            if (entry[0].equals(name))
            {
                return true;
            }
        }

        return false;
    }

    private VertexConfig()
    {
    }
}
