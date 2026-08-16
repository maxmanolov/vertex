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
                // Ambient-particle gates: a suppressed spawn returns null, exactly like
                // vanilla's own distance culling in the same method.
                result = HeadGuardPatch.apply(result, Mappings.RG_DO_SPAWN_PARTICLE, Mappings.RG_DO_SPAWN_PARTICLE_DESC,
                    "vertex/hooks/VertexAnimations", "interceptParticle", HeadGuardPatch.THIS_AND_OBJECT);

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
                // Trees fast/fancy: loadRenderers pushes fancyGraphics into both leaf
                // blocks; the reroute applies the tri-state override and captures the
                // blocks so a menu flip can re-push immediately.
                result = RerouteVirtualInMethodPatch.apply(result,
                    Mappings.RG_LOAD_RENDERERS, Mappings.RG_LOAD_RENDERERS_DESC,
                    Mappings.LEAVES_CLASS, Mappings.LEAVES_SET_GRAPHICS, Mappings.LEAVES_SET_GRAPHICS_DESC,
                    "vertex/hooks/VertexGraphics", "setLeavesGraphics");
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
                    // Rain splash particles: gated by weather above AND their own key -
                    // stacked guards OR together, so either off suppresses the splashes.
                    new SkipMethodPatch.Target(Mappings.ER_ADD_RAIN_PARTICLES, Mappings.ER_ADD_RAIN_PARTICLES_DESC, "particleRainSplash"),
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
                // Per-sprite gates first, then the atlas recorder, then the global
                // master skip - so a fully frozen game never enters the loop at all.
                result = RerouteVirtualInMethodPatch.apply(result,
                    Mappings.TM_UPDATE_ANIMATIONS, Mappings.TM_UPDATE_ANIMATIONS_DESC,
                    Mappings.SPRITE_CLASS, Mappings.SPRITE_UPDATE, Mappings.SPRITE_UPDATE_DESC,
                    "vertex/hooks/VertexAnimations", "updateSprite");
                result = HeadInstanceCallPatch.apply(result, Mappings.TM_UPDATE_ANIMATIONS, Mappings.TM_UPDATE_ANIMATIONS_DESC,
                    "vertex/hooks/VertexAnimations", "beginAtlasUpdate");
                result = SkipMethodPatch.apply(result, new SkipMethodPatch.Target[] {
                    new SkipMethodPatch.Target(Mappings.TM_UPDATE_ANIMATIONS, Mappings.TM_UPDATE_ANIMATIONS_DESC, "textureAnimations"),
                });
                result = HeadInstanceCallPatch.apply(result, Mappings.TM_LOAD_ATLAS, Mappings.TM_LOAD_ATLAS_DESC,
                    "vertex/hooks/VertexCtm", "beforeStitch");
                // Mipmap type: cache the terrain atlas id and apply the filter choice.
                result = TailInstanceCallPatch.apply(result, Mappings.TM_LOAD_ATLAS, Mappings.TM_LOAD_ATLAS_DESC,
                    "vertex/hooks/VertexQuality", "afterAtlasLoad");
            }
            else if (Mappings.MINECRAFT.equals(name))
            {
                LogWrapper.info("[Vertex] Patching Minecraft (" + name + ")");
                result = HeadInstanceCallPatch.apply(result, Mappings.MC_LOAD_WORLD, Mappings.MC_LOAD_WORLD_DESC,
                    "vertex/hooks/VertexTessellator", "sanitizeOnWorldChange");
                result = TailInstanceCallPatch.apply(result, Mappings.MC_LOAD_WORLD, Mappings.MC_LOAD_WORLD_DESC,
                    "vertex/hooks/VertexTessellator", "sanitizeOnWorldChange");
                result = HeadInstanceCallPatch.apply(result, Mappings.MC_RUN_GAME_LOOP, Mappings.MC_RUN_GAME_LOOP_DESC, "vertex/hooks/VertexTestHarness", "tick");
                // Smooth FPS: drain the driver queue at the frame tail when enabled.
                result = TailInstanceCallPatch.apply(result, Mappings.MC_RUN_GAME_LOOP, Mappings.MC_RUN_GAME_LOOP_DESC,
                    "vertex/hooks/VertexPerformance", "afterFrame");
                // Grass fast/fancy: the per-frame fancy-grass derivation reroutes so
                // the tri-state override lands in RenderBlocks' static gate instead.
                result = StaticFieldWriteReroutePatch.apply(result,
                    Mappings.MC_PRE_RENDER, Mappings.MC_PRE_RENDER_DESC,
                    Mappings.RENDER_BLOCKS, Mappings.RB_FANCY_GRASS, "Z",
                    "vertex/hooks/VertexGraphics", "applyFancyGrass");
                // Antialiasing: the display-create reroute asks for a sampled context
                // first and falls back through vanilla's own ladder.
                result = RerouteStaticInMethodPatch.apply(result,
                    Mappings.MC_INIT_DISPLAY, Mappings.MC_INIT_DISPLAY_DESC,
                    "org/lwjgl/opengl/Display", "create", "(Lorg/lwjgl/opengl/PixelFormat;)V",
                    "vertex/hooks/VertexAntialias", "createDisplay");
                // Debug profiler: every showDebugProfilerChart read gates on the key,
                // so vanilla's own conjunction stops profiler collection when off.
                result = FieldReadReroutePatch.apply(result,
                    Mappings.GAME_SETTINGS, Mappings.GS_SHOW_DEBUG_CHART, "Z",
                    "vertex/hooks/VertexHud", "debugChartEnabled");
            }
            else if (Mappings.RENDER_BLOCKS.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderBlocks (" + name + ")");
                result = IconHookPatch.apply(result);
                // Better snow: the per-block dispatch gets a prepend hook that draws a
                // one-layer snow box under qualifying blocks; it never skips the body.
                result = HeadGuardPatch.apply(result, Mappings.RB_DISPATCH, Mappings.RB_DISPATCH_DESC,
                    "vertex/hooks/VertexBetterSnow", "prepend", HeadGuardPatch.THIS_OBJECT_III);
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
                // Smooth lighting level: scale the ambient-occlusion corner darkening.
                result = ReturnAdjustPatch.apply(result, Mappings.BLOCK_AO_VALUE, Mappings.BLOCK_AO_VALUE_DESC,
                    "vertex/hooks/VertexGraphics", "aoLightValue");
            }
            else if (Mappings.RENDER_ITEM.equals(name))
            {
                LogWrapper.info("[Vertex] Patching RenderItem (" + name + ")");
                // Dropped items fast/fancy: the flat-vs-3D choice reads fancyGraphics.
                result = FieldReadReroutePatch.apply(result,
                    Mappings.GAME_SETTINGS, Mappings.GS_FANCY_GRAPHICS, "Z",
                    "vertex/hooks/VertexGraphics", "fancyItems");
            }
            else if (Mappings.ENTITY_PLAYER_SP.equals(name))
            {
                LogWrapper.info("[Vertex] Patching EntityPlayerSP (" + name + ")");
                // Dynamic FOV: pin the sprint/potion FOV multiplier to 1 when off.
                result = ReturnAdjustPatch.apply(result, Mappings.PLAYER_FOV_MULTIPLIER, Mappings.PLAYER_FOV_MULTIPLIER_DESC,
                    "vertex/hooks/VertexGraphics", "fovMultiplier");
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
                // Show FPS + lagometer draw at the overlay tail, inside the GUI ortho.
                result = TailInstanceCallPatch.apply(result, Mappings.GI_RENDER_OVERLAY, Mappings.GI_RENDER_OVERLAY_DESC,
                    "vertex/hooks/VertexHud", "afterOverlay");
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
                // Time override: an added client-only getCelestialAngle override; the
                // integrated server's Worlds keep the vanilla method untouched.
                result = AddFloatOverridePatch.apply(result,
                    Mappings.WORLD_CELESTIAL_ANGLE, Mappings.WORLD_CELESTIAL_ANGLE_DESC,
                    "vertex/hooks/VertexWorldVisuals", "celestialAngle");
            }
            else if (Mappings.MC_SERVER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching MinecraftServer (" + name + ")");
                // Autosave interval: the tick's single %900 site becomes configurable.
                result = ReplaceIntConstPatch.apply(result, Mappings.MC_SERVER_TICK, Mappings.MC_SERVER_TICK_DESC,
                    900, 1, "vertex/hooks/VertexWorldVisuals", "autosaveTicks");
            }
            else if (isSmoothBlender(name))
            {
                LogWrapper.info("[Vertex] Patching biome color blender (" + name + ")");
                // Smooth biomes off: the 3x3 blend collapses to the center sample.
                result = CenterSampleOverridePatch.apply(result,
                    Mappings.COLOR_BLEND, Mappings.COLOR_BLEND_DESC,
                    isWaterBlender(name) ? 2 : isFoliageBlender(name) ? 1 : 0,
                    "vertex/hooks/VertexSmoothBiomes", "fastPath", "centerSample");
            }
            else if (Mappings.SWAMP_BIOME.equals(name))
            {
                LogWrapper.info("[Vertex] Patching BiomeGenSwamp (" + name + ")");
                // Swamp colors off: both special-cased colors fall through to the base
                // colormap path (which the custom-colors interception already covers).
                result = SuperFallbackPatch.apply(result, Mappings.BIOME_GRASS_COLOR, Mappings.BIOME_COLOR_DESC,
                    "vertex/hooks/VertexQuality", "swampColors");
                result = SuperFallbackPatch.apply(result, Mappings.BIOME_FOLIAGE_COLOR, Mappings.BIOME_COLOR_DESC,
                    "vertex/hooks/VertexQuality", "swampColors");
            }
            else if (Mappings.MATH_HELPER.equals(name))
            {
                LogWrapper.info("[Vertex] Patching MathHelper (" + name + ")");
                // Fast math: table-backed sin/cos (mode fixed at class load; the
                // vanilla-mode table is bit-exact, so this is always safe to weave).
                result = MethodBodyReplacePatch.apply(result, Mappings.MATH_SIN, Mappings.MATH_TRIG_DESC,
                    "vertex/hooks/VertexFastMath", "sin");
                result = MethodBodyReplacePatch.apply(result, Mappings.MATH_COS, Mappings.MATH_TRIG_DESC,
                    "vertex/hooks/VertexFastMath", "cos");
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

    private static boolean isSmoothBlender(String name)
    {
        return isFoliageBlender(name) || isGrassBlender(name) || isWaterBlender(name);
    }

    private static boolean isWaterBlender(String name)
    {
        for (String owner : Mappings.SMOOTH_WATER_BLENDERS)
        {
            if (owner.equals(name))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isGrassBlender(String name)
    {
        for (String owner : Mappings.SMOOTH_GRASS_BLENDERS)
        {
            if (owner.equals(name))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isFoliageBlender(String name)
    {
        for (String owner : Mappings.SMOOTH_FOLIAGE_BLENDERS)
        {
            if (owner.equals(name))
            {
                return true;
            }
        }

        return false;
    }
}
