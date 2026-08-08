package vertex.hooks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import vertex.Mappings;

/**
 * Freelook: while the Freelook key is held, the player's heading, movement direction and
 * crosshair aim stay frozen and the mouse orbits a third-person camera instead; releasing
 * the key snaps straight back to the prior view and perspective.
 *
 * Mechanism, two scoped patches on EntityRenderer:
 *
 * - updateCameraAndRender's two mouse-look call sites (plain and cinematic) are rerouted
 *   to {@link #setAngles}: while freelook is active the delta feeds the orbit via
 *   {@link FreelookState} and the player's rotation is simply never written - nothing to
 *   restore, and the wire sees the same unchanged rotation that standing still sends.
 * - renderWorld is bracketed by {@link #beginRenderWorld}/{@link #endRenderWorld}, which
 *   swap the view entity's rotation quartet (yaw/pitch and their interpolation anchors)
 *   to the orbit angles for exactly the camera-and-world-render window. Aim picking
 *   (getMouseOver) runs before that window inside updateCameraAndRender and every game
 *   tick runs outside it, so interactions and physics always read the true heading. The
 *   next frame's entry points also restore defensively, so an exception escaping
 *   renderWorld cannot leak camera angles into the player.
 *
 * The keybind is a real vanilla KeyBinding appended to GameSettings.keyBindings: the
 * Controls screen lists it under Miscellaneous, rebinding works, saveOptions persists it
 * as key_Freelook, and this class re-applies the persisted code once at startup (vanilla
 * loadOptions ran before the binding existed). Perspective is forced to third-person rear
 * while held and restored on release. Every failure self-disables back to vanilla.
 */
public final class VertexFreelook
{
    private static final String BINDING_NAME = "Freelook";
    private static final FreelookState STATE = new FreelookState();

    public static long activations;

    private static boolean disabled = false;
    private static boolean resolved = false;
    private static boolean registered = false;

    private static Object minecraft;
    private static Field theWorld;
    private static Field currentScreen;
    private static Field gameSettings;
    private static Field renderViewEntity;
    private static Field keyBindings;
    private static Field thirdPersonView;
    private static Field keyCode;
    private static Method resetKeyHash;
    private static Constructor<?> bindingCtor;
    private static Object binding;

    private static Field rotationYaw;
    private static Field rotationPitch;
    private static Field prevRotationYaw;
    private static Field prevRotationPitch;
    private static Method vanillaSetAngles;

    private static boolean spoofPushed = false;
    private static Object spoofedEntity;
    private static float savedYaw;
    private static float savedPitch;
    private static float savedPrevYaw;
    private static float savedPrevPitch;

    private static boolean perspectiveForced = false;
    private static int savedPerspective;

    /** Once per frame from the harness tick: resolve, register the keybind, self-heal. */
    public static void tick(Object mc)
    {
        if (disabled)
        {
            return;
        }

        minecraft = mc;

        try
        {
            if (!resolved)
            {
                resolve(mc);
            }

            if (!registered)
            {
                register(mc);
            }

            if (spoofPushed)
            {
                // Belt and braces: endRenderWorld restores every frame; reaching a tick
                // with the spoof still pushed means an exception tore the render frame.
                restoreAngles();
            }
        }
        catch (Exception e)
        {
            disable("init", e);
        }
    }

    /**
     * Reroute target for updateCameraAndRender's mouse-look call sites. Receives the
     * player and the processed deltas (sensitivity and invert already applied). Edge
     * detection runs here first so the very first delta after a key press is absorbed
     * by the orbit, never leaked into the player's heading.
     */
    public static void setAngles(Object player, float dx, float dy)
    {
        if (!disabled)
        {
            update();

            if (STATE.active())
            {
                STATE.consume(dx, dy);
                return;
            }
        }

        try
        {
            if (vanillaSetAngles == null)
            {
                vanillaSetAngles = player.getClass().getMethod(
                    Mappings.ENTITY_SET_ANGLES, float.class, float.class);
            }

            vanillaSetAngles.invoke(player, Float.valueOf(dx), Float.valueOf(dy));
        }
        catch (Exception e)
        {
            // Unreachable where the patch applied (setAngles is public on the vanilla
            // entity); disable logs once, the pass-through keeps being attempted so a
            // broken environment fails loudly rather than silently eating mouse input.
            disable("setAngles", e);
        }
    }

