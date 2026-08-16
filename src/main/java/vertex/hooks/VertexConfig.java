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
        {"sunMoon", "true", "Draw the sun and moon (the rest of the sky pass is unaffected)"},
        {"stars", "true", "Draw the night-sky stars"},
        {"depthFog", "true", "Darken the fog color toward black near bedrock depths"},
        {"fogStart", "default", "Linear fog start as a fraction of the fog end: default (vanilla), 0.2, 0.4, 0.6 or 0.8"},
        {"cloudHeight", "0", "Lift clouds above their vanilla height by 0, 25, 50, 75 or 100 percent of the remaining sky"},
        {"trees", "default", "Leaf rendering: default (follow Graphics), fast (opaque) or fancy (transparent)"},
        {"droppedItems", "default", "Dropped item rendering: default (follow Graphics), fast (flat) or fancy (3D)"},
        {"aoLevel", "100", "Smooth lighting corner darkening: 0 (none), 50 or 100 percent"},
        {"dynamicFov", "true", "Widen the field of view while sprinting, flying or under speed effects"},
        {"showFps", "false", "Draw a small FPS readout in the top-left corner"},
        {"lagometer", "false", "Draw a frame-time graph under the FPS readout"},
        {"debugProfiler", "true", "Collect and show the F3+Shift pie chart profiler"},
        {"autosave", "45", "Integrated-server autosave interval in seconds: 45 (vanilla), 180 or 1800"},
        {"timeOverride", "default", "Client-visual time of day: default, day or night (gameplay time unaffected)"},
        {"chunkUpdates", "4", "Finished chunk builds applied per frame: 1 (smoothest) to 5 (fastest world loading)"},
        {"dynamicUpdates", "false", "Triple the chunk update budget while the player stands still"},
        {"smoothFps", "false", "Drain the GPU driver queue every frame to even out frame times"},
        {"fastMath", "false", "Use a small cache-resident trig table (restart required)"},
        {"mipmapType", "nearest", "Terrain atlas minification: nearest (vanilla) or linear (smoother distant texel blend)"},
        {"swampColors", "true", "Use the swamp biome's special grass and foliage tint instead of the standard colormap"},
        {"clouds", "true", "Render clouds"},
        {"weather", "true", "Render rain and snow, and spawn rain splash particles"},
        {"voidParticles", "true", "Spawn void fog depth particles"},
        {"textureAnimations", "true", "Master switch for all texture animations (terrain and items)"},
        {"terrainAnimated", "true", "Animate terrain-atlas textures (water, lava, fire, portals, ...)"},
        {"itemsAnimated", "true", "Animate item textures (compass needle, clock dial)"},
        {"animWater", "true", "Animate the water textures"},
        {"animLava", "true", "Animate the lava textures"},
        {"animFire", "true", "Animate the fire textures"},
        {"animPortal", "true", "Animate the nether portal texture"},
        {"particleExplosions", "true", "Spawn explosion particles"},
        {"particleSmoke", "true", "Spawn smoke particles (torches, fires, furnaces)"},
        {"particlePortal", "true", "Spawn nether portal ambience particles"},
        {"particleFlame", "true", "Spawn flame particles (torches, mob spawners)"},
        {"particleWater", "true", "Spawn water splash and bubble particles"},
        {"particleDripping", "true", "Spawn dripping water and lava particles"},
        {"particlePotion", "true", "Spawn potion and spell particles"},
        {"particleRainSplash", "true", "Spawn rain splash particles on the ground (also requires weather)"},
        {"fog", "true", "Render distance fog (lava, water and blindness fog always stay)"},
        {"interactiveRenderPriority", "true", "Rebuild the chunk section you just edited ahead of the update queue"},
        {"dynamicLights", "true", "Dynamic light sources illuminate surroundings"},
        {"customColors", "true", "Resource-pack custom colors (grass and foliage colormaps)"},
        {"betterGrass", "false", "Render grass block sides as grass when the terrain continues below"},
        {"betterSnow", "false", "Draw a snow layer under fences, walls and plants next to snow"},
        {"naturalTextures", "true", "Mirror tile variants from a pack's natural.properties"},
        {"randomEntities", "true", "Per-mob texture variants when a pack supplies numbered siblings"},
        {"customSky", "true", "Draw pack-defined custom sky layers"},
        {"connectedTextures", "true", "Connected textures from a pack's mcpatcher/ctm rules"},
        {"multicore", "true", "Build chunk geometry on CPU worker threads (restart required)"},
        {"renderer", "arena", "Terrain renderer backend: arena (default when this key is missing; shared buffers with batched submission), vbo (per-section vertex buffers), displaylist (managed section-mesh pipeline, vanilla visuals), or legacy (the untouched vanilla display-list path); restart required"},
        {"fullbright", "false", "Render everything at full brightness and skip light-triggered chunk rebuilds"},
        {"freelook", "true", "Hold the Freelook key (rebindable in Controls, default Left Alt) to orbit the camera without turning your character"},
        {"toggleSprint", "false", "Tap the sprint key to toggle continuous sprinting instead of holding it"},
        {"chatBackground", "true", "Draw the translucent background behind chat lines"},
        {"scoreboardBackground", "true", "Draw the translucent background behind the scoreboard sidebar"},
        {"diagnostics", "false", "Log a Vertex activity summary once per minute"},
    };

    private static final Properties values = new Properties();
    private static File file;
    private static volatile long lastCheck = 0L;
    private static long lastModified = -1L;

    /**
     * Published read-only copy of {@link #values}: the hot readers (render thread per
     * frame, worker threads per icon lookup during tessellation) take no lock and touch
     * no Hashtable synchronization - each read is one volatile load of the latest
     * fully-built map. Every mutation path republishes it before returning.
     */
    private static volatile java.util.HashMap<String, String> snapshot =
        new java.util.HashMap<String, String>();

    public static boolean enabled(String key)
    {
        maybeRefresh();
        String value = snapshot.get(key);

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

    /** Package-visible for the video menu's Reset button; unknown keys resolve false. */
    static boolean declaredDefault(String key)
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
    public static String value(String key, String fallback)
    {
        maybeRefresh();
        String value = snapshot.get(key);

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

    /** Nesting depth of open bulk-save scopes; while positive, saves coalesce. */
    private static int bulkDepth = 0;
    private static boolean bulkDirty = false;
    private static int bulkKeys = 0;

    /**
     * Coalesces the writes of a multi-key update (the menu's Reset and All ON/OFF flip
     * dozens of keys per click): saves inside a bulk scope apply in memory immediately
     * but the file writes once, at the close of the outermost scope, with one log line.
     */
    public static synchronized void beginBulkSave()
    {
        ++bulkDepth;
    }

    public static synchronized void endBulkSave()
    {
        if (bulkDepth > 0 && --bulkDepth == 0 && bulkDirty)
        {
            bulkDirty = false;
            int flushed = bulkKeys;
            bulkKeys = 0;
            persist(flushed + " keys");
        }
    }

    /**
     * In-game toggle support: set one key and rewrite the file in the same commented
     * format, carrying over current values and preserving keys Vertex doesn't declare.
     * String-keyed variant for the non-boolean options (renderer, fogStart, ...).
     */
    public static synchronized void setAndSaveValue(String key, String value)
    {
        refresh();
        values.setProperty(key, value);
        publish();

        if (bulkDepth > 0)
        {
            bulkDirty = true;
            ++bulkKeys;
            return;
        }

        persist(key + "=" + value);
    }

    /** Rebuilds and republishes the lock-free read copy; call with the lock held. */
    private static void publish()
    {
        java.util.HashMap<String, String> copy = new java.util.HashMap<String, String>();

        for (String name : values.stringPropertyNames())
        {
            copy.put(name, values.getProperty(name));
        }

        snapshot = copy;
    }

    public static synchronized void setAndSave(String key, boolean value)
    {
        setAndSaveValue(key, String.valueOf(value));
    }

    /**
     * Rewrites the file with the current values. lastModified is re-read after the
     * write so our own save doesn't trigger a reload.
     */
    private static void persist(String what)
    {
        if (file == null)
        {
            return;
        }

        try
        {
            writeCurrent();
            lastModified = file.lastModified();
            LogWrapper.info("[Vertex] Saved " + what + " to " + file.getName());
        }
        catch (Exception e)
        {
            // The in-memory change already applied; a failed save only loses persistence.
            LogWrapper.warning("[Vertex] Could not save vertex.properties: " + e);
        }
    }

    /** Lock-free fast path: one volatile read per call between the 1s re-checks. */
    private static void maybeRefresh()
    {
        if (System.currentTimeMillis() - lastCheck < 1000L)
        {
            return;
        }

        synchronized (VertexConfig.class)
        {
            refresh();
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
                publish();
                LogWrapper.info("[Vertex] Loaded configuration from " + file.getName());
            }
        }
        catch (Exception e)
        {
            // The log promises declared defaults. Do not leave the last valid file's
            // values active after a malformed hot reload (#101). lastModified already
            // records this file version, so unchanged bad content logs only once.
            values.clear();
            publish();
            LogWrapper.warning("[Vertex] Could not read vertex.properties, using defaults: " + e);
        }
    }

    private static void writeDefaults() throws Exception
    {
        values.clear();
        writeCurrent();
    }

    /** Test seam: file rewrites performed since class load. */
    static int fileWritesForTest = 0;

    private static void writeCurrent() throws Exception
    {
        ++fileWritesForTest;
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
