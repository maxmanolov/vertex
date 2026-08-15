package vertex.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;

/**
 * HUD background toggles: chat and scoreboard backgrounds each get a config key, a rerouted
 * drawRect gate, and an ON/OFF button injected into the vanilla Chat Settings screen.
 *
 * The rerouted call sites arrive here instead of Gui.drawRect; when the toggle is on the
 * rectangle is drawn with the same GL sequence vanilla uses (blend on, texture off,
 * src-alpha blending, one quad, state restored), so enabled behavior is pixel-identical.
 * Reflection for the button injection resolves everything from live instances - never
 * Class.forName on obfuscated names, which would load untransformed duplicates across the
 * LaunchWrapper classloader split.
 */
public final class VertexHud
{
    private static final int CHAT_BG_BUTTON_ID = 250;
    private static final int SCORE_BG_BUTTON_ID = 251;

    private static Field buttonList;
    private static Field screenWidth;
    private static Field buttonId;
    private static Field buttonDisplay;
    private static Field buttonY;
    private static Constructor<?> buttonCtor;
    private static boolean guiBroken = false;

    // ---- overlay tail: Show FPS + Lagometer ---------------------------------------------

    /** Frame-time ring for the lagometer: one entry per rendered frame, wrap-around. */
    private static final int LAG_COLUMNS = 240;
    private static final long[] frameNanos = new long[LAG_COLUMNS];
    private static int frameCursor = 0;
    private static long lastFrameStamp = 0L;
    private static long fpsWindowStart = 0L;
    private static int fpsWindowFrames = 0;
    private static int fps = 0;

    private static Field giMc;
    private static Field mcFont;
    private static java.lang.reflect.Method fontDrawShadow;
    private static Field showChartField;
    private static boolean overlayBroken = false;

    /** Tail of GuiIngame.renderGameOverlay: the GUI ortho matrix is still active. */
    public static void afterOverlay(Object gui)
    {
        long now = System.nanoTime();

        if (lastFrameStamp != 0L)
        {
            frameNanos[frameCursor] = now - lastFrameStamp;
            frameCursor = (frameCursor + 1) % LAG_COLUMNS;
        }

        lastFrameStamp = now;
        ++fpsWindowFrames;

        if (fpsWindowStart == 0L)
        {
            fpsWindowStart = now;
        }
        else if (now - fpsWindowStart >= 1_000_000_000L)
        {
            fps = (int)(fpsWindowFrames * 1_000_000_000L / (now - fpsWindowStart));
            fpsWindowFrames = 0;
            fpsWindowStart = now;
        }

        boolean showFps = VertexConfig.enabled("showFps");
        boolean lagometer = VertexConfig.enabled("lagometer");

        if (overlayBroken || (!showFps && !lagometer))
        {
            return;
        }

        try
        {
            if (lagometer)
            {
                drawLagometer();
            }

            if (showFps)
            {
                Object minecraft = resolveOverlayHandles(gui);
                Object font = mcFont.get(minecraft);
                fontDrawShadow.invoke(font, fps + " fps", Integer.valueOf(2),
                    Integer.valueOf(2), Integer.valueOf(0xFFFFFF));
            }
        }
        catch (Throwable t)
        {
            overlayBroken = true;
            LogWrapper.severe("[Vertex] HUD overlay extras disabled after failure");
            t.printStackTrace();
        }
    }

    /**
     * One column per frame, oldest to newest, under the FPS readout: green under
     * 16.7ms, yellow under 33.3ms, red above, height 2px per ms (clamped to 40).
     */
    private static void drawLagometer()
    {
        int baseY = 14;

        for (int i = 0; i < LAG_COLUMNS; ++i)
        {
            long nanos = frameNanos[(frameCursor + i) % LAG_COLUMNS];

            if (nanos == 0L)
            {
                continue;
            }

            float ms = nanos / 1_000_000.0F;
            int height = Math.min(40, Math.max(1, (int)(ms * 2.0F)));
            int color = ms < 16.7F ? 0x9000FF00 : ms < 33.4F ? 0x90FFFF00 : 0x90FF0000;
            fillRect(2 + i, baseY, 3 + i, baseY + height, color);
        }
    }

    private static Object resolveOverlayHandles(Object gui) throws Exception
    {
        if (giMc == null)
        {
            giMc = gui.getClass().getDeclaredField(Mappings.GI_MC);
            giMc.setAccessible(true);
        }

        Object minecraft = giMc.get(gui);

        if (mcFont == null)
        {
            mcFont = minecraft.getClass().getDeclaredField(Mappings.MC_FONT_RENDERER);
            mcFont.setAccessible(true);
            Class<?> fontClass = mcFont.getType();
            fontDrawShadow = fontClass.getMethod(Mappings.FONT_DRAW_SHADOW,
                String.class, int.class, int.class, int.class);
            fontDrawShadow.setAccessible(true);
        }

        return minecraft;
    }

    // ---- profiler chart gate --------------------------------------------------------------

