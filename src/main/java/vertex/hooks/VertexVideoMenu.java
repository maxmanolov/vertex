package vertex.hooks;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Rebuilds the vanilla Video Settings screen (and five sub-pages living on the same
 * screen class) into the OptiFine 1.7.10 layout described by {@link VideoMenuLayout},
 * wiring every interactive slot to functionality that actually exists: vanilla options
 * through GameSettings (real labels, real persistence, real GuiOptionSlider widgets),
 * Vertex features through vertex.properties, and nothing at all behind the disabled
 * reference slots.
 *
 * Mechanism: the tail of GuiVideoSettings.initGui replaces the vanilla content - the
 * scrolling options row list collapses to an empty zero-height instance and buttonList
 * is rebuilt from the layout - and a head guard on actionPerformed dispatches every
 * click except Done (id 200 falls through to vanilla, which saves options and returns
 * to the parent screen). Sub-pages are real GuiVideoSettings instances whose parent is
 * the page that opened them, so Done and Esc walk back exactly like vanilla screens.
 * Every reflective handle resolves from live instances; any failure disables the whole
 * rework and the vanilla screen keeps working untouched.
 */
public final class VertexVideoMenu
{
    private static boolean disabled = false;
    private static boolean resolved = false;

    private static Field parentField;
    private static Field titleField;
    private static Field rowListField;
    private static Field buttonListField;
    private static Field widthField;
    private static Field heightField;
    private static Field screenMcField;
    private static Field mcGameSettings;
    private static Field buttonIdField;
    private static Field buttonDisplayField;
    private static Field buttonEnabledField;
    private static Field heldTooltipsField;
    private static Method setOptionValue;
    private static Method setOptionFloatValue;
    private static Method getOptionOrdinal;
    private static Method getLabel;
    private static Method saveOptions;
    private static Method displayGuiScreen;
    private static Constructor<?> buttonCtor;
    private static Constructor<?> sliderCtor;
    private static Constructor<?> rowListCtor;
    private static Constructor<?> screenCtor;
    private static Class<?> optionsClass;
    private static Object emptyOptions;
    private static final Map<String, Object> optionConstants = new HashMap<String, Object>();

    /** Which page each live screen instance shows; weak so closed screens can collect. */
    private static final WeakHashMap<Object, Integer> pageOf = new WeakHashMap<Object, Integer>();
    private static int pendingPage = -1;

    /** Tail of GuiVideoSettings.initGui: replace the vanilla content with our page. */
    public static void initScreen(Object screen)
    {
        if (disabled)
        {
            return;
        }

        try
        {
            if (!resolved)
            {
                resolve(screen);
            }

            Integer known = pageOf.get(screen);
            int page = pendingPage >= 0 ? pendingPage
                : known != null ? known.intValue() : VideoMenuLayout.PAGE_VIDEO;
            pendingPage = -1;
            pageOf.put(screen, Integer.valueOf(page));
            rebuild(screen, page);
        }
        catch (Throwable t)
        {
            disable(t);
        }
    }

