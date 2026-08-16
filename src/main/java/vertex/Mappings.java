package vertex;

/**
 * Obfuscated (notch) names for the vanilla 1.7.10 client members Vertex patches or reads,
 * resolved from the MCP 1.7.10 joined.srg plus the stable-12 name CSVs.
 *
 * readable name                          srg              notch
 * -------------------------------------- ---------------- -----------------
 * net.minecraft.client.Minecraft         -                bao
 * net.minecraft.client.renderer.RenderGlobal -            bma
 * net.minecraft.client.renderer.WorldRenderer -           blo
 * net.minecraft.client.renderer.entity.RenderManager -    bnn
 * net.minecraft.entity.Entity            -                sa
 * net.minecraft.entity.EntityLivingBase  -                sv
 * Minecraft.theWorld                     field_71441_e    f
 * Minecraft.thePlayer                    field_71439_g    h
 * Minecraft.renderGlobal                 field_71438_f    g
 * Minecraft.runGameLoop                  func_71411_J     ak ()V
 * Minecraft.launchIntegratedServer       func_71371_a     a (String,String,Lahj;)V
 * Minecraft.getMinecraft()               func_71410_x     B ()Lbao;
 * Minecraft.renderViewEntity             field_71451_h    i
 * RenderGlobal.markBlockForUpdate        func_147586_a    a (III)V
 * RenderGlobal.markBlocksForUpdate       func_72725_b     b (IIIIII)V
 * RenderGlobal.updateRenderers           func_72716_a     a (Lsv;Z)Z
 * RenderGlobal.mc                        field_72777_q    A
 * RenderGlobal.worldRenderers            field_72765_l    v
 * RenderGlobal.worldRenderersToUpdate    field_72767_j    t
 * RenderGlobal.renderChunksWide          field_72766_m    w
 * RenderGlobal.renderChunksTall          field_72763_n    x
 * RenderGlobal.renderChunksDeep          field_72764_o    y
 * WorldRenderer.needsUpdate              field_78939_q    q
 * WorldRenderer.isInFrustum              field_78927_l    l
 * WorldRenderer.posX/posY/posZ           field_78923_c..  c / d / e
 * WorldRenderer.setPosition              func_78913_a     a (III)V
 * WorldRenderer.updateRenderer           func_147892_a    a (Lsv;)V
 * WorldRenderer.distanceToEntitySquared  func_78912_a     a (Lsa;)F
 * Entity.posX/posY/posZ                  field_70165_t..  s / t / u
 * Block.blockRegistry                    field_149771_c   c
 * Block.getIdFromBlock                   func_149682_b    b (Laji;)I
 * RegistryNamespaced.getNameForObject    func_148750_c    c (Object)String
 */
