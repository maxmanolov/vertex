package vertex.transform;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Dispatches per-class bytecode patches. Vertex targets the obfuscated (notch) names of the
 * vanilla 1.7.10 client only; in any other environment the names will not match and every
 * class passes through untouched.
 */
public class VertexTransformer implements IClassTransformer
{
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass)
    {
        if (basicClass == null)
        {
            return null;
        }

        try
        {
            if (Mappings.WORLD_RENDERER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching WorldRenderer (" + name + ")");
                return WorldRendererPatch.apply(basicClass);
            }

            if (Mappings.RENDER_GLOBAL.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderGlobal (" + name + ")");
                byte[] patched = RenderGlobalPatch.apply(basicClass);
                return SkipMethodPatch.apply(patched, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.RG_RENDER_SKY, Mappings.RG_RENDER_SKY_DESC, "sky"),
                    new SkipMethodPatch.Target(Mappings.RG_RENDER_CLOUDS, Mappings.RG_RENDER_CLOUDS_DESC, "clouds"),
                });
            }

            if (Mappings.ENTITY_RENDERER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching EntityRenderer (" + name + ")");
                return SkipMethodPatch.apply(basicClass, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.ER_RENDER_RAIN_SNOW, Mappings.ER_RENDER_RAIN_SNOW_DESC, "weather"),
                    new SkipMethodPatch.Target(Mappings.ER_ADD_RAIN_PARTICLES, Mappings.ER_ADD_RAIN_PARTICLES_DESC, "weather"),
                });
            }

            if (Mappings.TEXTURE_MAP.equals(name))
            {
                LogWrapper.info("[Vertex] Patching TextureMap (" + name + ")");
                return SkipMethodPatch.apply(basicClass, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.TM_UPDATE_ANIMATIONS, Mappings.TM_UPDATE_ANIMATIONS_DESC, "textureAnimations"),
                });
            }

            if (Mappings.WORLD_CLIENT.equals(name))
            {
                LogWrapper.info("[Vertex] Patching WorldClient (" + name + ")");
                return SkipMethodPatch.apply(basicClass, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.WC_DO_VOID_FOG_PARTICLES, Mappings.WC_DO_VOID_FOG_PARTICLES_DESC, "voidParticles"),
                });
            }
        }
        catch (Exception e)
        {
            // A failed patch must never take the game down; fall back to vanilla bytes.
            LogWrapper.severe("[Vertex] Failed to patch " + name + ", leaving class unmodified");
            e.printStackTrace();
        }

        return basicClass;
    }
}