    /** Head guard on GuiVideoSettings.actionPerformed: true = the click was handled. */
    public static boolean actionPerformed(Object screen, Object button)
    {
        if (disabled || button == null)
        {
            return false;
        }

        try
        {
            Integer pageValue = pageOf.get(screen);

            if (pageValue == null)
            {
                return false;
            }

            if (!buttonEnabledField.getBoolean(button))
            {
                return true;
            }

            int id = buttonIdField.getInt(button);

            if (id == VideoMenuLayout.ID_DONE)
            {
                // Vanilla saves options and walks back to the parent screen.
                return false;
            }

            VideoMenuLayout.Placed slot = findSlot(screen, pageValue.intValue(), id);

            if (slot == null)
            {
                return true;
            }

            Object minecraft = screenMcField.get(screen);
            Object settings = mcGameSettings.get(minecraft);

            switch (slot.kind)
            {
                case VideoMenuLayout.KIND_NAV:
                    pendingPage = Integer.parseInt(slot.ref);
                    displayGuiScreen.invoke(minecraft, screenCtor.newInstance(screen, settings));
                    return true;
                case VideoMenuLayout.KIND_VANILLA:
                    setOptionValue.invoke(settings, option(slot.ref), Integer.valueOf(1));
                    buttonDisplayField.set(button, vanillaLabel(settings, slot.ref));
                    return true;
                case VideoMenuLayout.KIND_VERTEX:
                    VertexConfig.setAndSave(slot.ref, !VertexConfig.enabled(slot.ref));
                    buttonDisplayField.set(button, vertexLabel(slot.label, slot.ref));

                    if (VideoMenuLayout.rebakesSections(slot.ref))
                    {
                        VertexRenderer.requestSettingsRemark();
                    }

                    return true;
                case VideoMenuLayout.KIND_FULLBRIGHT:
                    VertexConfig.setAndSave("fullbright", !VertexConfig.enabled("fullbright"));
                    buttonDisplayField.set(button, vertexLabel("Fullbright", "fullbright"));
                    return true;
                case VideoMenuLayout.KIND_CHUNK_LOADING:
                    VertexConfig.setAndSave("multicore", !VertexConfig.enabled("multicore"));
                    buttonDisplayField.set(button, chunkLoadingLabel());
                    return true;
                case VideoMenuLayout.KIND_CLOUDS:
                    boolean clouds = effectiveClouds(settings);
                    setToggle(settings, Mappings.OPT_CLOUDS, !clouds);
                    VertexConfig.setAndSave("clouds", !clouds);
                    saveOptions.invoke(settings);
                    buttonDisplayField.set(button, cloudsLabel(settings));
                    return true;
                case VideoMenuLayout.KIND_HELD_TOOLTIPS:
                    heldTooltipsField.setBoolean(settings, !heldTooltipsField.getBoolean(settings));
                    saveOptions.invoke(settings);
                    buttonDisplayField.set(button, heldTooltipsLabel(settings));
                    return true;
                case VideoMenuLayout.KIND_FOG_START:
                    VertexConfig.setAndSaveValue("fogStart",
                        VideoMenuLayout.nextFogStart(VertexConfig.value("fogStart", "default")));
                    buttonDisplayField.set(button, fogStartLabel());
                    return true;
                case VideoMenuLayout.KIND_CLOUD_HEIGHT:
                    VertexConfig.setAndSaveValue("cloudHeight", String.valueOf(
                        VideoMenuLayout.nextCloudHeight(cloudHeightPercent())));
                    buttonDisplayField.set(button, cloudHeightLabel());
                    return true;
                case VideoMenuLayout.KIND_ALL_ON:
                case VideoMenuLayout.KIND_ALL_OFF:
                    boolean on = slot.kind == VideoMenuLayout.KIND_ALL_ON;
                    VertexConfig.setAndSave("textureAnimations", on);
                    VertexConfig.setAndSave("voidParticles", on);
                    rebuild(screen, pageValue.intValue());
                    return true;
                case VideoMenuLayout.KIND_RESET:
                    resetDefaults(settings);
                    rebuild(screen, pageValue.intValue());
                    return true;
                default:
                    // Sliders manage themselves through mouse handling; static slots
                    // are disabled and never get here. Swallow so the vanilla body
                    // (which only understands Done) does not run.
                    return true;
            }
        }
        catch (Throwable t)
        {
            disable(t);
            return false;
        }
    }

    /** Added GuiVideoSettings.keyTyped override: Esc saves and walks to this page's parent. */
    public static boolean keyTyped(Object screen, int keyCode)
    {
        if (disabled || keyCode != 1 || !pageOf.containsKey(screen))
        {
            return false;
        }

        try
        {
            Object minecraft = screenMcField.get(screen);
            Object settings = mcGameSettings.get(minecraft);
            saveOptions.invoke(settings);
            displayGuiScreen.invoke(minecraft, parentField.get(screen));
            return true;
        }
        catch (Throwable t)
        {
            disable(t);
            return false;
        }
    }

    // ---- page construction ---------------------------------------------------------

