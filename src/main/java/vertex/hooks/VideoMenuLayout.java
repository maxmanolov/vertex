package vertex.hooks;

import java.util.ArrayList;
import java.util.List;

/**
 * The six video-settings pages as pure data: the OptiFine 1.7.10 layout (reference
 * screenshots are the source of truth) with each slot naming the real functionality
 * behind it. Geometry is the classic OptiFine grid: two 150-wide columns at
 * width/2-155 and width/2+5, 20-tall rows on a 24 pitch from height/6-12, Done
 * centered at the vanilla bottom anchor. Slots without any real Vertex or vanilla
 * backing are STATIC: they keep the reference geometry and label but render disabled,
 * because a control that does nothing must look like a control that does nothing.
 *
 * This class touches no Minecraft types so the whole layout - inventory, ordering,
 * wiring kinds, ids and pixel math - is unit-testable; {@link VertexVideoMenu} turns
 * it into live widgets reflectively.
 */
final class VideoMenuLayout
{
    static final int PAGE_VIDEO = 0;
    static final int PAGE_DETAILS = 1;
    static final int PAGE_ANIMATIONS = 2;
    static final int PAGE_QUALITY = 3;
    static final int PAGE_PERFORMANCE = 4;
    static final int PAGE_OTHER = 5;
    static final int PAGE_COUNT = 6;

    /** A vanilla cycle option: ref = the bbm constant's field name, label from vanilla. */
    static final int KIND_VANILLA = 0;
    /** A vanilla float option rendered as a real GuiOptionSlider: ref = bbm field name. */
    static final int KIND_SLIDER = 1;
    /** A Vertex boolean config key: ref = the vertex.properties key, label = base text. */
    static final int KIND_VERTEX = 2;
    /** Navigation to a sub-page: ref = the page number as a string, id = 300 + page. */
    static final int KIND_NAV = 3;
    /** Reference slot with no real backing: disabled button, label taken verbatim. */
    static final int KIND_STATIC = 4;
    /** The fullbright toggle, sitting in the reference's Shaders... slot. */
    static final int KIND_FULLBRIGHT = 5;
    /** Chunk Loading: Default / Multi-Core, backed by the multicore key (restart). */
    static final int KIND_CHUNK_LOADING = 6;
    /** Held Item Tooltips: vanilla GameSettings boolean field (no Options enum entry). */
    static final int KIND_HELD_TOOLTIPS = 7;
    static final int KIND_ALL_ON = 8;
    static final int KIND_ALL_OFF = 9;
    static final int KIND_RESET = 10;
    static final int KIND_DONE = 11;
    /** Effective cloud toggle: vanilla GameSettings and Vertex's pass gate move together. */
    static final int KIND_CLOUDS = 12;

    static final int KIND_FOG_START = 13;

    static final int KIND_CLOUD_HEIGHT = 14;

    static final int KIND_TRISTATE = 15;

    static final int KIND_AO_LEVEL = 16;

    static final int KIND_AUTOSAVE = 17;

    static final int KIND_TIME = 18;

    static final int KIND_CHUNK_UPDATES = 19;

    static final int KIND_FAST_RENDER = 20;

    static final int KIND_MIPMAP_TYPE = 21;

    static final int KIND_ANTIALIAS = 22;

    static final int ID_DONE = 200;
    static final int ID_NAV_BASE = 300;
    static final int ID_SLOT_BASE = 400;

    /** One positioned widget: everything the glue needs to build and dispatch it. */
    static final class Placed
    {
        final int kind;
        final String label;
        final String ref;
        final int id;
        final int x;
        final int y;
        final int width;
        final int height;