public final class Mappings
{
    public static final String MINECRAFT = "bao";
    public static final String RENDER_GLOBAL = "bma";
    public static final String WORLD_RENDERER = "blo";
    public static final String RENDER_MANAGER = "bnn";
    public static final String ENTITY = "sa";
    public static final String BLOCK = "aji";
    public static final String BLOCK_REGISTRY = "c";
    public static final String BLOCK_GET_ID = "b";
    public static final String REGISTRY_NAME_FOR_OBJECT = "c";
    public static final String MC_RESOURCE_MANAGER = "an";
    public static final String BLOCKS_REGISTRY = "ajn";
    public static final String BLOCKS_GRASS = "c";
    public static final String BLOCKS_SNOW_LAYER = "aC";
    public static final String IBA_GET_BLOCK = "a";
    public static final String BLOCK_GET_ICON_META = "a";
    public static final String MC_GET_TEXTURE_MANAGER = "P";
    public static final String TEXTURE_BIND = "a";
    public static final String WORLD_GET_TIME = "J";
    public static final String WORLD_CELESTIAL_ANGLE = "c";
    public static final String RENDER_CLASS = "bno";
    /* RenderManager.renderEntityStatic=func_147939_a=a(Lsa;FZ)Z obtains the packed
     * entity lightmap value through Entity.getBrightnessForRender=func_70070_b=c(F)I
     * immediately before passing its two halves to OpenGlHelper. */
    public static final String RM_RENDER_ENTITY_STATIC = "a";
    public static final String RM_RENDER_ENTITY_STATIC_DESC = "(Lsa;FZ)Z";
    public static final String ENTITY_GET_BRIGHTNESS_FOR_RENDER = "c";
    public static final String ENTITY_GET_BRIGHTNESS_FOR_RENDER_DESC = "(F)I";
    public static final String RENDER_BIND_ENTITY_TEXTURE = "b";
    public static final String RENDER_BIND_ENTITY_TEXTURE_DESC = "(Lsa;)V";
    public static final String RENDER_GET_ENTITY_TEXTURE = "a";
    public static final String RENDER_BIND_TEXTURE = "a";
    public static final String LOCATION_PATH = "a";
    public static final String LOCATION_DOMAIN = "b";
    public static final String ENTITY_GET_ID = "y";
    public static final String COLORIZER_GRASS = "aha";
    public static final String COLORIZER_FOLIAGE = "agx";
    public static final String COLORIZER_GET = "a";
    public static final String RELOAD_LISTENER_IFACE = "bqz";
    public static final String REGISTER_RELOAD_LISTENER = "a";
    public static final String RESOURCE_LOCATION = "bqx";
    public static final String GET_RESOURCE = "a";
    public static final String RESOURCE_GET_STREAM = "b";
    public static final String WORLD_PLAYER_ENTITIES = "h";
    public static final String ELB_GET_HELD_ITEM = "be";
    public static final String STACK_GET_ITEM = "b";
    public static final String BLOCK_FROM_ITEM = "a";
    public static final String BLOCK_GET_LIGHT_VALUE = "m";
    public static final String RENDER_BLOCKS = "blm";
    public static final String IICON = "rf";
    public static final String ICON_MIN_U = "c";
    public static final String ICON_MAX_U = "d";
    public static final String ICON_MIN_V = "e";
    public static final String ICON_MAX_V = "f";
    public static final String ICON_INTERP_U = "a";
    public static final String ICON_INTERP_V = "b";
    public static final String ICON_NAME = "g";
    public static final String RB_GET_BLOCK_ICON = "a";
    public static final String RB_GET_BLOCK_ICON_DESC = "(Laji;Lahl;IIII)Lrf;";
    public static final String BLOCK_MIXED_BRIGHTNESS = "c";
    public static final String BLOCK_MIXED_BRIGHTNESS_DESC = "(Lahl;III)I";
    public static final String ENTITY_LIVING_BASE = "sv";

    public static final String MC_THE_WORLD = "f";
    public static final String MC_THE_PLAYER = "h";
    public static final String MC_RENDER_GLOBAL = "g";
    public static final String GAME_SETTINGS = "bbj";
    public static final String MC_RUN_GAME_LOOP = "ak";
    public static final String MC_RUN_GAME_LOOP_DESC = "()V";
    public static final String MC_LOAD_WORLD = "a";
    public static final String MC_LOAD_WORLD_DESC = "(Lbjf;)V";
    public static final String MC_LAUNCH_INTEGRATED_SERVER = "a";
    public static final String MINECRAFT_GET_MINECRAFT = "B";
    public static final String MINECRAFT_GET_MINECRAFT_DESC = "()Lbao;";
    public static final String MINECRAFT_RENDER_VIEW_ENTITY = "i";

    public static final String RG_MARK_BLOCK_FOR_UPDATE = "a";
    public static final String RG_MARK_BLOCK_FOR_UPDATE_DESC = "(III)V";
    public static final String RG_MARK_BLOCKS_FOR_UPDATE = "b";
    public static final String RG_MARK_BLOCKS_FOR_UPDATE_DESC = "(IIIIII)V";
    /* RenderGlobal.markBlockRangeForRenderUpdate=func_147585_a=a(IIIIII)V - the public
     * range entry (IWorldAccess), distinct from the private markBlocksForUpdate funnel
     * b(IIIIII)V that all mark entries share. */
    public static final String RG_MARK_BLOCK_RANGE_FOR_RENDER_UPDATE = "a";
    public static final String RG_MARK_BLOCK_RANGE_FOR_RENDER_UPDATE_DESC = "(IIIIII)V";