    /** Head of renderWorld: run the state machine, then spoof the camera quartet. */
    public static void beginRenderWorld(Object entityRenderer)
    {
        if (disabled)
        {
            return;
        }

        if (spoofPushed)
        {
            restoreAngles();
        }

        // Also runs here because the mouse-look sites are skipped while a GUI is open
        // or the window is unfocused; releasing the key must still deactivate then.
        update();

        if (!STATE.active())
        {
            return;
        }

        try
        {
            Object view = renderViewEntity.get(minecraft);

            if (view == null)
            {
                return;
            }

            if (rotationYaw == null)
            {
                resolveRotation(view);
            }

            savedYaw = rotationYaw.getFloat(view);
            savedPitch = rotationPitch.getFloat(view);
            savedPrevYaw = prevRotationYaw.getFloat(view);
            savedPrevPitch = prevRotationPitch.getFloat(view);
            // Current value and interpolation anchor both take the orbit angle, so the
            // camera sits exactly on it with no partial-tick smearing.
            rotationYaw.setFloat(view, STATE.yaw());
            prevRotationYaw.setFloat(view, STATE.yaw());
            rotationPitch.setFloat(view, STATE.pitch());
            prevRotationPitch.setFloat(view, STATE.pitch());
            spoofedEntity = view;
            spoofPushed = true;
        }
        catch (Exception e)
        {
            disable("push", e);
        }
    }

    /** Before every return of renderWorld: put the true heading back. */
    public static void endRenderWorld(Object entityRenderer)
    {
        if (spoofPushed)
        {
            restoreAngles();
        }
    }

