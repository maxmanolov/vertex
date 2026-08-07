package vertex.hooks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;

/**
 * GUI verification probe (-Dvertex.test.guiProbe=true with -Dvertex.test.shotDir=path):
 * from the main menu, opens the vanilla Chat Settings screen, screenshots it (button
 * placement evidence), clicks the injected Chat Background button through the real patched
 * actionPerformed, screenshots the flipped label, then shuts the client down. Two frames of
 * evidence per run: placement + a full toggle round trip including the config save.
 */
public final class VertexGuiProbe
{
    // "true"/"chatOptions" probes the Chat Settings screen (button 250); "videoSettings"
    // probes the Video Settings screen (button 252). Any other value leaves it off.
    private static final String PROBE_SCREEN = System.getProperty("vertex.test.guiProbe");
    private static final boolean VIDEO = "videoSettings".equals(PROBE_SCREEN);
    private static final boolean ENABLED = VIDEO || "true".equals(PROBE_SCREEN) || "chatOptions".equals(PROBE_SCREEN);
    private static final String SHOT_DIR = System.getProperty("vertex.test.shotDir", ".");
    private static final int MENU_WARMUP_FRAMES = 150;
    private static final int SETTLE_FRAMES = 40;

    private static boolean done = false;
    private static int frames = 0;
    private static int stage = 0;
    private static int settle = 0;
    private static Object screen;
    private static Method actionPerformed;
    private static Field buttonListField;
    private static Field buttonIdField;

    static boolean active()
    {
        return ENABLED && !done;
    }

    static void tick(Object minecraft)
    {
        if (++frames < MENU_WARMUP_FRAMES)
        {
            return;
        }

        try
        {
            if (stage == 0)
            {
                openChatOptions(minecraft);
                stage = 1;
                settle = 0;
            }
            else if (stage == 1 && ++settle >= SETTLE_FRAMES)
            {
                shot(VIDEO ? "gui-video-settings-initial" : "gui-chat-options-initial");
                clickButton(VIDEO ? 252 : 250);
                stage = 2;
                settle = 0;
            }
            else if (stage == 2 && ++settle >= SETTLE_FRAMES)
            {
                shot(VIDEO ? "gui-video-settings-toggled" : "gui-chat-options-toggled");
                done = true;
                LogWrapper.info("[Vertex] GUI probe complete, shutting down");
                shutdown(minecraft);
            }
        }
        catch (Throwable t)
        {
            done = true;
            LogWrapper.severe("[Vertex] GUI probe failed: " + t);
            t.printStackTrace();
            shutdown(minecraft);
        }
    }

    private static void openChatOptions(Object minecraft) throws Exception
    {
        ClassLoader loader = minecraft.getClass().getClassLoader();
        Class<?> screenClass = loader.loadClass(VIDEO ? Mappings.GUI_VIDEO_SETTINGS : Mappings.SCREEN_CHAT_OPTIONS);
        Class<?> buttonClass = loader.loadClass(Mappings.GUI_BUTTON);
        Constructor<?> ctor = screenClass.getConstructors()[0];
        Field gameSettings = minecraft.getClass().getDeclaredField(Mappings.MC_GAME_SETTINGS);
        gameSettings.setAccessible(true);
        screen = ctor.newInstance(null, gameSettings.get(minecraft));

        actionPerformed = screenClass.getDeclaredMethod(Mappings.SCREEN_ACTION_PERFORMED, buttonClass);
        actionPerformed.setAccessible(true);
        buttonIdField = buttonClass.getDeclaredField(Mappings.BUTTON_ID);
        buttonIdField.setAccessible(true);

        for (Class<?> cls = screenClass; cls != null && buttonListField == null; cls = cls.getSuperclass())
        {
            try
            {
                buttonListField = cls.getDeclaredField(Mappings.SCREEN_BUTTON_LIST);
                buttonListField.setAccessible(true);
            }
            catch (NoSuchFieldException e)
            {
                // keep walking up
            }
        }

        Method display = null;

        for (Method candidate : minecraft.getClass().getMethods())
        {
            if (candidate.getName().equals("a") && candidate.getParameterTypes().length == 1
                && candidate.getParameterTypes()[0].getName().equals(Mappings.GUI_SCREEN))
            {
                display = candidate;
                break;
            }
        }

        if (display == null)
        {
            throw new IllegalStateException("displayGuiScreen not found");
        }

        display.invoke(minecraft, screen);
        LogWrapper.info("[Vertex] GUI probe: chat options screen opened");
    }

    private static void clickButton(int id) throws Exception
    {
        List<?> buttons = (List<?>) buttonListField.get(screen);

        for (Object button : buttons)
        {
            if (buttonIdField.getInt(button) == id)
            {
                actionPerformed.invoke(screen, button);
                LogWrapper.info("[Vertex] GUI probe: clicked button " + id);
                return;
            }
        }

        throw new IllegalStateException("Injected button " + id + " not present in buttonList ("
            + buttons.size() + " buttons)");
    }

    private static void shot(String name) throws Exception
    {
        int width = Display.getWidth();
        int height = Display.getHeight();
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3).order(ByteOrder.nativeOrder());
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buffer);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; ++y)
        {
            for (int x = 0; x < width; ++x)
            {
                int i = (y * width + x) * 3;
                int rgb = (buffer.get(i) & 255) << 16 | (buffer.get(i + 1) & 255) << 8 | (buffer.get(i + 2) & 255);
                image.setRGB(x, height - 1 - y, rgb);
            }
        }

        File dir = new File(SHOT_DIR);
        dir.mkdirs();
        File out = new File(dir, name + ".png");
        ImageIO.write(image, "png", out);
        LogWrapper.info("[Vertex] GUI probe: wrote " + out.getAbsolutePath());
    }

    private static void shutdown(Object minecraft)
    {
        try
        {
            Method quit = minecraft.getClass().getDeclaredMethod(Mappings.MC_SHUTDOWN);
            quit.setAccessible(true);
            quit.invoke(minecraft);
        }
        catch (Exception e)
        {
            LogWrapper.severe("[Vertex] GUI probe: clean shutdown failed: " + e);
        }
    }

    private VertexGuiProbe()
    {
    }
}