    /* EntityPlayerMP.playerNetServerHandler=field_71135_a=a; its
     * NetHandlerPlayServer.setPlayerLocation=func_147364_a=a(DDDFF)V is the vanilla
     * server-authoritative teleport, the one path a position pin can use that the
     * client will not fight. */
    public static final String PLAYER_MP_NET_HANDLER = "a";
    public static final String NET_HANDLER_SET_PLAYER_LOCATION = "a";
    public static final String RG_UPDATE_RENDERERS = "a";
    public static final String RG_UPDATE_RENDERERS_DESC = "(Lsv;Z)Z";
    public static final String RG_LOAD_RENDERERS = "a";
    public static final String RG_LOAD_RENDERERS_DESC = "()V";
    /* RenderGlobal.markBlockForRenderUpdate=func_147588_b=b(III)V - the light-only
     * re-mark path (IWorldAccess.notifyLightSet), distinct from markBlockForUpdate
     * (block changes) and markBlockRangeForRenderUpdate. Skippable under fullbright. */
    public static final String RG_MARK_BLOCK_FOR_RENDER_UPDATE = "b";
    public static final String RG_MARK_BLOCK_FOR_RENDER_UPDATE_DESC = "(III)V";
    /* Render-phase profiling surface (all RenderGlobal=bma, resolved from joined.srg):
     * sortAndRender=func_72719_a=a(Lsv;ID)I walks the pass's renderers and groups the
     * visible ones into the four RenderLists; renderAllRenderLists=func_72733_a=a(ID)V
     * (called from its tail via renderSortedRenderers=func_72724_a) issues the actual
     * glCallLists batches; clipRenderersByFrustum=func_72729_a=a(Lbmv;F)V is the
     * per-frame visibility pass. Debug counters, reset each pass-0 walk:
     * renderersLoaded=field_72751_K=Y, renderersBeingClipped=field_72744_L=Z,
     * renderersBeingOccluded=field_72745_M=aa, renderersBeingRendered=field_72746_N=ab,
     * renderersSkippingRenderPass=field_72747_O=ac; glRenderLists=field_72755_R=af. */
    public static final String RG_SORT_AND_RENDER = "a";
    public static final String RG_SORT_AND_RENDER_DESC = "(Lsv;ID)I";
    public static final String RG_RENDER_ALL_LISTS = "a";
    public static final String RG_RENDER_ALL_LISTS_DESC = "(ID)V";
    public static final String RG_CLIP_FRUSTUM = "a";
    public static final String RG_CLIP_FRUSTUM_DESC = "(Lbmv;F)V";
    public static final String RG_DBG_LOADED = "Y";
    public static final String RG_DBG_CLIPPED = "Z";
    public static final String RG_DBG_OCCLUDED = "aa";
    public static final String RG_DBG_RENDERED = "ab";
    public static final String RG_DBG_SKIPPED_PASS = "ac";
    public static final String RG_GL_RENDER_LISTS = "af";
    public static final String RG_MC = "A";
    public static final String RG_WORLD_RENDERERS = "v";
    public static final String RG_WORLD_RENDERERS_TO_UPDATE = "t";
    public static final String RG_RENDER_CHUNKS_WIDE = "w";
    public static final String RG_RENDER_CHUNKS_TALL = "x";
    public static final String RG_RENDER_CHUNKS_DEEP = "y";

    public static final String WR_NEEDS_UPDATE = "q";
    public static final String WR_POS_X = "c";
    public static final String WR_POS_Y = "d";
    public static final String WR_POS_Z = "e";
    public static final String WR_SET_POSITION = "a";
    public static final String WR_SET_POSITION_DESC = "(III)V";
    public static final String WR_UPDATE_RENDERER = "a";
    public static final String WR_UPDATE_RENDERER_DESC = "(Lsv;)V";
    /* WorldRenderer.updateRendererSort=func_147889_b=b(Lsv;)V - client-side translucent
     * resort; reads vertexState twice (null guard then use), so it must not run while a
     * worker build owns the renderer (#92). */
    public static final String WR_UPDATE_RENDERER_SORT = "b";
    public static final String WR_UPDATE_RENDERER_SORT_DESC = "(Lsv;)V";

    public static final String ENTITY_POS_X = "s";
    public static final String ENTITY_POS_Y = "t";
    public static final String ENTITY_POS_Z = "u";
    /* Entity.prevPosX/Y/Z = field_70142_S / field_70137_T / field_70136_U - the
     * interpolation anchors renderSortedRenderers uses for the camera position. */
    public static final String ENTITY_PREV_POS_X = "S";
    public static final String ENTITY_PREV_POS_Y = "T";
    public static final String ENTITY_PREV_POS_Z = "U";

    /** Field added to WorldRenderer by the transformer. */
    public static final String ADDED_IMMEDIATE_FIELD = "vertex$immediate";

    /** Backend slot field added to WorldRenderer by the transformer (see MeshHost). */
    public static final String ADDED_MESH_FIELD = "vertex$mesh";

    /*
     * Render-control targets (all void, patched with config-gated head skips):
     * RenderGlobal.renderSky          func_72714_a     a (F)V
     * RenderGlobal.renderClouds       func_72718_b     b (F)V
     * Tessellator                     -                bmh
 * Tessellator.instance            field_78398_a    a (public static final)
 * EntityRenderer                  -                blt
     * EntityRenderer.setupFog         func_78468_a     a (IF)V
 * EntityRenderer.renderRainSnow   func_78474_d     e (F)V
     * EntityRenderer.addRainParticles func_78484_h     l ()V
     * TextureMap                      -                bpz
     * TextureMap.updateAnimations     func_94248_c     d ()V
     * WorldClient                     -                bjf
     * WorldClient.doVoidFogParticles  func_73029_E     C (III)V
     */
    public static final String TESSELLATOR = "bmh";
    public static final String TESSELLATOR_INSTANCE = "a";

