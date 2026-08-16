package vertex.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.launchwrapper.LogWrapper;
import org.lwjgl.opengl.GL11;
import vertex.Mappings;

/**
 * Quality-page hooks: the terrain atlas mipmap filter and the swamp color guard.
 *
 * Mipmap Type only ever swaps between the two mipmapped minification filters; when the
 * atlas has no mipmaps (mipmap levels 0 leaves a non-mipmap filter) it changes nothing,
 * so it can never break sampling. Swamp Colors gates the swamp biome's special-cased
 * grass/foliage constants; off falls through to the base colormap path, which composes
 * with the custom-colors interception.
 */
public final class VertexQuality
{
    private static boolean disabled = false;

    private static Field atlasType;
    private static Method getGlTextureId;
    private static int terrainAtlasId = -1;

    /** Tail of TextureMap.loadTextureAtlas: remember the terrain atlas, apply the filter. */
    public static void afterAtlasLoad(Object textureMap)
    {
        if (disabled)
        {
            return;
        }

        try
        {
            if (atlasType == null)
            {
                atlasType = textureMap.getClass().getDeclaredField(Mappings.TM_TYPE);
                atlasType.setAccessible(true);
                getGlTextureId = textureMap.getClass().getMethod(Mappings.TEXTURE_GL_ID);
                getGlTextureId.setAccessible(true);
            }

            if (atlasType.getInt(textureMap) == 0)
            {
                terrainAtlasId = ((Integer)getGlTextureId.invoke(textureMap)).intValue();
                applyMipmapType();
            }
        }
        catch (Throwable t)
        {
            disabled = true;
            LogWrapper.severe("[Vertex] Quality hooks disabled after failure");
            t.printStackTrace();
        }
    }

    /** Applies the configured filter to the cached terrain atlas; safe to call live. */
    public static void applyMipmapType()
    {
        if (disabled || terrainAtlasId < 0)
        {
            return;
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrainAtlasId);
        int current = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int target = mipmapFilter(VertexConfig.value("mipmapType", "nearest"), current);

        if (target != current)
        {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, target);
        }
    }

    /** Guard for the swamp color special case: true keeps vanilla's swamp constants. */
    public static boolean swampColors()
    {
        return VertexConfig.enabled("swampColors");
    }

    // ---- pure decision logic (unit-tested) -------------------------------------------

    /**
     * Swaps only between the two mipmapped filters; any non-mipmap current filter
     * (atlas built with zero mipmap levels) is left exactly as it is.
     */
    static int mipmapFilter(String mode, int currentFilter)
    {
        boolean mipmapped = currentFilter == GL11.GL_NEAREST_MIPMAP_LINEAR
            || currentFilter == GL11.GL_LINEAR_MIPMAP_LINEAR
            || currentFilter == GL11.GL_NEAREST_MIPMAP_NEAREST
            || currentFilter == GL11.GL_LINEAR_MIPMAP_NEAREST;

        if (!mipmapped)
        {
            return currentFilter;
        }

        String trimmed = mode == null ? "" : mode.trim();
        return trimmed.equals("linear") ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_LINEAR;
    }

    private VertexQuality()
    {
    }
}