    private static void rebuild(Object screen, int page) throws Exception
    {
        Object minecraft = screenMcField.get(screen);
        Object settings = mcGameSettings.get(minecraft);
        titleField.set(screen, VideoMenuLayout.title(page));
        int width = widthField.getInt(screen);
        int height = heightField.getInt(screen);
        // An empty row list parked above the viewport: vanilla's draw and mouse paths
        // keep their wiring but nothing renders or hits. The strip must be at least
        // 5 tall - GuiSlot's scroll math divides by the zero content height whenever
        // (bottom - top - 4) is not positive (measured: / by zero at bcm.a:295 with a
        // zero-area list).
        rowListField.set(screen, rowListCtor.newInstance(minecraft,
            Integer.valueOf(width), Integer.valueOf(height), Integer.valueOf(-30),
            Integer.valueOf(-5), Integer.valueOf(25), emptyOptions));

        @SuppressWarnings("unchecked")
        List<Object> buttons = (List<Object>) buttonListField.get(screen);
        buttons.clear();

        for (VideoMenuLayout.Placed slot : VideoMenuLayout.layout(page, width, height))
        {
            buttons.add(build(slot, settings));
        }
    }

    private static Object build(VideoMenuLayout.Placed slot, Object settings) throws Exception
    {
        if (slot.kind == VideoMenuLayout.KIND_SLIDER)
        {
            return sliderCtor.newInstance(Integer.valueOf(slot.id), Integer.valueOf(slot.x),
                Integer.valueOf(slot.y), option(slot.ref));
        }

        String label;

        switch (slot.kind)
        {
            case VideoMenuLayout.KIND_VANILLA:
                label = vanillaLabel(settings, slot.ref);
                break;
            case VideoMenuLayout.KIND_VERTEX:
                label = vertexLabel(slot.label, slot.ref);
                break;
            case VideoMenuLayout.KIND_FULLBRIGHT:
                label = vertexLabel("Fullbright", "fullbright");
                break;
            case VideoMenuLayout.KIND_CHUNK_LOADING:
                label = chunkLoadingLabel();
                break;
            case VideoMenuLayout.KIND_CLOUDS:
                label = cloudsLabel(settings);
                break;
            case VideoMenuLayout.KIND_HELD_TOOLTIPS:
                label = heldTooltipsLabel(settings);
                break;
            case VideoMenuLayout.KIND_FOG_START:
                label = fogStartLabel();
                break;
            case VideoMenuLayout.KIND_CLOUD_HEIGHT:
                label = cloudHeightLabel();
                break;
            default:
                label = slot.label;
        }

        Object button = buttonCtor.newInstance(Integer.valueOf(slot.id),
            Integer.valueOf(slot.x), Integer.valueOf(slot.y),
            Integer.valueOf(slot.width), Integer.valueOf(slot.height), label);

        if (slot.kind == VideoMenuLayout.KIND_STATIC)
        {
            // Reference geometry without reference behavior: nothing real sits behind
            // this slot, so it renders in the disabled style and ignores clicks.
            buttonEnabledField.setBoolean(button, false);
        }

        return button;
    }

    private static VideoMenuLayout.Placed findSlot(Object screen, int page, int id) throws Exception
    {
        int width = widthField.getInt(screen);
        int height = heightField.getInt(screen);

        for (VideoMenuLayout.Placed slot : VideoMenuLayout.layout(page, width, height))
        {
            if (slot.id == id)
            {
                return slot;
            }
        }

        return null;
    }

    // ---- labels ----------------------------------------------------------------------

    private static String vanillaLabel(Object settings, String ref) throws Exception
    {
        return colorize((String) getLabel.invoke(settings, option(ref)));
    }

    private static String vertexLabel(String base, String key)
    {
        return base + ": " + (VertexConfig.enabled(key) ? "§aON" : "§cOFF");
    }

    private static String chunkLoadingLabel()
    {
        return "Chunk Loading: " + (VertexConfig.enabled("multicore") ? "Multi-Core" : "Default");
    }

    private static String cloudsLabel(Object settings) throws Exception
    {
        return "Clouds: " + (effectiveClouds(settings) ? "§aON" : "§cOFF");
    }

    private static boolean effectiveClouds(Object settings) throws Exception
    {
        return effectiveClouds(vanillaToggle(settings, Mappings.OPT_CLOUDS),
            VertexConfig.enabled("clouds"));
    }

    static boolean effectiveClouds(boolean vanillaClouds, boolean vertexClouds)
    {
        return vanillaClouds && vertexClouds;
    }

    private static String heldTooltipsLabel(Object settings) throws Exception
    {
        return "Held Item Tooltips: "
            + (heldTooltipsField.getBoolean(settings) ? "§aON" : "§cOFF");
    }