    /** WorldRenderer caches Tessellator.instance in its own private static; reads must be redirected too. */
    public static final String WR_CACHED_TESSELLATOR = "A";

    /*
     * Multi-core capture/replay surface:
     * WorldRenderer.glRenderList         field_78933_?    z
     * WorldRenderer.bytesDrawn           field_78931_?    D
     * WorldRenderer.vertexState          -                y
     * WorldRenderer.tileEntities         -                C
     * WorldRenderer.tileEntityRenderers  -                x
     * WorldRenderer.preRenderBlocks      func_78908_a     b (I)V
     * WorldRenderer.postRenderBlocks     func_78904_a?    a (ILsv;)V
     * Tessellator.startDrawingQuads      func_78382_b     b ()V
     * Tessellator.setTranslation         func_78373_b     b (DDD)V
     * Tessellator.draw                   func_78381_a     a ()I
     * Tessellator.getVertexState         -                a (FFF)Lbmi;
     */
    public static final String WR_GL_RENDER_LIST = "z";
    public static final String WR_BYTES_DRAWN = "D";
    public static final String WR_VERTEX_STATE = "y";
    /** WorldRenderer.skipRenderPass = field_78928_m: true per pass = nothing to draw. */
    public static final String WR_SKIP_RENDER_PASS = "m";
    public static final String WR_TILE_ENTITIES = "C";
    public static final String WR_TILE_ENTITY_RENDERERS = "x";
    public static final String WR_PRE_RENDER_BLOCKS = "b";
    public static final String WR_PRE_RENDER_BLOCKS_DESC = "(I)V";
    public static final String WR_POST_RENDER_BLOCKS = "a";
    public static final String WR_POST_RENDER_BLOCKS_DESC = "(ILsv;)V";
    public static final String WR_SETUP_GL_TRANSLATION = "f";
    public static final String TESS_START_QUADS = "b";
    public static final String TESS_SET_TRANSLATION = "b";
    public static final String TESS_DRAW = "a";
    public static final String TESS_GET_VERTEX_STATE = "a";
    /* Tessellator.reset=func_78379_d=d()V (private), isDrawing=field_78415_z=x */
    public static final String TESS_RESET = "d";
    public static final String TESS_IS_DRAWING = "x";
    /* Tessellator geometry fields for MeshData extraction, srg-verified and cross-checked
     * against the pointer setup in draw() (bmh.a()I) bytecode:
     * rawBuffer=field_78405_h=f, vertexCount=field_78406_i=g,
     * rawBufferIndex=field_147569_p=p, drawMode=field_78409_u=s,
     * hasColor=field_78399_n=l, hasTexture=field_78400_o=m,
     * hasBrightness=field_78414_p=n, hasNormals=field_78413_q=o. */
    public static final String TESS_RAW_BUFFER = "f";
    public static final String TESS_VERTEX_COUNT = "g";
    public static final String TESS_RAW_BUFFER_INDEX = "p";
    public static final String TESS_DRAW_MODE = "s";
    public static final String TESS_HAS_COLOR = "l";
    public static final String TESS_HAS_TEXTURE = "m";
    public static final String TESS_HAS_BRIGHTNESS = "n";
    public static final String TESS_HAS_NORMALS = "o";
    /* Minecraft.entityRenderer=field_71460_t=p; EntityRenderer.enableLightmap=
     * func_78463_b=b(D)V and disableLightmap=func_78483_a=a(D)V bracket vanilla's
     * renderAllRenderLists submission and must bracket a managed backend's too. */
    public static final String MC_ENTITY_RENDERER = "p";
    public static final String ER_ENABLE_LIGHTMAP = "b";
    public static final String ER_DISABLE_LIGHTMAP = "a";
    public static final String ENTITY_RENDERER = "blt";
    public static final String TEXTURE_MAP = "bpz";
    public static final String WORLD_CLIENT = "bjf";

