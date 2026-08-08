package vertex.hooks;

import java.lang.reflect.Field;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import vertex.Mappings;

/**
 * ToggleSprint: tap the vanilla sprint key once to latch continuous sprinting instead of
 * holding it down. While latched, this hook holds the sprint KeyBinding's pressed flag
 * high once per frame, so every vanilla sprint gate stays authoritative - the forward
 * key, hunger, blindness, collisions and sneaking all behave exactly as if the key were
 * physically held, and the player re-sprints the instant vanilla allows it (after
 * stopping, jumping, or bumping into a block). Nothing else is patched: no movement
 * logic, no packets; the server keeps enforcing sprint legality untouched, which is the
 * same posture as modern Minecraft's native sprint-toggle accessibility setting.
 *
 * The latch starts armed when the feature is enabled (enabling toggleSprint IS choosing
 * auto-sprint); tapping the sprint key (default Left Ctrl, rebindable) pauses and
 * resumes it in-session. Taps are only sampled with no GUI open, so typing Ctrl in chat
 * never flips the latch. Disabling the config or leaving the world releases the spoof
 * and re-arms the latch for next time. Any failure self-disables back to vanilla.
 */
public final class VertexToggleSprint
{
    private static final ToggleSprintState STATE = new ToggleSprintState();

    public static long latchFlips;

    private static boolean disabled = false;
    private static boolean resolved = false;
    private static boolean spoofing = false;

    private static Field theWorld;
    private static Field currentScreen;
    private static Field gameSettings;
    private static Field keyBindSprint;
    private static Field keyCode;
    private static Field pressed;

    /** Once per frame from the harness tick, ahead of input processing and entity ticks. */
    public static void tick(Object minecraft)
    {
        if (disabled)
        {
            return;
        }

        try
        {
            if (!resolved)
            {
                resolve(minecraft);
            }

            Object binding = keyBindSprint.get(gameSettings.get(minecraft));

            if (!VertexConfig.enabled("toggleSprint") || theWorld.get(minecraft) == null)
            {
                release(binding);
                STATE.reset();
                return;
            }

            boolean raw = keyDown(keyCode.getInt(binding));

            if (currentScreen.get(minecraft) == null && STATE.sample(raw))
            {
                ++latchFlips;
            }

            if (STATE.latched())
            {
                // Written at the head of the game loop, before input events and entity
                // ticks, so the sprint gate in the player's living update sees the key
                // held this frame. A physical key-up event later in the frame is
                // re-covered next frame; vanilla keeps an ongoing sprint through that
                // gap on its own (the key only gates STARTING a sprint).
                pressed.setBoolean(binding, true);
                spoofing = true;
            }
            else if (spoofing)
            {
                pressed.setBoolean(binding, raw);
                spoofing = false;
            }
        }
        catch (Exception e)
        {
            disable(e);
        }
    }

    /** Vanilla key-code convention: positive = keyboard, negative = mouse button + 100. */
    private static boolean keyDown(int code)
    {
        if (code > 0)
        {
            return Keyboard.isKeyDown(code);
        }

        return code < 0 && Mouse.isButtonDown(code + 100);
    }

    private static void release(Object binding) throws Exception
    {
        if (spoofing)
        {
            // Hand the flag back to the real key state; the next physical key event
            // re-asserts it either way.
            pressed.setBoolean(binding, false);
            spoofing = false;
        }
    }

    private static void resolve(Object minecraft) throws Exception
    {
        Class<?> mcClass = minecraft.getClass();
        theWorld = mcClass.getDeclaredField(Mappings.MC_THE_WORLD);
        theWorld.setAccessible(true);
        currentScreen = mcClass.getDeclaredField(Mappings.MC_CURRENT_SCREEN);
        currentScreen.setAccessible(true);
        gameSettings = mcClass.getDeclaredField(Mappings.MC_GAME_SETTINGS);
        gameSettings.setAccessible(true);

        Object settings = gameSettings.get(minecraft);
        keyBindSprint = settings.getClass().getDeclaredField(Mappings.GS_KEY_BIND_SPRINT);
        keyBindSprint.setAccessible(true);

        // The KeyBinding class comes from the live field's type - hooks never
        // Class.forName (the tweaker and the game sit on different class loaders).
        Class<?> bindingClass = keyBindSprint.getType();
        keyCode = bindingClass.getDeclaredField(Mappings.KB_KEY_CODE);
        keyCode.setAccessible(true);
        pressed = bindingClass.getDeclaredField(Mappings.KB_PRESSED);
        pressed.setAccessible(true);
        resolved = true;
    }

    private static void disable(Exception e)
    {
        disabled = true;
        LogWrapper.severe("[Vertex] ToggleSprint disabled after failure");
        e.printStackTrace();
    }

    private VertexToggleSprint()
    {
    }
}