    private static String fogStartLabel()
    {
        return VideoMenuLayout.fogStartLabel(VertexConfig.value("fogStart", "default"));
    }

    private static int cloudHeightPercent()
    {
        return VertexSkyDetails.cloudLiftPercent(VertexConfig.value("cloudHeight", "0"));
    }

    private static String cloudHeightLabel()
    {
        return VideoMenuLayout.cloudHeightLabel(cloudHeightPercent());
    }

    /** The reference colors binary states: green ON, red OFF; other values stay plain. */
    private static String colorize(String label)
    {
        if (label.endsWith(": ON"))
        {
            return label.substring(0, label.length() - 2) + "§aON";
        }

        if (label.endsWith(": OFF"))
        {
            return label.substring(0, label.length() - 3) + "§cOFF";
        }

        return label;
    }

    // ---- reset -------------------------------------------------------------------------

    /**
     * Reset Video Settings, scoped to what can be reset honestly: every Vertex key on
     * these pages returns to its declared default, vanilla float options return to the
     * 1.7.10 defaults, and vanilla boolean toggles are flipped back through the same
     * setOptionValue path a click uses. Cycle options without a boolean getter
     * (graphics, smooth lighting, GUI scale, particles) and fullscreen (a live display
     * mode switch) are deliberately left alone.
     */
    private static void resetDefaults(Object settings) throws Exception
    {
        String[] vertexKeys = {"dynamicLights", "fullbright", "sky", "clouds", "fog", "weather",
            "textureAnimations", "voidParticles", "betterGrass", "randomEntities",
            "customColors", "naturalTextures", "customSky", "connectedTextures", "multicore",
            "sunMoon", "stars", "depthFog"};
        boolean remark = false;

        for (String key : vertexKeys)
        {
            boolean target = VertexConfig.declaredDefault(key);
            remark |= VideoMenuLayout.rebakesSections(key) && VertexConfig.enabled(key) != target;
            VertexConfig.setAndSave(key, target);
        }

        if (remark)
        {
            VertexRenderer.requestSettingsRemark();
        }

        VertexConfig.setAndSaveValue("fogStart", "default");
        VertexConfig.setAndSaveValue("cloudHeight", "0");

        setOptionFloatValue.invoke(settings, option(Mappings.OPT_GAMMA), Float.valueOf(0.0F));
        setOptionFloatValue.invoke(settings, option(Mappings.OPT_RENDER_DISTANCE), Float.valueOf(8.0F));
        setOptionFloatValue.invoke(settings, option(Mappings.OPT_FRAMERATE), Float.valueOf(120.0F));
        setOptionFloatValue.invoke(settings, option(Mappings.OPT_MIPMAPS), Float.valueOf(4.0F));
        setOptionFloatValue.invoke(settings, option(Mappings.OPT_ANISO), Float.valueOf(1.0F));
        setToggle(settings, Mappings.OPT_VIEW_BOBBING, true);
        setToggle(settings, Mappings.OPT_ADVANCED_GL, false);
        setToggle(settings, Mappings.OPT_ANAGLYPH, false);
        setToggle(settings, Mappings.OPT_CLOUDS, true);
        setToggle(settings, Mappings.OPT_SHOW_CAPE, true);

        if (!heldTooltipsField.getBoolean(settings))
        {
            heldTooltipsField.setBoolean(settings, true);
        }

        saveOptions.invoke(settings);
    }

    private static boolean vanillaToggle(Object settings, String ref) throws Exception
    {
        return ((Boolean)getOptionOrdinal.invoke(settings, option(ref))).booleanValue();
    }

    private static void setToggle(Object settings, String ref, boolean target) throws Exception
    {
        if (vanillaToggle(settings, ref) != target)
        {
            setOptionValue.invoke(settings, option(ref), Integer.valueOf(1));
        }
    }

    // ---- resolution ----------------------------------------------------------------------

    private static Object option(String fieldName) throws Exception
    {
        Object constant = optionConstants.get(fieldName);

        if (constant == null)
        {
            constant = optionsClass.getDeclaredField(fieldName).get(null);
            optionConstants.put(fieldName, constant);
        }

        return constant;
    }