    public static final String RG_RENDER_SKY = "a";
    public static final String RG_RENDER_SKY_DESC = "(F)V";
    public static final String RG_RENDER_CLOUDS = "b";
    public static final String RG_RENDER_CLOUDS_DESC = "(F)V";
    public static final String ER_SETUP_FOG = "a";
    public static final String ER_SETUP_FOG_DESC = "(IF)V";
    public static final String ER_RENDER_RAIN_SNOW = "e";
    public static final String ER_RENDER_RAIN_SNOW_DESC = "(F)V";
    public static final String ER_ADD_RAIN_PARTICLES = "l";
    public static final String ER_ADD_RAIN_PARTICLES_DESC = "()V";

    // --- sky detail surface (javap bma renderSky a(F)V, blt fog passes, aqo) ---------
    // renderSky binds bqf.a(Lbqx;)V three times: the End sky, the sun (getstatic o,
    // offset 869) and the moon (getstatic n, 962); the sun/moon quads flush through
    // bmh.a()I at offsets 950/1120 while the sunset glow and horizon caps draw with
    // texturing disabled. Stars are a display list: glColor4f(starBrightness x3)
    // directly precedes getfield F + GL11.glCallList at 1162-1165; G is the sky dome
    // list (417) and H the below-horizon cap (1250). Clouds render in bma.b(F)V.
    // blt.a(IF)V sets the linear fog band as start=0.25*far, end=far (sky pass -1:
    // start=0, end=0.8*far). The depth darkening multiplies the fog color by
    // ((eyeY)*aqo.k())^2 whenever that product is below 1; aqo.k()D is
    // WorldProvider.getVoidFogYFactor returning 0.03125, invoked from blt's fog-color
    // pass at offset 685.
    public static final String TEXTURE_MANAGER = "bqf";
    public static final String RG_STAR_LIST = "F";
    public static final String TESS_DRAW_DESC = "()I";
    public static final String WORLD_PROVIDER = "aqo";
    public static final String WP_VOID_FOG_FACTOR = "k";
    public static final String WP_VOID_FOG_FACTOR_DESC = "()D";

    // --- animation surface (javap bpz TextureMap, bqd TextureAtlasSprite, bma) -------
    // bpz.d()V walks listAnimatedSprites (field e) invoking bqd.j()V per sprite; the
    // atlas discriminates via bpz.h (0=blocks, 1=items - the items branch special-cases
    // "clock"/"compass" in bpz.a(String)). bqd.g() returns the icon name (field i, set
    // in the ctor). Every string-named ambient particle funnels through
    // bma.b(Ljava/lang/String;DDDDDD)Lbkm; whose callers all tolerate null (vanilla
    // returns null itself on distance culling).
    // --- graphics decoupling surface (javap bma loadRenderers, bny, aji, blk, bao) ----
    // loadRenderers (bma.a()V) pushes gameSettings.fancyGraphics (bbj.i:Z) into both
    // leaf blocks: getstatic ajn.t/ajn.u (Lalt;) then alt.b(Z)V at offsets 21/37.
    // RenderItem (bny extends bno) reads bbj.i directly for the flat-vs-3D dropped item
    // choice. Block.getAmbientOcclusionLightValue is aji.I()F (0.2 for normal cubes).
    // The hand-FOV smoother (blt.j()V) polls blk.t()F - EntityPlayerSP.getFOVMultiplier
    // - into fovModifierHand each tick. bao.x()Z is the static fancy-graphics wrapper.
    public static final String LEAVES_CLASS = "alt";
    public static final String LEAVES_SET_GRAPHICS = "b";
    public static final String LEAVES_SET_GRAPHICS_DESC = "(Z)V";
    public static final String GS_FANCY_GRAPHICS = "i";
    public static final String RENDER_ITEM = "bny";
    public static final String BLOCK_AO_VALUE = "I";
    public static final String BLOCK_AO_VALUE_DESC = "()F";
    public static final String ENTITY_PLAYER_SP = "blk";
    public static final String PLAYER_FOV_MULTIPLIER = "t";
    public static final String PLAYER_FOV_MULTIPLIER_DESC = "()F";