        Placed(int kind, String label, String ref, int id, int x, int y, int width, int height)
        {
            this.kind = kind;
            this.label = label;
            this.ref = ref;
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /** Column-local slot description used while assembling a page. */
    private static final class Slot
    {
        final int kind;
        final String label;
        final String ref;

        Slot(int kind, String label, String ref)
        {
            this.kind = kind;
            this.label = label;
            this.ref = ref;
        }
    }

    static String title(int page)
    {
        switch (page)
        {
            case PAGE_DETAILS:
                return "Detail Settings";
            case PAGE_ANIMATIONS:
                return "Animation Settings";
            case PAGE_QUALITY:
                return "Quality Settings";
            case PAGE_PERFORMANCE:
                return "Performance Settings";
            case PAGE_OTHER:
                return "Other Settings";
            default:
                return "Video Settings";
        }
    }

    // ---- geometry (derived from the reference screenshots) -----------------------------

    static int columnX(int width, int column)
    {
        return width / 2 - 155 + column * 160;
    }

    static int rowY(int height, int row)
    {
        return height / 6 - 12 + row * 24;
    }

    /**
     * Vanilla's ScaledResolution permits a 240-pixel-tall GUI. Nine 20-pixel controls
     * on a fixed 24-pixel pitch plus the bottom row do not fit there, so compress only
     * the inter-row gaps as needed. Twenty is the lower bound: controls may touch but
     * can never overlap.
     */
    private static int rowPitch(int height, int gridRows)
    {
        if (gridRows <= 1)
        {
            return 24;
        }

        int top = rowY(height, 0);
        int lastGridTop = height - 22 - 4 - 20;
        int availablePitch = (lastGridTop - top) / (gridRows - 1);
        return Math.max(20, Math.min(24, availablePitch));
    }

    private static int rowY(int height, int row, int pitch)
    {
        return height / 6 - 12 + row * pitch;
    }

    /**
     * The reference anchors the bottom row to the GRID, not the screen bottom: Done
     * (or the All ON row) sits one small gap below the last grid row, and the Other
     * page leaves two empty rows before Reset. Compact layouts use the compressed pitch
     * above; the final clamp only protects dimensions below vanilla's supported minimum.
     */
    static int bottomRowY(int height, int gridRows)
    {
        int pitch = rowPitch(height, gridRows);
        return Math.min(rowY(height, gridRows, pitch) + 4, height - 22);
    }

    static int resetY(int height, int gridRows)
    {
        return Math.min(rowY(height, gridRows + 2), height - 54);
    }

    /** Builds the fully positioned page: grid slots, page-specific bottom row, Done. */
    static List<Placed> layout(int page, int width, int height)
    {
        List<Slot> left = new ArrayList<Slot>();
        List<Slot> right = new ArrayList<Slot>();
        fill(page, left, right);

        List<Placed> placed = new ArrayList<Placed>();
        int slotId = ID_SLOT_BASE;
        int gridRows = Math.max(left.size(), right.size());
        int pitch = rowPitch(height, gridRows);

        for (int column = 0; column < 2; ++column)
        {
            List<Slot> slots = column == 0 ? left : right;

            for (int row = 0; row < slots.size(); ++row)
            {
                Slot slot = slots.get(row);
                int id = slot.kind == KIND_NAV
                    ? ID_NAV_BASE + Integer.parseInt(slot.ref) : slotId++;
                placed.add(new Placed(slot.kind, slot.label, slot.ref, id,
                    columnX(width, column), rowY(height, row, pitch), 150, 20));
            }
        }

        if (page == PAGE_ANIMATIONS)
        {
            // Reference bottom row: two 73-wide halves of the left column plus a
            // right-column-sized Done, one row gap under the grid.
            int y = bottomRowY(height, gridRows);
            placed.add(new Placed(KIND_ALL_ON, "All ON", null, slotId++,
                width / 2 - 155, y, 73, 20));
            placed.add(new Placed(KIND_ALL_OFF, "All OFF", null, slotId++,
                width / 2 - 78, y, 73, 20));
            placed.add(new Placed(KIND_DONE, "Done", null, ID_DONE,
                width / 2 + 5, y, 150, 20));
        }
        else if (page == PAGE_OTHER)
        {
            int reset = resetY(height, gridRows);
            placed.add(new Placed(KIND_RESET, "Reset Video Settings...", null, slotId++,
                width / 2 - 100, reset, 200, 20));
            placed.add(new Placed(KIND_DONE, "Done", null, ID_DONE,
                width / 2 - 100, reset + 32, 200, 20));
        }
        else
        {
            placed.add(new Placed(KIND_DONE, "Done", null, ID_DONE,
                width / 2 - 100, bottomRowY(height, gridRows), 200, 20));
        }

        return placed;
    }

    // ---- page inventories (transcribed from the reference screenshots) -----------------

    private static void fill(int page, List<Slot> left, List<Slot> right)
    {
        switch (page)
        {
            case PAGE_DETAILS:
                left.add(new Slot(KIND_CLOUDS, "Clouds", "clouds"));
                left.add(new Slot(KIND_TRISTATE, "Trees", "trees"));
                left.add(fixed("Water: Fast"));
                left.add(vertex("Sky", "sky"));
                left.add(vertex("Sun & Moon", "sunMoon"));
                left.add(vertex("Fog", "fog"));
                left.add(vertex("Depth Fog", "depthFog"));
                left.add(fixed("Translucent Blocks: Fast"));
                left.add(fixed("Vignette: Fast"));
                right.add(new Slot(KIND_CLOUD_HEIGHT, "Cloud Height", "cloudHeight"));
                right.add(new Slot(KIND_TRISTATE, "Grass", "grass"));
                right.add(vertex("Rain & Snow", "weather"));
                right.add(vertex("Stars", "stars"));
                right.add(vanilla("z"));                                 // Show Capes
                right.add(new Slot(KIND_FOG_START, "Fog Start", "fogStart"));
                right.add(new Slot(KIND_HELD_TOOLTIPS, "Held Item Tooltips", null));
                right.add(new Slot(KIND_TRISTATE, "Dropped Items", "droppedItems"));
                return;
            case PAGE_ANIMATIONS:
                left.add(vertex("Water Animated", "animWater"));
                left.add(vertex("Fire Animated", "animFire"));
                left.add(fixed("Redstone Animated: §aON"));              // no such 1.7.10 animation
                left.add(vertex("Flame Animated", "particleFlame"));
                left.add(vertex("Void Particles", "voidParticles"));
                left.add(vertex("Rain Splash", "particleRainSplash"));
                left.add(vertex("Potion Particles", "particlePotion"));
                left.add(vertex("Terrain Animated", "terrainAnimated"));
                left.add(vertex("Textures Animated", "textureAnimations"));
                right.add(vertex("Lava Animated", "animLava"));
                right.add(vertex("Portal Animated", "animPortal"));
                right.add(vertex("Explosion Animated", "particleExplosions"));
                right.add(vertex("Smoke Animated", "particleSmoke"));
                right.add(vertex("Water Particles", "particleWater"));
                right.add(vertex("Portal Particles", "particlePortal"));
                right.add(vertex("Dripping Water/Lava", "particleDripping"));
                right.add(vertex("Items Animated", "itemsAnimated"));
                right.add(vanilla("q"));                                 // Particles
                return;
            case PAGE_QUALITY:
                left.add(slider("F"));                                   // Mipmap Levels
                left.add(slider("G"));                                   // Anisotropic Filtering
                left.add(fixed("Clear Water: §cOFF"));                   // tracked enhancement
                left.add(vertex("Better Grass", "betterGrass"));
                left.add(fixed("Custom Fonts: §cOFF"));                  // tracked enhancement
                left.add(vertex("Swamp Colors", "swampColors"));
                left.add(vertex("Connected Textures", "connectedTextures"));
                left.add(vertex("Custom Sky", "customSky"));
                right.add(new Slot(KIND_MIPMAP_TYPE, "Mipmap Type", "mipmapType"));
                right.add(new Slot(KIND_ANTIALIAS, "Antialiasing", "antialiasing"));
                right.add(vertex("Random Mobs", "randomEntities"));
                right.add(vertex("Better Snow", "betterSnow"));
                right.add(vertex("Custom Colors", "customColors"));
                right.add(vertex("Smooth Biomes", "smoothBiomes"));
                right.add(vertex("Natural Textures", "naturalTextures"));
                return;
            case PAGE_PERFORMANCE:
                left.add(vertex("Smooth FPS", "smoothFps"));
                left.add(fixed("Load Far: §cOFF"));                      // no 1.7.10 behavior
                left.add(new Slot(KIND_CHUNK_UPDATES, "Chunk Updates", "chunkUpdates"));
                left.add(vertex("Fast Math", "fastMath"));
                left.add(new Slot(KIND_FAST_RENDER, "Fast Render", null));
                right.add(fixed("Smooth World: §cOFF"));                 // integrated-server pacing, tracked
                right.add(fixed("Preloaded Chunks: §cOFF"));             // no 1.7.10 behavior
                right.add(vertex("Dynamic Updates", "dynamicUpdates"));
                right.add(fixed("Lazy Chunk Loading: §cOFF"));           // integrated-server pacing, tracked
                return;
            case PAGE_OTHER:
                left.add(vertex("Lagometer", "lagometer"));
                left.add(vertex("Show FPS", "showFps"));
                left.add(vertex("Weather", "weather"));
                left.add(vanilla("x"));                                  // Fullscreen
                left.add(vanilla("h"));                                  // 3D Anaglyph
                right.add(vertex("Debug Profiler", "debugProfiler"));
                right.add(new Slot(KIND_AUTOSAVE, "Autosave", "autosave"));
                right.add(new Slot(KIND_TIME, "Time", "timeOverride"));
                right.add(fixed("Fullscreen Mode: Default"));
                return;
            default:                                                     // PAGE_VIDEO
                left.add(vanilla("m"));                                  // Graphics
                left.add(vanilla("n"));                                  // Smooth Lighting
                left.add(new Slot(KIND_AO_LEVEL, "Smooth Lighting Level", "aoLevel"));
                left.add(vanilla("o"));                                  // GUI Scale
                left.add(slider("d"));                                   // Brightness
                left.add(vertex("Dynamic Lights", "dynamicLights"));
                left.add(new Slot(KIND_FULLBRIGHT, "Fullbright", null)); // the Shaders slot
                left.add(nav("Details...", PAGE_DETAILS));
                left.add(nav("Animations...", PAGE_ANIMATIONS));
                right.add(slider("f"));                                  // Render Distance
                right.add(slider("j"));                                  // Max Framerate
                right.add(vanilla("g"));                                 // View Bobbing
                right.add(vanilla("i"));                                 // Advanced OpenGL
                right.add(new Slot(KIND_CHUNK_LOADING, "Chunk Loading", null));
                right.add(vertex("Dynamic FOV", "dynamicFov"));
                right.add(nav("Quality...", PAGE_QUALITY));
                right.add(nav("Performance...", PAGE_PERFORMANCE));
                right.add(nav("Other...", PAGE_OTHER));
        }
    }

    private static Slot vanilla(String optionField)
    {
        return new Slot(KIND_VANILLA, null, optionField);
    }

    private static Slot slider(String optionField)
    {
        return new Slot(KIND_SLIDER, null, optionField);
    }

    private static Slot vertex(String label, String key)
    {
        return new Slot(KIND_VERTEX, label, key);
    }

    private static Slot nav(String label, int target)
    {
        return new Slot(KIND_NAV, label, String.valueOf(target));
    }

    private static Slot fixed(String label)
    {
        return new Slot(KIND_STATIC, label, null);
    }

    /**
     * Keys whose effect is baked into section meshes at tessellation time. Flipping one
     * from the menu must re-mark every section or the world keeps the old look until
     * chunks happen to rebuild.
     */
    static boolean rebakesSections(String key)
    {
        return "betterGrass".equals(key) || "naturalTextures".equals(key)
            || "connectedTextures".equals(key) || "customColors".equals(key)
            || "swampColors".equals(key) || "betterSnow".equals(key)
            || "smoothBiomes".equals(key) || "grass".equals(key);
    }

    /** Every Vertex key wired on the Animations page: the All ON / All OFF scope. */
    static String[] animationKeys()
    {
        return new String[] {"animWater", "animFire", "particleFlame", "voidParticles",
            "particleRainSplash", "particlePotion", "terrainAnimated", "textureAnimations",
            "animLava", "animPortal", "particleExplosions", "particleSmoke",
            "particleWater", "particlePortal", "particleDripping", "itemsAnimated"};
    }

    /** Fog Start cycle: Default -> 0.2 -> 0.4 -> 0.6 -> 0.8 -> Default. */
    static String nextFogStart(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        return trimmed.equals("default") ? "0.2" : trimmed.equals("0.2") ? "0.4"
            : trimmed.equals("0.4") ? "0.6" : trimmed.equals("0.6") ? "0.8" : "default";
    }

    static String fogStartLabel(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        boolean known = trimmed.equals("0.2") || trimmed.equals("0.4")
            || trimmed.equals("0.6") || trimmed.equals("0.8");
        return "Fog Start: " + (known ? trimmed : "Default");
    }

    /** Cloud Height cycle: OFF -> 25% -> 50% -> 75% -> 100% -> OFF. */
    static int nextCloudHeight(int current)
    {
        return current == 0 ? 25 : current == 25 ? 50 : current == 50 ? 75
            : current == 75 ? 100 : 0;
    }

    static String cloudHeightLabel(int percent)
    {
        return "Cloud Height: " + (percent <= 0 ? "§cOFF" : percent + "%");
    }

    static String fastRenderLabel(boolean on)
    {
        return "Fast Render: " + (on ? "§aON" : "§cOFF");
    }

    /** Antialiasing cycle: OFF -> 2x -> 4x -> 8x -> OFF (restart applies it). */
    static String nextAntialias(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        return trimmed.equals("0") || trimmed.isEmpty() ? "2" : trimmed.equals("2") ? "4"
            : trimmed.equals("4") ? "8" : "0";
    }

    static String antialiasLabel(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        boolean on = trimmed.equals("2") || trimmed.equals("4") || trimmed.equals("8");
        return "Antialiasing: " + (on ? trimmed + "x" : "§cOFF");
    }

    /** Mipmap Type cycle: Nearest <-> Linear. */
    static String nextMipmapType(String current)
    {
        return "linear".equals(current == null ? "" : current.trim()) ? "nearest" : "linear";
    }

    static String mipmapTypeLabel(String current)
    {
        return "Mipmap Type: "
            + ("linear".equals(current == null ? "" : current.trim()) ? "Linear" : "Nearest");
    }

    /** Tri-state cycle: Default -> Fast -> Fancy -> Default. */
    static String nextTriState(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        return trimmed.equals("default") ? "fast" : trimmed.equals("fast") ? "fancy" : "default";
    }

    static String triStateLabel(String base, String current)
    {
        String trimmed = current == null ? "" : current.trim();
        String value = trimmed.equals("fast") ? "Fast" : trimmed.equals("fancy") ? "Fancy" : "Default";
        return base + ": " + value;
    }

    /** Smooth Lighting Level cycle: 100% -> OFF -> 50% -> 100%. */
    static int nextAoLevel(int current)
    {
        return current == 100 ? 0 : current == 0 ? 50 : 100;
    }

    static String aoLevelLabel(int percent)
    {
        return "Smooth Lighting Level: " + (percent == 0 ? "§cOFF" : percent + "%");
    }

    /** Autosave cycle: 45s (vanilla) -> 3min -> 30min -> 45s. */
    static String nextAutosave(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        return trimmed.equals("45") ? "180" : trimmed.equals("180") ? "1800" : "45";
    }

    static String autosaveLabel(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        String value = trimmed.equals("180") ? "3min" : trimmed.equals("1800") ? "30min" : "45s";
        return "Autosave: " + value;
    }

    /** Chunk Updates cycle: 1..5 then wrap; anything unparsable restarts at the default 4. */
    static String nextChunkUpdates(String current)
    {
        String trimmed = current == null ? "" : current.trim();

        if (trimmed.length() == 1 && trimmed.charAt(0) >= '1' && trimmed.charAt(0) <= '4')
        {
            return String.valueOf((char)(trimmed.charAt(0) + 1));
        }

        return trimmed.equals("5") ? "1" : "5";
    }

    static String chunkUpdatesLabel(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        boolean valid = trimmed.length() == 1 && trimmed.charAt(0) >= '1' && trimmed.charAt(0) <= '5';
        return "Chunk Updates: " + (valid ? trimmed : "4");
    }

    /** Time cycle: Default -> Day -> Night -> Default. */
    static String nextTimeOverride(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        return trimmed.equals("default") ? "day" : trimmed.equals("day") ? "night" : "default";
    }

    static String timeOverrideLabel(String current)
    {
        String trimmed = current == null ? "" : current.trim();
        String value = trimmed.equals("day") ? "Day" : trimmed.equals("night") ? "Night" : "Default";
        return "Time: " + value;
    }

    private VideoMenuLayout()
    {
    }
}