    private static void resolve(Object screen) throws Exception
    {
        Class<?> screenClass = screen.getClass();
        ClassLoader loader = screenClass.getClassLoader();
        parentField = screenClass.getDeclaredField(Mappings.VS_PARENT);
        parentField.setAccessible(true);
        titleField = screenClass.getDeclaredField(Mappings.VS_TITLE);
        titleField.setAccessible(true);
        rowListField = screenClass.getDeclaredField(Mappings.VS_ROW_LIST);
        rowListField.setAccessible(true);
        buttonListField = findField(screenClass, Mappings.SCREEN_BUTTON_LIST);
        widthField = findField(screenClass, Mappings.SCREEN_WIDTH);
        heightField = findField(screenClass, Mappings.SCREEN_HEIGHT);
        screenMcField = findField(screenClass, Mappings.SCREEN_MC);

        Object minecraft = screenMcField.get(screen);
        mcGameSettings = minecraft.getClass().getDeclaredField(Mappings.MC_GAME_SETTINGS);
        mcGameSettings.setAccessible(true);
        Object settings = mcGameSettings.get(minecraft);
        Class<?> settingsClass = settings.getClass();

        optionsClass = loader.loadClass(Mappings.OPTIONS_ENUM);
        setOptionValue = settingsClass.getMethod(Mappings.GS_SET_OPTION, optionsClass, int.class);
        setOptionFloatValue = settingsClass.getMethod(Mappings.GS_SET_OPTION, optionsClass, float.class);
        getOptionOrdinal = settingsClass.getMethod(Mappings.GS_GET_ORDINAL, optionsClass);
        getLabel = settingsClass.getMethod(Mappings.GS_GET_LABEL, optionsClass);
        saveOptions = settingsClass.getMethod(Mappings.GS_SAVE_OPTIONS);
        heldTooltipsField = settingsClass.getDeclaredField(Mappings.GS_HELD_ITEM_TOOLTIPS);
        heldTooltipsField.setAccessible(true);

        Class<?> buttonClass = loader.loadClass(Mappings.GUI_BUTTON);
        buttonIdField = buttonClass.getDeclaredField(Mappings.BUTTON_ID);
        buttonIdField.setAccessible(true);
        buttonDisplayField = buttonClass.getDeclaredField(Mappings.BUTTON_DISPLAY);
        buttonDisplayField.setAccessible(true);
        buttonEnabledField = buttonClass.getDeclaredField(Mappings.BUTTON_ENABLED);
        buttonEnabledField.setAccessible(true);
        buttonCtor = buttonClass.getConstructor(
            int.class, int.class, int.class, int.class, int.class, String.class);
        sliderCtor = loader.loadClass(Mappings.GUI_OPTION_SLIDER).getConstructor(
            int.class, int.class, int.class, optionsClass);

        Class<?> minecraftClass = loader.loadClass(Mappings.MINECRAFT);
        Class<?> screenBase = loader.loadClass(Mappings.GUI_SCREEN);
        emptyOptions = Array.newInstance(optionsClass, 0);
        rowListCtor = loader.loadClass(Mappings.OPTIONS_ROW_LIST).getConstructor(
            minecraftClass, int.class, int.class, int.class, int.class, int.class,
            emptyOptions.getClass());
        screenCtor = screenClass.getConstructor(screenBase, settingsClass);

        for (Method candidate : minecraftClass.getMethods())
        {
            if (candidate.getName().equals(Mappings.MC_DISPLAY_GUI_SCREEN)
                && candidate.getParameterTypes().length == 1
                && candidate.getParameterTypes()[0] == screenBase)
            {
                displayGuiScreen = candidate;
                break;
            }
        }

        if (displayGuiScreen == null)
        {
            throw new IllegalStateException("displayGuiScreen not found");
        }

        resolved = true;
    }

    private static Field findField(Class<?> start, String name) throws NoSuchFieldException
    {
        for (Class<?> cls = start; cls != null; cls = cls.getSuperclass())
        {
            try
            {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException e)
            {
                // keep walking up
            }
        }

        throw new NoSuchFieldException(start.getName() + "." + name);
    }

    private static void disable(Throwable t)
    {
        if (disabled)
        {
            return;
        }

        disabled = true;
        LogWrapper.severe("[Vertex] Video settings rework disabled after failure; vanilla screen stays");
        t.printStackTrace();
    }

    private VertexVideoMenu()
    {
    }
}
