package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;

/**
 * Clear Water: vanilla 1.7.10 water opacity lives entirely in the water sprites'
 * texture alpha - the liquid renderer tessellates opaque vertex colors, so the texel
 * alpha alone decides how see-through water is. With clearWater=true the two water
 * sprites' animation frames get their alpha scaled to {@link #ALPHA_PERCENT}% of
 * vanilla across every mipmap level, so each per-tick animation upload carries the
 * clearer pixels.
 *
 * Originals are deep-copied before the first mutation, making the toggle fully live
 * in both directions; a flip also re-uploads the current frame immediately, so it
 * lands even while texture animations are frozen. Resource reloads rebuild the
 * sprites, so the tail hook re-captures from scratch each time.
 */
public final class VertexClearWater
{
    /** Scaled water alpha, as a percentage of the pack's own alpha. */
    static final int ALPHA_PERCENT = 40;

    private static boolean disabled = false;

    private static Field mapType;
    private static Method mapGlTextureId;
    private static Field animatedSprites;
    private static Field spriteFrames;
    private static Field spriteWidth;
    private static Field spriteHeight;
    private static Field spriteOriginX;
    private static Field spriteOriginY;
    private static Field spriteFrameIndex;
    private static Method spriteName;
    private static Method uploadMipmap;

    private static final List<Object> waterSprites = new ArrayList<Object>();
    private static final List<int[][][]> originalFrames = new ArrayList<int[][][]>();
    private static int atlasId = -1;
    private static boolean appliedClear = false;

    /** Tail of TextureMap.loadTextureAtlas: re-capture the water sprites, then apply. */
    public static void afterAtlasLoad(Object textureMap)
    {
        if (disabled)
        {
            return;
        }

        try
        {
            if (mapType == null)
            {
                resolveMapHandles(textureMap);
            }

            if (mapType.getInt(textureMap) != 0)
            {
                return; // the item atlas has no water sprites
            }

            waterSprites.clear();
            originalFrames.clear();
            appliedClear = false;
            atlasId = ((Integer)mapGlTextureId.invoke(textureMap)).intValue();

            for (Object sprite : (List<?>)animatedSprites.get(textureMap))
            {
                if (spriteName == null)
                {
                    resolveSpriteHandles(sprite);
                }

                if (isWaterSprite((String)spriteName.invoke(sprite)))
                {
                    waterSprites.add(sprite);
                    originalFrames.add(copyFrames((List<?>)spriteFrames.get(sprite)));
                }
            }

            if (waterSprites.isEmpty())
            {
                LogWrapper.info("[Vertex] Clear water found no animated water sprites in this pack");
                return;
            }

            LogWrapper.info("[Vertex] Clear water armed (" + waterSprites.size() + " sprites)");

            if (VertexConfig.enabled("clearWater"))
            {
                applyState(true);
            }
        }
        catch (Throwable t)
        {
            disable(t);
        }
    }

    /** Menu flip / Reset entry: bring the frame data in line with the config key. */
    public static void applyFromMenu()
    {
        if (disabled || waterSprites.isEmpty())
        {
            return;
        }

        try
        {
            applyState(VertexConfig.enabled("clearWater"));
        }
        catch (Throwable t)
        {
            disable(t);
        }
    }

    private static void applyState(boolean clear) throws Exception
    {
        if (clear == appliedClear)
        {
            return;
        }

        for (int s = 0; s < waterSprites.size(); ++s)
        {
            List<?> frames = (List<?>)spriteFrames.get(waterSprites.get(s));
            int[][][] originals = originalFrames.get(s);

            for (int f = 0; f < originals.length && f < frames.size(); ++f)
            {
                blendFrame((int[][])frames.get(f), originals[f], clear, ALPHA_PERCENT);
            }
        }

        appliedClear = clear;
        uploadCurrentFrames();
    }

    /**
     * Re-upload each water sprite's current frame so the flip is visible immediately,
     * even when texture animations are frozen and no per-tick upload would run.
     */
    private static void uploadCurrentFrames() throws Exception
    {
        if (atlasId < 0)
        {
            return;
        }

        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasId);