    /**
     * Reroute of Minecraft's showDebugProfilerChart reads: with debugProfiler off the
     * chart never shows and vanilla's own conjunction turns profiler collection off.
     */
    public static boolean debugChartEnabled(Object settings)
    {
        try
        {
            if (showChartField == null)
            {
                showChartField = settings.getClass().getDeclaredField(Mappings.GS_SHOW_DEBUG_CHART);
                showChartField.setAccessible(true);
            }

            boolean vanilla = showChartField.getBoolean(settings);
            return vanilla && VertexConfig.enabled("debugProfiler");
        }
        catch (Throwable t)
        {
            // Unresolvable field: degrade to key-only control rather than pinning the
            // chart on or off regardless of the setting.
            return VertexConfig.enabled("debugProfiler");
        }
    }

    // ---- rerouted drawRect gates -------------------------------------------------------

    public static void chatRect(int left, int top, int right, int bottom, int color)
    {
        if (VertexConfig.enabled("chatBackground"))
        {
            fillRect(left, top, right, bottom, color);
        }
    }

    public static void scoreboardRect(int left, int top, int right, int bottom, int color)
    {
        if (VertexConfig.enabled("scoreboardBackground"))
        {
            fillRect(left, top, right, bottom, color);
        }
    }

    /** Vanilla Gui.drawRect semantics: swapped coordinates normalize, ARGB color, alpha blend. */
    private static void fillRect(int left, int top, int right, int bottom, int color)
    {
        int swap;

        if (left < right)
        {
            swap = left;
            left = right;
            right = swap;
        }

        if (top < bottom)
        {
            swap = top;
            top = bottom;
            bottom = swap;
        }

        float alpha = (color >> 24 & 255) / 255.0F;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ---- Chat Settings screen buttons --------------------------------------------------

    /** Tail of ScreenChatOptions.initGui: append the two Vertex toggle buttons. */
    public static void chatOptionsInit(Object screen)
    {
        if (guiBroken)
        {
            return;
        }

        try
        {
            resolve(screen);
            @SuppressWarnings("unchecked")
            List<Object> buttons = (List<Object>) buttonList.get(screen);
            int width = screenWidth.getInt(screen);
            int maxY = 0;

            for (Object button : buttons)
            {
                maxY = Math.max(maxY, buttonY.getInt(button));
            }

            // The bottom-most vanilla button is Done; the row above it is clear in the
            // 1.7.10 chat options layout (verified by screenshot before release).
            int rowY = maxY - 24;
            buttons.add(buttonCtor.newInstance(CHAT_BG_BUTTON_ID, width / 2 - 155, rowY, 150, 20,
                label("Chat Background", "chatBackground")));
            buttons.add(buttonCtor.newInstance(SCORE_BG_BUTTON_ID, width / 2 + 5, rowY, 150, 20,
                label("Scoreboard BG", "scoreboardBackground")));
        }
        catch (Throwable t)
        {
            // The screen must keep working even if injection fails; disable, never crash.
            guiBroken = true;
            LogWrapper.warning("[Vertex] Chat options button injection disabled after failure: " + t);
        }
    }

    /** Head guard on ScreenChatOptions.actionPerformed: true = the click was ours. */
    public static boolean chatOptionsAction(Object screen, Object button)
    {
        if (guiBroken || button == null)
        {
            return false;
        }

        try
        {
            resolve(screen);
            int id = buttonId.getInt(button);
            String key;
            String name;

            if (id == CHAT_BG_BUTTON_ID)
            {
                key = "chatBackground";
                name = "Chat Background";
            }
            else if (id == SCORE_BG_BUTTON_ID)
            {
                key = "scoreboardBackground";
                name = "Scoreboard BG";
            }
            else
            {
                return false;
            }

            VertexConfig.setAndSave(key, !VertexConfig.enabled(key));
            buttonDisplay.set(button, label(name, key));
            return true;
        }
        catch (Throwable t)
        {
            guiBroken = true;
            LogWrapper.warning("[Vertex] Chat options button handling disabled after failure: " + t);
            return false;
        }
    }

    private static String label(String name, String key)
    {
        return name + ": " + (VertexConfig.enabled(key) ? "ON" : "OFF");
    }

    private static synchronized void resolve(Object screen) throws Exception
    {
        if (buttonCtor != null)
        {
            return;
        }

        buttonList = findField(screen.getClass(), Mappings.SCREEN_BUTTON_LIST);
        screenWidth = findField(screen.getClass(), Mappings.SCREEN_WIDTH);
        Class<?> buttonClass = screen.getClass().getClassLoader().loadClass(Mappings.GUI_BUTTON);
        buttonId = findField(buttonClass, Mappings.BUTTON_ID);
        buttonDisplay = findField(buttonClass, Mappings.BUTTON_DISPLAY);
        buttonY = findField(buttonClass, Mappings.BUTTON_Y);
        buttonCtor = buttonClass.getConstructor(
            int.class, int.class, int.class, int.class, int.class, String.class);
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

    private VertexHud()
    {
    }
}
