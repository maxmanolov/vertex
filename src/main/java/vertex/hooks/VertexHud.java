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
    private static final int FULLBRIGHT_BUTTON_ID = 252;

    private static Field buttonList;
    private static Field screenWidth;
    private static Field buttonId;
    private static Field buttonDisplay;
    private static Field buttonY;
    private static Constructor<?> buttonCtor;
    private static boolean guiBroken = false;

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

    /** Tail of GuiVideoSettings.initGui: append the Fullbright toggle (#116). */
    public static void videoOptionsInit(Object screen)
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
            int doneY = 0;

            for (Object button : buttons)
            {
                doneY = Math.max(doneY, buttonY.getInt(button));
            }

            // This screen's option grid lives inside a scrolling row-list widget, NOT in
            // buttonList (which holds only Done) - a floating button "above Done" lands
            // on whatever row is scrolled there (caught by the GUI probe screenshot).
            // The one patch of screen the list never covers is Done's own row; sit to
            // Done's right, sized for the ~110px flank at the minimum scaled width.
            buttons.add(buttonCtor.newInstance(FULLBRIGHT_BUTTON_ID, width / 2 + 104, doneY, 115, 20,
                label("Fullbright", "fullbright")));
        }
        catch (Throwable t)
        {
            guiBroken = true;
            LogWrapper.warning("[Vertex] Video options button injection disabled after failure: " + t);
        }
    }

    /** Head guard on GuiVideoSettings.actionPerformed: true = the click was ours. */
    public static boolean videoOptionsAction(Object screen, Object button)
    {
        if (guiBroken || button == null)
        {
            return false;
        }

        try
        {
            resolve(screen);

            if (buttonId.getInt(button) != FULLBRIGHT_BUTTON_ID)
            {
                return false;
            }

            VertexConfig.setAndSave("fullbright", !VertexConfig.enabled("fullbright"));
            buttonDisplay.set(button, label("Fullbright", "fullbright"));
            return true;
        }
        catch (Throwable t)
        {
            guiBroken = true;
            LogWrapper.warning("[Vertex] Video options button handling disabled after failure: " + t);
            return false;
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
