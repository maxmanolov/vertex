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
     * The reference anchors the bottom row to the GRID, not the screen bottom: Done
     * (or the All ON row) sits one small gap below the last grid row, and the Other
     * page leaves two empty rows before Reset. Clamped so a degenerately small GUI
     * height overlaps the grid rather than pushing the button off-screen.
     */
    static int bottomRowY(int height, int gridRows)
    {
        return Math.min(rowY(height, gridRows) + 4, height - 22);
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

        for (int column = 0; column < 2; ++column)
        {
            List<Slot> slots = column == 0 ? left : right;

            for (int row = 0; row < slots.size(); ++row)
            {
                Slot slot = slots.get(row);
                int id = slot.kind == KIND_NAV
                    ? ID_NAV_BASE + Integer.parseInt(slot.ref) : slotId++;
                placed.add(new Placed(slot.kind, slot.label, slot.ref, id,
                    columnX(width, column), rowY(height, row), 150, 20));
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
                left.add(vanilla("p"));                                  // Clouds
                left.add(fixed("Trees: Fast"));
                left.add(fixed("Water: Fast"));
                left.add(vertex("Sky", "sky"));
                left.add(fixed("Sun & Moon: §cOFF"));
                left.add(vertex("Fog", "fog"));
                left.add(fixed("Depth Fog: §cOFF"));
                left.add(fixed("Translucent Blocks: Fast"));
                left.add(fixed("Vignette: Fast"));
                right.add(fixed("Cloud Height: §cOFF"));
                right.add(fixed("Grass: Fast"));
                right.add(vertex("Rain & Snow", "weather"));
                right.add(fixed("Stars: §cOFF"));
                right.add(vanilla("z"));                                 // Show Capes
                right.add(fixed("Fog Start: 0.2"));
                right.add(new Slot(KIND_HELD_TOOLTIPS, "Held Item Tooltips", null));
                right.add(fixed("Dropped Items: Fast"));
                return;
            case PAGE_ANIMATIONS:
                left.add(fixed("Water Animated: §aON"));
                left.add(fixed("Fire Animated: §aON"));
                left.add(fixed("Redstone Animated: §aON"));
                left.add(fixed("Flame Animated: §cOFF"));
                left.add(vertex("Void Particles", "voidParticles"));
                left.add(fixed("Rain Splash: §aON"));
                left.add(fixed("Potion Particles: §aON"));
                left.add(vertex("Terrain Animated", "textureAnimations"));
                left.add(fixed("Textures Animated: §aON"));
                right.add(fixed("Lava Animated: §aON"));
                right.add(fixed("Portal Animated: §aON"));
                right.add(fixed("Explosion Animated: §aON"));
                right.add(fixed("Smoke Animated: §aON"));
                right.add(fixed("Water Particles: §aON"));
                right.add(fixed("Portal Particles: §aON"));
                right.add(fixed("Dripping Water/Lava: §aON"));
                right.add(fixed("Items Animated: §aON"));
                right.add(vanilla("q"));                                 // Particles
                return;
            case PAGE_QUALITY:
                left.add(slider("F"));                                   // Mipmap Levels
                left.add(slider("G"));                                   // Anisotropic Filtering
                left.add(fixed("Clear Water: §cOFF"));
                left.add(vertex("Better Grass", "betterGrass"));
                left.add(fixed("Custom Fonts: §cOFF"));
                left.add(fixed("Swamp Colors: §aON"));
                left.add(fixed("Connected Textures: §cOFF"));
                left.add(vertex("Custom Sky", "customSky"));
                right.add(fixed("Mipmap Type: Nearest"));
                right.add(fixed("Antialiasing: §cOFF"));
                right.add(vertex("Random Mobs", "randomEntities"));
                right.add(fixed("Better Snow: §cOFF"));
                right.add(vertex("Custom Colors", "customColors"));
                right.add(fixed("Smooth Biomes: §cOFF"));
                right.add(vertex("Natural Textures", "naturalTextures"));
                return;
            case PAGE_PERFORMANCE:
                left.add(fixed("Smooth FPS: §cOFF"));
                left.add(fixed("Load Far: §cOFF"));
                left.add(fixed("Chunk Updates: 1"));
                left.add(fixed("Fast Math: §aON"));
                left.add(fixed("Fast Render: §aON"));
                right.add(fixed("Smooth World: §cOFF"));
                right.add(fixed("Preloaded Chunks: §cOFF"));
                right.add(fixed("Dynamic Updates: §cOFF"));
                right.add(fixed("Lazy Chunk Loading: §cOFF"));
                return;
            case PAGE_OTHER:
                left.add(fixed("Lagometer: §cOFF"));
                left.add(fixed("Show FPS: §cOFF"));
                left.add(fixed("Weather: §cOFF"));
                left.add(vanilla("x"));                                  // Fullscreen
                left.add(vanilla("h"));                                  // 3D Anaglyph
                right.add(fixed("Debug Profiler: §cOFF"));
                right.add(fixed("Autosave: 3min"));
                right.add(fixed("Time: Default"));
                right.add(fixed("Fullscreen Mode: Default"));
                return;
            default:                                                     // PAGE_VIDEO
                left.add(vanilla("m"));                                  // Graphics
                left.add(vanilla("n"));                                  // Smooth Lighting
                left.add(fixed("Smooth Lighting Level: §cOFF"));
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
                right.add(fixed("Dynamic FOV: §aON"));
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

    private VideoMenuLayout()
    {
    }
}