    // --- Other-page surface (javap bbv GuiIngame, bao, qi Profiler, MinecraftServer) --
    // GuiIngame holds Minecraft at bbv.k and its overlay pass is bbv.a(FZII)V; HUD
    // strings draw through the FontRenderer at bao.l (Lbbu;), with-shadow variant
    // bbu.b(Ljava/lang/String;III)I. The profiler gate: runGameLoop sets qi.a
    // (profilingEnabled) from showDebugInfo (bbj.ax) && showDebugProfilerChart
    // (bbj.ay) at offsets 336-398; bao reads ay at three sites, all profiler-related.
    // The integrated server autosaves when tickCounter % 900 == 0 inside
    // MinecraftServer.u()V - the only sipush 900 in the class, which keeps its real
    // name in 1.7.10. WorldClient (bjf extends ahb) does not override
    // getCelestialAngle c(F)F, so a client-visual override can be added safely.
    public static final String GI_RENDER_OVERLAY = "a";
    public static final String GI_RENDER_OVERLAY_DESC = "(FZII)V";
    public static final String GI_MC = "k";
    public static final String MC_FONT_RENDERER = "l";
    public static final String FONT_DRAW_SHADOW = "b";
    public static final String GS_SHOW_DEBUG_CHART = "ay";
    public static final String MC_PROFILER = "z";
    public static final String PROFILER_ENABLED = "a";
    public static final String MC_SERVER = "net.minecraft.server.MinecraftServer";
    public static final String MC_SERVER_TICK = "u";
    public static final String MC_SERVER_TICK_DESC = "()V";
    public static final String WORLD_CLASS = "ahb";
    public static final String WORLD_CELESTIAL_ANGLE_DESC = "(F)F";

    // --- fast math surface (javap qh) -------------------------------------------------
    // qh is MathHelper: a private static float[] a of 65,536 entries backs the final
    // static a(F)F (sin, indexer 10430.378 = 65536/2pi) and b(F)F (cos, quarter-turn
    // offset 16384). Both bodies replace with table-backed delegates.
    public static final String MATH_HELPER = "qh";
    public static final String MATH_SIN = "a";
    public static final String MATH_COS = "b";
    public static final String MATH_TRIG_DESC = "(F)F";

    // --- quality surface (javap aiv BiomeGenSwamp, bpz/bpp textures) ------------------
    // The swamp biome aiv (extends ahu) special-cases its colors: grass b(III)I picks
    // 5011004/6975545 by perlin, foliage c(III)I returns 6975545 flat; the base class
    // versions sample the standard temperature/rainfall colormaps. TextureMap extends
    // bpp whose b()I is getGlTextureId.
    public static final String SWAMP_BIOME = "aiv";
    public static final String BIOME_GRASS_COLOR = "b";
    public static final String BIOME_FOLIAGE_COLOR = "c";
    public static final String BIOME_COLOR_DESC = "(III)I";
    public static final String TEXTURE_GL_ID = "b";

    public static final String SPRITE_CLASS = "bqd";
    public static final String SPRITE_UPDATE = "j";
    public static final String SPRITE_UPDATE_DESC = "()V";
    public static final String SPRITE_NAME = "g";
    public static final String TM_TYPE = "h";
    public static final String RG_DO_SPAWN_PARTICLE = "b";
    public static final String RG_DO_SPAWN_PARTICLE_DESC = "(Ljava/lang/String;DDDDDD)Lbkm;";
    public static final String TEXTUREMAP_REGISTER_ICON = "a";
    public static final String TM_LOAD_ATLAS = "b";
    public static final String TM_LOAD_ATLAS_DESC = "(Lbqy;)V";
    public static final String TM_UPDATE_ANIMATIONS = "d";
    public static final String TM_UPDATE_ANIMATIONS_DESC = "()V";
    public static final String WC_DO_VOID_FOG_PARTICLES = "C";
    public static final String WC_DO_VOID_FOG_PARTICLES_DESC = "(III)V";

