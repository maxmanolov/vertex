package vertex.transform;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.Mappings;

/**
 * Dispatches per-class bytecode patches. Vertex targets the obfuscated (notch) names of the
 * vanilla 1.7.10 client only; in any other environment the names will not match and every
 * class passes through untouched. Named patches run first, then the Tessellator redirect
 * sweeps every class (identity redirect until multi-core chunk building lands).
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

        byte[] result = basicClass;

        try
        {
            if (Mappings.WORLD_RENDERER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching WorldRenderer (" + name + ")");
                result = WorldRendererPatch.apply(result);
                result = HeadGuardPatch.apply(result, Mappings.WR_UPDATE_RENDERER, Mappings.WR_UPDATE_RENDERER_DESC,
                    "vertex/hooks/VertexMulticore", "interceptUpdate", HeadGuardPatch.THIS_AND_OBJECT);
                result = HeadGuardPatch.apply(result, Mappings.WR_PRE_RENDER_BLOCKS, Mappings.WR_PRE_RENDER_BLOCKS_DESC,
                    "vertex/hooks/VertexMulticore", "interceptPreRender", HeadGuardPatch.THIS_AND_INT);
                result = HeadGuardPatch.apply(result, Mappings.WR_POST_RENDER_BLOCKS, Mappings.WR_POST_RENDER_BLOCKS_DESC,
                    "vertex/hooks/VertexMulticore", "interceptPostRender", HeadGuardPatch.THIS_AND_INT);
            }
            else if (Mappings.RENDER_GLOBAL.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderGlobal (" + name + ")");
                result = RenderGlobalPatch.apply(result);
                result = SkipMethodPatch.apply(result, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.RG_RENDER_SKY, Mappings.RG_RENDER_SKY_DESC, "sky"),
                    new SkipMethodPatch.Target(Mappings.RG_RENDER_CLOUDS, Mappings.RG_RENDER_CLOUDS_DESC, "clouds"),
                });
            }
            else if (Mappings.ENTITY_RENDERER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching EntityRenderer (" + name + ")");
                result = SkipMethodPatch.apply(result, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.ER_RENDER_RAIN_SNOW, Mappings.ER_RENDER_RAIN_SNOW_DESC, "weather"),
                    new SkipMethodPatch.Target(Mappings.ER_ADD_RAIN_PARTICLES, Mappings.ER_ADD_RAIN_PARTICLES_DESC, "weather"),
                });
                result = TailCallPatch.apply(result, Mappings.ER_SETUP_FOG, Mappings.ER_SETUP_FOG_DESC, "vertex/hooks/VertexHooks", "afterFogSetup");
            }
            else if (Mappings.TEXTURE_MAP.equals(name))
            {
                LogWrapper.info("[Vertex] Patching TextureMap (" + name + ")");
                result = SkipMethodPatch.apply(result, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.TM_UPDATE_ANIMATIONS, Mappings.TM_UPDATE_ANIMATIONS_DESC, "textureAnimations"),
                });
            }
            else if (Mappings.MINECRAFT.equals(name))
            {
                LogWrapper.info("[Vertex] Patching Minecraft (" + name + ")");
                result = HeadInstanceCallPatch.apply(result, Mappings.MC_RUN_GAME_LOOP, Mappings.MC_RUN_GAME_LOOP_DESC, "vertex/hooks/VertexTestHarness", "tick");
            }
            else if (Mappings.WORLD_CLIENT.equals(name))
            {
                LogWrapper.info("[Vertex] Patching WorldClient (" + name + ")");
                result = SkipMethodPatch.apply(result, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.WC_DO_VOID_FOG_PARTICLES, Mappings.WC_DO_VOID_FOG_PARTICLES_DESC, "voidParticles"),
                });
            }

            // Applies to every class, including the ones patched above: rewrite reads of
            // the shared Tessellator through VertexTessellator.
            result = TessellatorRedirectPatch.process(name, result);
            // Fast Render investigation: count GL state calls and their redundancy.
            result = GLCallCountPatch.process(result);
        }
        catch (Exception e)
        {
            // A failed patch must never take the game down; fall back to vanilla bytes.
            LogWrapper.severe("[Vertex] Failed to patch " + name + ", leaving class unmodified");
            e.printStackTrace();
            return basicClass;
        }

        return result;
    }
}