        try
        {
            for (Object sprite : waterSprites)
            {
                List<?> frames = (List<?>)spriteFrames.get(sprite);
                int index = spriteFrameIndex.getInt(sprite);

                if (index < 0 || index >= frames.size())
                {
                    index = 0;
                }

                uploadMipmap.invoke(null, frames.get(index),
                    Integer.valueOf(spriteWidth.getInt(sprite)),
                    Integer.valueOf(spriteHeight.getInt(sprite)),
                    Integer.valueOf(spriteOriginX.getInt(sprite)),
                    Integer.valueOf(spriteOriginY.getInt(sprite)),
                    Boolean.FALSE, Boolean.FALSE);
            }
        }
        finally
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    // ---- pure frame logic (unit-tested) ------------------------------------------------

    /** Scales only the alpha channel; RGB bits pass through untouched. */
    static int scaleAlpha(int argb, int percent)
    {
        int alpha = (argb >>> 24) * percent / 100;
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Writes the clear or vanilla pixels into the live frame arrays in place, every
     * mipmap level included, so future animation uploads read the chosen state.
     */
    static void blendFrame(int[][] target, int[][] original, boolean clear, int percent)
    {
        for (int level = 0; level < original.length && level < target.length; ++level)
        {
            if (target[level] == null || original[level] == null)
            {
                continue;
            }

            for (int p = 0; p < original[level].length; ++p)
            {
                target[level][p] = clear ? scaleAlpha(original[level][p], percent) : original[level][p];
            }
        }
    }

    /** The terrain atlas names registered for the two water blocks. */
    static boolean isWaterSprite(String name)
    {
        return "water_still".equals(name) || "water_flow".equals(name);
    }

    static int[][][] copyFrames(List<?> frames)
    {
        int[][][] copy = new int[frames.size()][][];

        for (int f = 0; f < copy.length; ++f)
        {
            int[][] frame = (int[][])frames.get(f);
            copy[f] = new int[frame.length][];

            for (int level = 0; level < frame.length; ++level)
            {
                copy[f][level] = frame[level] == null ? null : frame[level].clone();
            }
        }

        return copy;
    }

    // ---- plumbing -----------------------------------------------------------------------

    private static void resolveMapHandles(Object textureMap) throws Exception
    {
        mapType = textureMap.getClass().getDeclaredField(Mappings.TM_TYPE);
        mapType.setAccessible(true);
        mapGlTextureId = textureMap.getClass().getMethod(Mappings.TEXTURE_GL_ID);
        mapGlTextureId.setAccessible(true);
        animatedSprites = textureMap.getClass().getDeclaredField(Mappings.TM_ANIMATED_SPRITES);
        animatedSprites.setAccessible(true);

        Class<?> textureUtil = net.minecraft.launchwrapper.Launch.classLoader
            .loadClass(Mappings.TEXTURE_UTIL);
        uploadMipmap = textureUtil.getDeclaredMethod(Mappings.TEX_UPLOAD, int[][].class,
            int.class, int.class, int.class, int.class, boolean.class, boolean.class);
        uploadMipmap.setAccessible(true);
    }

    private static void resolveSpriteHandles(Object sprite) throws Exception
    {
        Class<?> cls = sprite.getClass();

        // Custom packs may subclass the sprite; walk up to the class declaring the fields.
        while (cls.getSuperclass() != null && cls.getSuperclass() != Object.class)
        {
            cls = cls.getSuperclass();
        }

        spriteFrames = cls.getDeclaredField(Mappings.SPRITE_FRAMES);
        spriteFrames.setAccessible(true);
        spriteWidth = cls.getDeclaredField(Mappings.SPRITE_WIDTH);
        spriteWidth.setAccessible(true);
        spriteHeight = cls.getDeclaredField(Mappings.SPRITE_HEIGHT);
        spriteHeight.setAccessible(true);
        spriteOriginX = cls.getDeclaredField(Mappings.SPRITE_ORIGIN_X);
        spriteOriginX.setAccessible(true);
        spriteOriginY = cls.getDeclaredField(Mappings.SPRITE_ORIGIN_Y);
        spriteOriginY.setAccessible(true);
        spriteFrameIndex = cls.getDeclaredField(Mappings.SPRITE_FRAME_INDEX);
        spriteFrameIndex.setAccessible(true);
        spriteName = cls.getMethod(Mappings.SPRITE_NAME);
        spriteName.setAccessible(true);
    }

    private static void disable(Throwable t)
    {
        if (!disabled)
        {
            disabled = true;
            waterSprites.clear();
            originalFrames.clear();
            LogWrapper.severe("[Vertex] Clear water disabled after failure");
            t.printStackTrace();
        }
    }

    private VertexClearWater()
    {
    }
}
