package vertex.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;
import vertex.sky.SkyLayer;

/**
 * Draws loaded custom sky layers at the tail of the vanilla sky pass. Each layer is a
 * textured box around the viewer, faded by its day-clock window and rotated with the
 * celestial angle, drawn with the layer's blend mode and no depth writes so terrain and
 * clouds still occlude correctly.
 *
 * The face UV layout follows the conventional 3x2 cell arrangement used by sky packs.
 * That mapping is a calibration seam: it is mechanically verifiable (draws issue, no GL
 * errors) but its correctness is visual, so it is flagged for the same fly-through pass
 * that confirms multi-core rendering. Self-disables on any failure.
 */
public final class VertexSky
{
    /** Cell (column,row) in the 3x2 texture grid for each box face, and its axis rotation. */
    private static final int[][] FACE_CELLS = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}};

    public static long draws = 0L;

    private static boolean disabled = false;
    private static boolean ready = false;
    private static Method getTextureManager;
    private static Method bindTexture;
    private static Method getWorldTime;
    private static Method getCelestialAngle;
    private static Constructor<?> locationCtor;
    private static final Map<String, Object> locations = new HashMap<String, Object>();

    public static void renderLayers(Object renderGlobal)
    {
        List<SkyLayer> layers = VertexPackLoader.skyLayers;

        if (disabled || layers.isEmpty() || !VertexConfig.enabled("customSky"))
        {
            return;
        }

        try
        {
            Object minecraft = VertexHooks.minecraftOf(renderGlobal);
            Object world = VertexHooks.worldOf(minecraft);

            if (minecraft == null || world == null)
            {
                return;
            }

            if (!ready)
            {
                initialize(minecraft, world);
            }

            long worldTime = ((Long)getWorldTime.invoke(world)).longValue();
            float celestial = ((Float)getCelestialAngle.invoke(world, Float.valueOf(1.0F))).floatValue();
            Object textureManager = getTextureManager.invoke(minecraft);
            boolean drewAny = false;

            for (SkyLayer layer : layers)
            {
                float opacity = layer.timing.opacity(worldTime);

                if (opacity <= 0.001F)
                {
                    continue;
                }

                if (!drewAny)
                {
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glDepthMask(false);
                    GL11.glEnable(GL11.GL_BLEND);
                    drewAny = true;
                }

                if (layer.blend.equals("alpha"))
                {
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                }
                else
                {
                    GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                }

                GL11.glColor4f(1.0F, 1.0F, 1.0F, opacity);
                bindTexture.invoke(textureManager, location(layer.source));
                GL11.glPushMatrix();

                if (layer.rotate)
                {
                    GL11.glRotatef(celestial * 360.0F * layer.speed, layer.axis[0], layer.axis[1], layer.axis[2]);
                }

                drawBox();
                GL11.glPopMatrix();
                ++draws;
            }

            if (drewAny)
            {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDepthMask(true);
            }
        }
        catch (Throwable e)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Custom sky disabled after failure");
            e.printStackTrace();
        }
    }

    /** Six faces of a box around the origin, each sampling one cell of the 3x2 grid. */
    private static void drawBox()
    {
        float size = 100.0F;

        for (int face = 0; face < 6; ++face)
        {
            float u0 = FACE_CELLS[face][0] / 3.0F;
            float u1 = u0 + 1.0F / 3.0F;
            float v0 = FACE_CELLS[face][1] / 2.0F;
            float v1 = v0 + 0.5F;
            GL11.glPushMatrix();

            switch (face)
            {
                case 1:
                    GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
                    break;
                case 2:
                    GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
                    break;
                case 3:
                    GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
                    break;
                case 4:
                    GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
                    break;
                case 5:
                    GL11.glRotatef(-90.0F, 0.0F, 0.0F, 1.0F);
                    break;
                default:
                    break;
            }

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u0, v0);
            GL11.glVertex3f(-size, -size, -size);
            GL11.glTexCoord2f(u0, v1);
            GL11.glVertex3f(-size, -size, size);
            GL11.glTexCoord2f(u1, v1);
            GL11.glVertex3f(size, -size, size);
            GL11.glTexCoord2f(u1, v0);
            GL11.glVertex3f(size, -size, -size);
            GL11.glEnd();
            GL11.glPopMatrix();
        }
    }

    private static Object location(String source) throws Exception
    {
        Object cached = locations.get(source);

        if (cached == null)
        {
            cached = locationCtor.newInstance(source);
            locations.put(source, cached);
        }

        return cached;
    }

    private static void initialize(Object minecraft, Object world) throws Exception
    {
        getTextureManager = minecraft.getClass().getMethod(Mappings.MC_GET_TEXTURE_MANAGER);
        getTextureManager.setAccessible(true);
        Class<?> textureManager = getTextureManager.getReturnType();
        Class<?> locationClass = Class.forName(Mappings.RESOURCE_LOCATION, false, minecraft.getClass().getClassLoader());
        locationCtor = locationClass.getConstructor(String.class);
        bindTexture = textureManager.getMethod(Mappings.TEXTURE_BIND, locationClass);
        bindTexture.setAccessible(true);
        Class<?> worldRoot = world.getClass();

        while (worldRoot.getSuperclass() != null && worldRoot.getSuperclass() != Object.class)
        {
            worldRoot = worldRoot.getSuperclass();
        }

        getWorldTime = worldRoot.getDeclaredMethod(Mappings.WORLD_GET_TIME);
        getWorldTime.setAccessible(true);
        getCelestialAngle = worldRoot.getDeclaredMethod(Mappings.WORLD_CELESTIAL_ANGLE, Float.TYPE);
        getCelestialAngle.setAccessible(true);
        ready = true;
        LogWrapper.info("[Vertex] Custom sky armed (" + VertexPackLoader.skyLayers.size() + " layers)");
    }

    private VertexSky()
    {
    }
}