    /* Stress harness members (resolved from MCP 1.7.10 srg):
     * Minecraft.gameSettings=u  GameSettings(bbj).renderDistanceChunks=c  hideGUI=av
     * Entity.setPosition=b(DDD)V  rotationYaw=y  rotationPitch=z
     * World.setWorldTime=b(J)V  Minecraft.refreshResources=c()V  shutdown=k()V
     * Minecraft.loadWorld=a(Lbjf;)V
     */
    /* HUD and GUI members (resolved from MCP 1.7.10 srg + stable-12 CSVs):
     * Gui=bbw  Gui.drawRect=a(IIIII)V (static)
     * GuiNewChat=bcc  drawChat=func_146230_a=a(I)V  printChatMessage=func_146227_a=a(Lfj;)V
     * GuiIngame=bbv  renderScoreboard=func_96136_a=a(Lazx;IILbbu;)V  persistantChatGUI=l
     * ScreenChatOptions=bcs  GuiScreen=bdw  initGui=func_73866_w_=b()V
     * GuiScreen.actionPerformed=func_146284_a=a(Lbcb;)V  buttonList=field_146292_n=n
     * GuiScreen.width=l  height=m
     * GuiButton=bcb  id=k  displayString=j  xPosition=h  yPosition=i  width=f  height=g
     * Minecraft.ingameGUI=r  ChatComponentText=fq
     */
    public static final String GUI = "bbw";
    public static final String GUI_DRAW_RECT = "a";
    public static final String GUI_DRAW_RECT_DESC = "(IIIII)V";
    public static final String GUI_NEW_CHAT = "bcc";
    public static final String CHAT_DRAW = "a";
    public static final String CHAT_DRAW_DESC = "(I)V";
    public static final String CHAT_PRINT_MESSAGE = "a";
    public static final String CHAT_PRINT_MESSAGE_DESC = "(Lfj;)V";
    public static final String GUI_INGAME = "bbv";
    public static final String GI_RENDER_SCOREBOARD = "a";
    public static final String GI_RENDER_SCOREBOARD_DESC = "(Lazx;IILbbu;)V";
    public static final String GI_PERSISTANT_CHAT = "l";
    public static final String GUI_SCREEN = "bdw";
    public static final String SCREEN_CHAT_OPTIONS = "bcs";
    public static final String GUI_VIDEO_SETTINGS = "bef";
    public static final String SCREEN_INIT_GUI = "b";
    public static final String SCREEN_INIT_GUI_DESC = "()V";
    public static final String SCREEN_ACTION_PERFORMED = "a";
    public static final String SCREEN_ACTION_PERFORMED_DESC = "(Lbcb;)V";
    public static final String SCREEN_KEY_TYPED = "a";
    public static final String SCREEN_KEY_TYPED_DESC = "(CI)V";
    public static final String SCREEN_BUTTON_LIST = "n";
    public static final String SCREEN_WIDTH = "l";
    public static final String GUI_BUTTON = "bcb";
    public static final String BUTTON_ID = "k";
    public static final String BUTTON_DISPLAY = "j";
    public static final String BUTTON_Y = "i";
    public static final String MC_INGAME_GUI = "r";
    public static final String CHAT_COMPONENT_TEXT = "fq";

    /* Video-settings menu surface (vanilla 1.7.10 bytecode evidence):
     * GuiVideoSettings=bef fields: parentScreen=f, title=a (protected String, drawn by
     * its drawScreen), gameSettings=g, options row list=h (Lbch;). initGui=b()V builds
     * Done as bcb(200, width/2-100, height-27) plus a bck row list; actionPerformed=
     * a(Lbcb;)V handles only id 200 (saveOptions then displayGuiScreen(parent));
     * mouseClicked calls super FIRST (buttonList clicks work without the list) and
     * re-inits the screen when guiScale changed. GuiOptionsRowList=bck, ctor
     * (Lbao;IIIII[Lbbm;)V, columns at width/2-155 and +160; its factory news
     * bcn(IIILbbm;)V for float options (GuiOptionSlider) and bcj otherwise with labels
     * from GameSettings.getKeyBinding=c(Lbbm;)String. Options enum=bbm; constants
     * pinned by <clinit> ldc order: gamma=d renderDistance=f viewBobbing=g anaglyph=h
     * advancedOpengl=i framerateLimit=j graphics=m ao=n guiScale=o renderClouds=p
     * particles=q fullscreen=x showCape=z mipmapLevels=F anisotropicFiltering=G.
     * GameSettings: setOptionValue=a(Lbbm;I)V, setOptionFloatValue=a(Lbbm;F)V,
     * getOptionOrdinalValue=b(Lbbm;)Z, saveOptions=b()V; heldItemTooltips=B (a plain
     * boolean, parsed from "heldItemTooltips" in loadOptions, no Options entry).
     * Minecraft.displayGuiScreen=a(Lbdw;)V; GuiScreen.mc=k height=m;
     * GuiButton.enabled=l. GuiVideoSettings does not override GuiScreen.keyTyped=
     * a(CI)V; the inherited Esc path displays null, so Vertex adds a targeted override. */
    public static final String VS_PARENT = "f";
    public static final String VS_TITLE = "a";
    public static final String VS_ROW_LIST = "h";
    public static final String OPTIONS_ENUM = "bbm";
    public static final String OPTIONS_ROW_LIST = "bck";
    public static final String GUI_OPTION_SLIDER = "bcn";
    public static final String GS_SET_OPTION = "a";
    public static final String GS_GET_ORDINAL = "b";
    public static final String GS_GET_LABEL = "c";
    public static final String GS_SAVE_OPTIONS = "b";
    public static final String GS_HELD_ITEM_TOOLTIPS = "B";
    public static final String MC_DISPLAY_GUI_SCREEN = "a";
    public static final String SCREEN_MC = "k";
    public static final String SCREEN_HEIGHT = "m";
    public static final String BUTTON_ENABLED = "l";
    public static final String OPT_GAMMA = "d";
    public static final String OPT_RENDER_DISTANCE = "f";
    public static final String OPT_VIEW_BOBBING = "g";
    public static final String OPT_ANAGLYPH = "h";
    public static final String OPT_ADVANCED_GL = "i";
    public static final String OPT_FRAMERATE = "j";
    public static final String OPT_GRAPHICS = "m";
    public static final String OPT_AO = "n";
    public static final String OPT_GUI_SCALE = "o";
    public static final String OPT_CLOUDS = "p";
    public static final String OPT_PARTICLES = "q";
    public static final String OPT_FULLSCREEN = "x";
    public static final String OPT_SHOW_CAPE = "z";
    public static final String OPT_MIPMAPS = "F";
    public static final String OPT_ANISO = "G";

