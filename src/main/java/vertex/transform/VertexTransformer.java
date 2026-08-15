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
                result = HeadGuardPatch.apply(result, Mappings.WR_UPDATE_RENDERER_SORT, Mappings.WR_UPDATE_RENDERER_SORT_DESC,
                    "vertex/hooks/VertexMulticore", "interceptSort", HeadGuardPatch.THIS_AND_OBJECT);
                result = HeadGuardPatch.apply(result, Mappings.WR_PRE_RENDER_BLOCKS, Mappings.WR_PRE_RENDER_BLOCKS_DESC,
                    "vertex/hooks/VertexMulticore", "interceptPreRender", HeadGuardPatch.THIS_AND_INT);
                result = HeadGuardPatch.apply(result, Mappings.WR_POST_RENDER_BLOCKS, Mappings.WR_POST_RENDER_BLOCKS_DESC,
                    "vertex/hooks/VertexMulticore", "interceptPostRender", HeadGuardPatch.THIS_AND_INT);
            }
            else if (Mappings.RENDER_GLOBAL.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderGlobal (" + name + ")");
                result = RenderGlobalPatch.apply(result);
                result = HeadInstanceCallPatch.apply(result, Mappings.RG_LOAD_RENDERERS, Mappings.RG_LOAD_RENDERERS_DESC,
                    "vertex/hooks/VertexMulticore", "onRenderersReloadedHook");
                result = HeadGuardPatch.apply(result, Mappings.RG_MARK_BLOCK_FOR_RENDER_UPDATE, Mappings.RG_MARK_BLOCK_FOR_RENDER_UPDATE_DESC,
                    "vertex/hooks/VertexFullbright", "interceptLightRemark", HeadGuardPatch.THIS_ONLY);

                if (vertex.hooks.VertexRenderer.MANAGED)
                {
                    // Managed section-mesh pipeline: a backend that owns submission draws
                    // the pass here instead of vanilla's glCallLists batches. Woven before
                    // the profiler brackets so the brackets time whichever path runs.
                    result = HeadGuardPatch.apply(result, Mappings.RG_RENDER_ALL_LISTS, Mappings.RG_RENDER_ALL_LISTS_DESC,
                        "vertex/hooks/VertexRenderer", "interceptSubmit", HeadGuardPatch.THIS_INT_DOUBLE);
                }

                if (vertex.hooks.VertexRenderProfiler.ACTIVE)
                {
                    // Render-phase baselines for the backend work: time frustum clip,
                    // the sortAndRender walk, the glCallLists submission nested inside
                    // it, and the rebuild/upload pass. Off = no bracket is woven.
                    result = BracketPatch.apply(result, Mappings.RG_CLIP_FRUSTUM, Mappings.RG_CLIP_FRUSTUM_DESC,
                        "vertex/hooks/VertexRenderProfiler", vertex.hooks.VertexRenderProfiler.PHASE_CLIP);
                    result = BracketPatch.apply(result, Mappings.RG_SORT_AND_RENDER, Mappings.RG_SORT_AND_RENDER_DESC,
                        "vertex/hooks/VertexRenderProfiler", vertex.hooks.VertexRenderProfiler.PHASE_SORT);
                    result = BracketPatch.apply(result, Mappings.RG_RENDER_ALL_LISTS, Mappings.RG_RENDER_ALL_LISTS_DESC,
                        "vertex/hooks/VertexRenderProfiler", vertex.hooks.VertexRenderProfiler.PHASE_SUBMIT);
                    result = BracketPatch.apply(result, Mappings.RG_UPDATE_RENDERERS, Mappings.RG_UPDATE_RENDERERS_DESC,
                        "vertex/hooks/VertexRenderProfiler", vertex.hooks.VertexRenderProfiler.PHASE_UPDATE);
                }

                if (vertex.hooks.VertexMarkAudit.ACTIVE)
                {
                    // #118 forensics: attribute every section re-mark to its entry point.
                    result = HeadGuardPatch.apply(result, Mappings.RG_MARK_BLOCK_FOR_UPDATE, Mappings.RG_MARK_BLOCK_FOR_UPDATE_DESC,
                        "vertex/hooks/VertexMarkAudit", "onMarkUpdate", HeadGuardPatch.THIS_ONLY);
                    result = HeadGuardPatch.apply(result, Mappings.RG_MARK_BLOCK_FOR_RENDER_UPDATE, Mappings.RG_MARK_BLOCK_FOR_RENDER_UPDATE_DESC,
                        "vertex/hooks/VertexMarkAudit", "onMarkLight", HeadGuardPatch.THIS_ONLY);
                    result = HeadGuardPatch.apply(result, Mappings.RG_MARK_BLOCK_RANGE_FOR_RENDER_UPDATE, Mappings.RG_MARK_BLOCK_RANGE_FOR_RENDER_UPDATE_DESC,
                        "vertex/hooks/VertexMarkAudit", "onMarkRange", HeadGuardPatch.THIS_ONLY);
                    result = HeadGuardPatch.apply(result, Mappings.RG_MARK_BLOCKS_FOR_UPDATE, Mappings.RG_MARK_BLOCKS_FOR_UPDATE_DESC,
                        "vertex/hooks/VertexMarkAudit", "onFunnel", HeadGuardPatch.THIS_ONLY);
                }
                // Sky details: every bindTexture in renderSky reports its texture (a
                // sun/moon bind arms the next flush), every tessellator flush routes
                // through the armed gate, and the star display list drops at its
                // glCallList site. The cloud pass wraps in a matrix lift for the
                // cloud-height setting.
                result = RerouteVirtualInMethodPatch.apply(result,
                    Mappings.RG_RENDER_SKY, Mappings.RG_RENDER_SKY_DESC,
                    Mappings.TEXTURE_MANAGER, Mappings.TEXTURE_BIND,
                    "(L" + Mappings.RESOURCE_LOCATION + ";)V",
                    "vertex/hooks/VertexSkyDetails", "bindSkyTexture");
                result = RerouteVirtualInMethodPatch.apply(result,
                    Mappings.RG_RENDER_SKY, Mappings.RG_RENDER_SKY_DESC,
                    Mappings.TESSELLATOR, Mappings.TESS_DRAW, Mappings.TESS_DRAW_DESC,
                    "vertex/hooks/VertexSkyDetails", "skyDraw");
                result = RerouteStaticInMethodPatch.apply(result,
                    Mappings.RG_RENDER_SKY, Mappings.RG_RENDER_SKY_DESC,
                    "org/lwjgl/opengl/GL11", "glCallList", "(I)V",
                    "vertex/hooks/VertexSkyDetails", "skyCallList");
                result = TailInstanceCallPatch.apply(result, Mappings.RG_RENDER_CLOUDS, Mappings.RG_RENDER_CLOUDS_DESC,
                    "vertex/hooks/VertexSkyDetails", "afterClouds");
                result = HeadInstanceCallPatch.apply(result, Mappings.RG_RENDER_CLOUDS, Mappings.RG_RENDER_CLOUDS_DESC,
                    "vertex/hooks/VertexSkyDetails", "beforeClouds");
                // Tail hooks BEFORE head skips: a skip guard adds a synthetic early RETURN,
                // and a tail call attached to it would run the feature while its pass is
                // disabled - custom sky layers were observed drawing 5,928/min with the
                // sky pass skipped (skyDraws counter vs skippedPasses=sky).
                result = TailCallPatch.apply(result, Mappings.RG_RENDER_SKY, Mappings.RG_RENDER_SKY_DESC,
                    "vertex/hooks/VertexSkyBridge", "afterSky");
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
                // Freelook: divert the two mouse-look setAngles call sites so a held key
                // freezes the player's heading while the orbit absorbs the deltas, and
                // bracket renderWorld so the view entity's rotation quartet is spoofed
                // for exactly the camera/world-render window (aim picking in getMouseOver
                // runs outside it and always reads the true heading).
                result = RerouteVirtualInMethodPatch.apply(result,
                    Mappings.ER_UPDATE_CAMERA_AND_RENDER, Mappings.ER_UPDATE_CAMERA_AND_RENDER_DESC,
                    Mappings.ENTITY_CLIENT_PLAYER, Mappings.ENTITY_SET_ANGLES, Mappings.ENTITY_SET_ANGLES_DESC,
                    "vertex/hooks/VertexFreelook", "setAngles");
                result = HeadInstanceCallPatch.apply(result, Mappings.ER_RENDER_WORLD, Mappings.ER_RENDER_WORLD_DESC,
                    "vertex/hooks/VertexFreelook", "beginRenderWorld");
                result = TailInstanceCallPatch.apply(result, Mappings.ER_RENDER_WORLD, Mappings.ER_RENDER_WORLD_DESC,
                    "vertex/hooks/VertexFreelook", "endRenderWorld");
            }
            else if (Mappings.TEXTURE_MAP.equals(name))
            {
                LogWrapper.info("[Vertex] Patching TextureMap (" + name + ")");
                result = SkipMethodPatch.apply(result, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.TM_UPDATE_ANIMATIONS, Mappings.TM_UPDATE_ANIMATIONS_DESC, "textureAnimations"),
                });
                result = HeadInstanceCallPatch.apply(result, Mappings.TM_LOAD_ATLAS, Mappings.TM_LOAD_ATLAS_DESC,
                    "vertex/hooks/VertexCtm", "beforeStitch");
            }
            else if (Mappings.MINECRAFT.equals(name))
            {
                LogWrapper.info("[Vertex] Patching Minecraft (" + name + ")");
                result = HeadInstanceCallPatch.apply(result, Mappings.MC_LOAD_WORLD, Mappings.MC_LOAD_WORLD_DESC,
                    "vertex/hooks/VertexTessellator", "sanitizeOnWorldChange");
                result = TailInstanceCallPatch.apply(result, Mappings.MC_LOAD_WORLD, Mappings.MC_LOAD_WORLD_DESC,
                    "vertex/hooks/VertexTessellator", "sanitizeOnWorldChange");
                result = HeadInstanceCallPatch.apply(result, Mappings.MC_RUN_GAME_LOOP, Mappings.MC_RUN_GAME_LOOP_DESC, "vertex/hooks/VertexTestHarness", "tick");
            }
            else if (Mappings.RENDER_BLOCKS.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderBlocks (" + name + ")");
                result = IconHookPatch.apply(result);
            }
            else if (Mappings.RENDER_CLASS.equals(name))
            {
                LogWrapper.info("[Vertex] Patching Render (" + name + ")");
                result = HeadGuardPatch.apply(result, Mappings.RENDER_BIND_ENTITY_TEXTURE, Mappings.RENDER_BIND_ENTITY_TEXTURE_DESC,
                    "vertex/hooks/VertexRandomEntities", "interceptBind", HeadGuardPatch.THIS_AND_OBJECT);
            }
            else if (Mappings.RENDER_MANAGER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderManager (" + name + ")");
                result = EntityBrightnessPatch.apply(result);
            }
            else if (Mappings.COLORIZER_GRASS.equals(name))
            {
                LogWrapper.info("[Vertex] Patching ColorizerGrass (" + name + ")");
                result = HeadOverridePatch.apply(result, Mappings.COLORIZER_GET, "vertex/hooks/VertexColorizer", "hasGrass", "grass");
            }
            else if (Mappings.COLORIZER_FOLIAGE.equals(name))
            {
                LogWrapper.info("[Vertex] Patching ColorizerFoliage (" + name + ")");
                result = HeadOverridePatch.apply(result, Mappings.COLORIZER_GET, "vertex/hooks/VertexColorizer", "hasFoliage", "foliage");
            }
            else if (Mappings.BLOCK.equals(name))
            {
                LogWrapper.info("[Vertex] Patching Block (" + name + ")");
                result = ReturnValuePatch.apply(result, Mappings.BLOCK_MIXED_BRIGHTNESS, Mappings.BLOCK_MIXED_BRIGHTNESS_DESC,
                    "vertex/hooks/VertexDynamicLights", "adjust");
            }
            else if (Mappings.GUI_NEW_CHAT.equals(name))
            {
                LogWrapper.info("[Vertex] Patching GuiNewChat (" + name + ")");
                result = RerouteStaticInMethodPatch.apply(result, Mappings.CHAT_DRAW, Mappings.CHAT_DRAW_DESC,
                    Mappings.GUI, Mappings.GUI_DRAW_RECT, Mappings.GUI_DRAW_RECT_DESC,
                    "vertex/hooks/VertexHud", "chatRect");
            }
            else if (Mappings.GUI_INGAME.equals(name))
            {
                LogWrapper.info("[Vertex] Patching GuiIngame (" + name + ")");
                result = RerouteStaticInMethodPatch.apply(result, Mappings.GI_RENDER_SCOREBOARD, Mappings.GI_RENDER_SCOREBOARD_DESC,
                    Mappings.GUI, Mappings.GUI_DRAW_RECT, Mappings.GUI_DRAW_RECT_DESC,
                    "vertex/hooks/VertexHud", "scoreboardRect");
            }
            else if (Mappings.GUI_VIDEO_SETTINGS.equals(name))
            {
                LogWrapper.info("[Vertex] Patching GuiVideoSettings (" + name + ")");
                // The six-page OptiFine-layout menu: the init tail rebuilds the screen's
                // content per page, the action guard dispatches every click except Done
                // (id 200 falls through to vanilla's save-and-return-to-parent).
                result = VideoSettingsKeyPatch.apply(result);
                result = TailInstanceCallPatch.apply(result, Mappings.SCREEN_INIT_GUI, Mappings.SCREEN_INIT_GUI_DESC,
                    "vertex/hooks/VertexVideoMenu", "initScreen");
                result = HeadGuardPatch.apply(result, Mappings.SCREEN_ACTION_PERFORMED, Mappings.SCREEN_ACTION_PERFORMED_DESC,
                    "vertex/hooks/VertexVideoMenu", "actionPerformed", HeadGuardPatch.THIS_AND_OBJECT);
            }
            else if (Mappings.SCREEN_CHAT_OPTIONS.equals(name))
            {
                LogWrapper.info("[Vertex] Patching ScreenChatOptions (" + name + ")");
                result = TailInstanceCallPatch.apply(result, Mappings.SCREEN_INIT_GUI, Mappings.SCREEN_INIT_GUI_DESC,
                    "vertex/hooks/VertexHud", "chatOptionsInit");
                result = HeadGuardPatch.apply(result, Mappings.SCREEN_ACTION_PERFORMED, Mappings.SCREEN_ACTION_PERFORMED_DESC,
                    "vertex/hooks/VertexHud", "chatOptionsAction", HeadGuardPatch.THIS_AND_OBJECT);
            }
            else if (Mappings.WORLD_CLIENT.equals(name))
            {
                LogWrapper.info("[Vertex] Patching WorldClient (" + name + ")");
                result = SkipMethodPatch.apply(result, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.WC_DO_VOID_FOG_PARTICLES, Mappings.WC_DO_VOID_FOG_PARTICLES_DESC, "voidParticles"),
                });
            }
            else if (Mappings.WORLD_PROVIDER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching WorldProvider (" + name + ")");
                // Depth fog: the fog-color pass darkens by ((eyeY)*factor)^2 below 1;
                // the adjuster returns a factor large enough that this never engages.
                result = ReturnAdjustPatch.apply(result, Mappings.WP_VOID_FOG_FACTOR, Mappings.WP_VOID_FOG_FACTOR_DESC,
                    "vertex/hooks/VertexSkyDetails", "voidFogFactor");
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
