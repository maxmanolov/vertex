package vertex.hooks;

import java.lang.reflect.Field;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;
import vertex.render.CloudCacheState;

/**
 * Client-thread cloud geometry cache. A miss records the complete vanilla cloud pass
 * into one owned display list while executing it. Hits replay the list under only the
 * translation accumulated from camera movement and the clouds' fixed eastward drift.
 */
public final class VertexCloudCache
{
    /** Test/benchmark A/B switch; production defaults to the cache. */
    private static final boolean ACTIVE = Boolean.parseBoolean(
        System.getProperty("vertex.cloudCache", "true"));
    private static final CloudCacheState state = new CloudCacheState();

    private static boolean disabled;
    private static boolean compiling;
    private static int list;
    private static Class<?> ownerClass;
    private static Class<?> cameraClass;

    private static Field renderMinecraft;
    private static Field cloudTickCounter;
    private static Field minecraftWorld;
    private static Field minecraftSettings;
    private static Field minecraftCamera;
    private static Field fancyGraphics;
    private static Field anaglyph;
    private static Field cameraLastX;
    private static Field cameraLastY;
    private static Field cameraLastZ;
    private static Field cameraX;
    private static Field cameraY;
    private static Field cameraZ;

    public static boolean replay(Object renderGlobal, float partialTick)
    {
        if (!ACTIVE || disabled)
        {
            return false;
        }

        try
        {
            Context context = context(renderGlobal, partialTick);

            if (state.reusable(renderGlobal, context.world, context.mode, context.tick))
            {
                GL11.glPushMatrix();

                try
                {
                    GL11.glTranslated(
                        -state.deltaX(context.tick, partialTick, context.x),
                        -state.deltaY(context.y),
                        -state.deltaZ(context.z));
                    GL11.glCallList(list);
                }
                finally
                {
                    GL11.glPopMatrix();
                }

                return true;
            }

            if (list == 0)
            {
                list = GL11.glGenLists(1);

                if (list == 0)
                {
                    throw new IllegalStateException("No OpenGL display list was available for cloud caching");
                }
            }

            state.capture(renderGlobal, context.world, context.mode, context.tick, partialTick,
                context.x, context.y, context.z);
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            compiling = true;
            return false;
        }
        catch (Throwable failure)
        {
            disable("start or replay", failure);
            return false;
        }
    }

    /** Closes a list only for a miss; hit returns are deliberately woven without this call. */
    public static void finish(Object renderGlobal, float partialTick)
    {
        if (!compiling)
        {
            return;
        }

        try
        {
            GL11.glEndList();
            compiling = false;
        }
        catch (Throwable failure)
        {
            compiling = false;
            disable("finish", failure);
        }
    }

    /** Renderer-grid changes invalidate geometry and release its native GL name. */
    public static void reset(Object renderGlobal)
    {
        reset();
    }

    /** Resource reloads can replace the texture id captured by the display list. */
    public static void reset()
    {
        closeOpenList();
        state.clear();

        if (list != 0)
        {
            try
            {
                GL11.glDeleteLists(list, 1);
            }
            catch (Throwable ignored)
            {
                // A lost GL context already reclaimed the name.
            }

            list = 0;
        }
    }

    private static Context context(Object renderGlobal, float partialTick) throws Exception
    {
        if (ownerClass != renderGlobal.getClass())
        {
            resolveOwner(renderGlobal.getClass());
        }

        Object minecraft = renderMinecraft.get(renderGlobal);
        Object world = minecraftWorld.get(minecraft);
        Object settings = minecraftSettings.get(minecraft);
        Object camera = minecraftCamera.get(minecraft);

        if (world == null || settings == null || camera == null)
        {
            throw new IllegalStateException("Cloud render state is incomplete");
        }

        if (cameraClass != camera.getClass())
        {
            resolveCamera(camera.getClass());
        }

        int tick = cloudTickCounter.getInt(renderGlobal);
        int mode = (fancyGraphics.getBoolean(settings) ? 1 : 0)
            | (anaglyph.getBoolean(settings) ? 2 : 0);
        double x = interpolate(cameraLastX.getDouble(camera), cameraX.getDouble(camera), partialTick);
        double y = interpolate(cameraLastY.getDouble(camera), cameraY.getDouble(camera), partialTick);
        double z = interpolate(cameraLastZ.getDouble(camera), cameraZ.getDouble(camera), partialTick);
        return new Context(world, mode, tick, x, y, z);
    }

    private static void resolveOwner(Class<?> renderGlobalClass) throws Exception
    {
        reset();
        renderMinecraft = accessible(renderGlobalClass, Mappings.RG_MC);
        cloudTickCounter = accessible(renderGlobalClass, Mappings.RG_CLOUD_TICK_COUNTER);
        Class<?> minecraftClass = renderMinecraft.getType();
        minecraftWorld = accessible(minecraftClass, Mappings.MC_THE_WORLD);
        minecraftSettings = accessible(minecraftClass, Mappings.MC_GAME_SETTINGS);
        minecraftCamera = accessible(minecraftClass, Mappings.MINECRAFT_RENDER_VIEW_ENTITY);
        Class<?> settingsClass = minecraftSettings.getType();
        fancyGraphics = accessible(settingsClass, Mappings.GS_FANCY_GRAPHICS);
        anaglyph = accessible(settingsClass, Mappings.GS_ANAGLYPH);
        ownerClass = renderGlobalClass;
    }

    private static void resolveCamera(Class<?> type) throws Exception
    {
        cameraLastX = accessibleHierarchy(type, Mappings.ENTITY_LAST_TICK_POS_X);
        // Vanilla's cloud fold interpolates Y from prevPosY (T), unlike X/Z which use
        // the lastTickPos pair - see the RG_CLOUD_TICK_COUNTER mapping evidence.
        cameraLastY = accessibleHierarchy(type, Mappings.ENTITY_PREV_POS_Y);
        cameraLastZ = accessibleHierarchy(type, Mappings.ENTITY_LAST_TICK_POS_Z);
        cameraX = accessibleHierarchy(type, Mappings.ENTITY_POS_X);
        cameraY = accessibleHierarchy(type, Mappings.ENTITY_POS_Y);
        cameraZ = accessibleHierarchy(type, Mappings.ENTITY_POS_Z);
        cameraClass = type;
    }

    private static Field accessible(Class<?> type, String name) throws NoSuchFieldException
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Field accessibleHierarchy(Class<?> type, String name) throws NoSuchFieldException
    {
        Class<?> cursor = type;

        while (cursor != null)
        {
            try
            {
                return accessible(cursor, name);
            }
            catch (NoSuchFieldException ignored)
            {
                cursor = cursor.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static double interpolate(double previous, double current, float partialTick)
    {
        return previous + (current - previous) * (double)partialTick;
    }

    private static void disable(String stage, Throwable failure)
    {
        if (!disabled)
        {
            LogWrapper.severe("[Vertex] Cloud cache disabled after " + stage + " failure: " + failure);
        }

        closeOpenList();
        state.clear();
        disabled = true;
    }

    private static void closeOpenList()
    {
        if (compiling)
        {
            try
            {
                GL11.glEndList();
            }
            catch (Throwable ignored)
            {
                // Best effort: subsequent rendering must not remain captured.
            }

            compiling = false;
        }
    }

    private static final class Context
    {
        final Object world;
        final int mode;
        final int tick;
        final double x;
        final double y;
        final double z;

        Context(Object world, int mode, int tick, double x, double y, double z)
        {
            this.world = world;
            this.mode = mode;
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private VertexCloudCache()
    {
    }
}