    /* ToggleSprint surface (verified against the vanilla 1.7.10 bytecode structure):
     * GameSettings.keyBindSprint=af, pinned in the bbj constructor - new bal("key.sprint",
     * 29, "key.categories.gameplay") -> putfield af (Left Ctrl default; the binding
     * exists in 1.7.10). KeyBinding=bal fields: keyDescription=d defaultCode=e
     * category=f keyCode=g pressed=h pressTime=i; getIsKeyPressed=d()Z returns h, and
     * the living-update sprint gate reads it, so holding h high per frame makes every
     * vanilla sprint condition authoritative. Minecraft.currentScreen=n. */
    public static final String GS_KEY_BIND_SPRINT = "af";
    public static final String KB_KEY_CODE = "g";
    public static final String KB_PRESSED = "h";
    /* Freelook surface (verified against the vanilla 1.7.10 bytecode structure; the
     * KeyBinding field layout is documented on the ToggleSprint block above):
     * EntityRenderer.updateCameraAndRender=func_78480_b=b(F)V holds the client's only
     * two mouse-look call sites (plain and cinematic branch on GameSettings.smoothCamera
     * =aB), both shaped GETFIELD bao.h -> INVOKEVIRTUAL bjk.c(FF)V. That callee is
     * Entity.setAngles=func_70082_c, declared on sa (yaw += dx*0.15, pitch -= dy*0.15
     * clamped +/-90, prev fields shifted by the same wrap); the call sites record the
     * receiver's static type bjk (EntityClientPlayerMP). EntityRenderer.renderWorld=
     * func_78471_a=a(FJ)V; orientCamera=h(F)V reads the view entity (bao.i) and
     * GameSettings.thirdPersonView=aw three times (>0 orbit, ==2 mirror). Entity
     * rotation quartet: rotationYaw=y prevRotationYaw=A rotationPitch=z
     * prevRotationPitch=B (prev names pinned by setAngles' tail adjustment).
     * KeyBinding=bal: the (String,int,String) ctor self-registers in the static binding
     * list, the key-code hash and the category set; static b()V rebuilds the key hash
     * (resetKeyBindingArrayAndHash). GameSettings.keyBindings=as is the full [Lbal;
     * array that loadOptions/saveOptions/Controls iterate (ar is the 9-slot hotbar
     * sub-array). */
    public static final String ENTITY_CLIENT_PLAYER = "bjk";
    public static final String ENTITY_SET_ANGLES = "c";
    public static final String ENTITY_SET_ANGLES_DESC = "(FF)V";
    public static final String ENTITY_PREV_ROTATION_YAW = "A";
    public static final String ENTITY_PREV_ROTATION_PITCH = "B";
    public static final String ER_UPDATE_CAMERA_AND_RENDER = "b";
    public static final String ER_UPDATE_CAMERA_AND_RENDER_DESC = "(F)V";
    public static final String ER_RENDER_WORLD = "a";
    public static final String ER_RENDER_WORLD_DESC = "(FJ)V";
    public static final String GS_THIRD_PERSON_VIEW = "aw";
    public static final String GS_KEY_BINDINGS = "as";
    public static final String KB_RESET_HASH = "b";
    public static final String MC_CURRENT_SCREEN = "n";

    public static final String MC_GAME_SETTINGS = "u";
    public static final String GS_RENDER_DISTANCE = "c";
    public static final String GS_HIDE_GUI = "av";
    public static final String ENTITY_SET_POSITION = "b";
    public static final String ENTITY_SET_POSITION_DESC = "(DDD)V";
    public static final String ENTITY_ROTATION_YAW = "y";
    public static final String ENTITY_ROTATION_PITCH = "z";
    public static final String WORLD_SET_TIME = "b";
    public static final String MC_REFRESH_RESOURCES = "c";
    public static final String MC_SHUTDOWN = "k";

    private Mappings()
    {
    }
}