    private static void update()
    {
        Object mc = minecraft;

        if (mc == null || !resolved)
        {
            return;
        }

        try
        {
            boolean held = false;

            if (registered && VertexConfig.enabled("freelook")
                && theWorld.get(mc) != null && currentScreen.get(mc) == null)
            {
                held = keyDown(keyCode.getInt(binding));
            }

            if (held && !STATE.active())
            {
                Object view = renderViewEntity.get(mc);

                if (view == null)
                {
                    return;
                }

                if (rotationYaw == null)
                {
                    resolveRotation(view);
                }

                STATE.activate(rotationYaw.getFloat(view), rotationPitch.getFloat(view));
                Object settings = gameSettings.get(mc);
                savedPerspective = thirdPersonView.getInt(settings);
                thirdPersonView.setInt(settings, 1);
                perspectiveForced = true;
                ++activations;
            }
            else if (!held && STATE.active())
            {
                STATE.deactivate();

                if (perspectiveForced)
                {
                    thirdPersonView.setInt(gameSettings.get(mc), savedPerspective);
                    perspectiveForced = false;
                }
            }
        }
        catch (Exception e)
        {
            disable("update", e);
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

    private static void resolve(Object mc) throws Exception
    {
        Class<?> mcClass = mc.getClass();
        theWorld = mcClass.getDeclaredField(Mappings.MC_THE_WORLD);
        theWorld.setAccessible(true);
        currentScreen = mcClass.getDeclaredField(Mappings.MC_CURRENT_SCREEN);
        currentScreen.setAccessible(true);
        gameSettings = mcClass.getDeclaredField(Mappings.MC_GAME_SETTINGS);
        gameSettings.setAccessible(true);
        renderViewEntity = mcClass.getDeclaredField(Mappings.MINECRAFT_RENDER_VIEW_ENTITY);
        renderViewEntity.setAccessible(true);

        Object settings = gameSettings.get(mc);
        keyBindings = settings.getClass().getDeclaredField(Mappings.GS_KEY_BINDINGS);
        keyBindings.setAccessible(true);
        thirdPersonView = settings.getClass().getDeclaredField(Mappings.GS_THIRD_PERSON_VIEW);
        thirdPersonView.setAccessible(true);

        // The KeyBinding class comes from the live array's component type - hooks never
        // Class.forName (the tweaker and the game sit on different class loaders).
        Class<?> bindingClass = keyBindings.getType().getComponentType();
        keyCode = bindingClass.getDeclaredField(Mappings.KB_KEY_CODE);
        keyCode.setAccessible(true);
        resetKeyHash = bindingClass.getMethod(Mappings.KB_RESET_HASH);
        bindingCtor = bindingClass.getConstructor(String.class, int.class, String.class);
        resolved = true;
    }

    private static void resolveRotation(Object view) throws Exception
    {
        // Public fields on the entity base class; getField walks the hierarchy.
        Class<?> cls = view.getClass();
        rotationYaw = cls.getField(Mappings.ENTITY_ROTATION_YAW);
        rotationPitch = cls.getField(Mappings.ENTITY_ROTATION_PITCH);
        prevRotationYaw = cls.getField(Mappings.ENTITY_PREV_ROTATION_YAW);
        prevRotationPitch = cls.getField(Mappings.ENTITY_PREV_ROTATION_PITCH);
    }

    private static void register(Object mc) throws Exception
    {
        // The constructor self-registers in KeyBinding's static list, key hash and
        // category set; appending to GameSettings.keyBindings is what makes the Controls
        // screen list it and saveOptions persist it.
        binding = bindingCtor.newInstance(BINDING_NAME, Integer.valueOf(Keyboard.KEY_LMENU),
            "key.categories.misc");
        Object settings = gameSettings.get(mc);
        Object old = keyBindings.get(settings);
        int length = Array.getLength(old);
        Object grown = Array.newInstance(old.getClass().getComponentType(), length + 1);
        System.arraycopy(old, 0, grown, 0, length);
        Array.set(grown, length, binding);
        keyBindings.set(settings, grown);

        // Vanilla loadOptions ran before this binding existed, so a persisted rebind
        // (key_Freelook:NN written by saveOptions) must be re-applied by hand once.
        int persisted = persistedKeyCode();

        if (persisted != Integer.MIN_VALUE)
        {
            keyCode.setInt(binding, persisted);
            resetKeyHash.invoke(null);
        }

        registered = true;
        LogWrapper.info("[Vertex] Freelook keybind registered (Controls > Miscellaneous, key code "
            + keyCode.getInt(binding) + ")");
    }

    private static int persistedKeyCode()
    {
        File options = new File(Launch.minecraftHome, "options.txt");

        if (!options.isFile())
        {
            return Integer.MIN_VALUE;
        }

        String prefix = "key_" + BINDING_NAME + ":";
        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new FileReader(options));

            for (String line = reader.readLine(); line != null; line = reader.readLine())
            {
                if (line.startsWith(prefix))
                {
                    return Integer.parseInt(line.substring(prefix.length()).trim());
                }
            }
        }
        catch (Exception e)
        {
            LogWrapper.warning("[Vertex] Freelook could not read the persisted keybind: " + e);
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (Exception ignored)
                {
                }
            }
        }

        return Integer.MIN_VALUE;
    }

    private static void restoreAngles()
    {
        try
        {
            if (spoofedEntity != null)
            {
                rotationYaw.setFloat(spoofedEntity, savedYaw);
                rotationPitch.setFloat(spoofedEntity, savedPitch);
                prevRotationYaw.setFloat(spoofedEntity, savedPrevYaw);
                prevRotationPitch.setFloat(spoofedEntity, savedPrevPitch);
            }
        }
        catch (Exception e)
        {
            disable("restore", e);
        }
        finally
        {
            spoofPushed = false;
            spoofedEntity = null;
        }
    }

    private static void disable(String site, Exception e)
    {
        if (disabled)
        {
            return;
        }

        disabled = true;
        STATE.deactivate();

        try
        {
            if (spoofPushed)
            {
                restoreAngles();
            }

            if (perspectiveForced)
            {
                thirdPersonView.setInt(gameSettings.get(minecraft), savedPerspective);
                perspectiveForced = false;
            }
        }
        catch (Exception ignored)
        {
        }

        LogWrapper.severe("[Vertex] Freelook disabled after failure at " + site);
        e.printStackTrace();
    }

    private VertexFreelook()
    {
    }
}
