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
 */
public final class Mappings
{
    public static final String MINECRAFT = "bao";
    public static final String RENDER_GLOBAL = "bma";
    public static final String WORLD_RENDERER = "blo";
    public static final String ENTITY = "sa";
    public static final String BLOCK = "aji";
    public static final String MC_RESOURCE_MANAGER = "an";
    public static final String BLOCKS_REGISTRY = "ajn";
    public static final String BLOCKS_GRASS = "c";
    public static final String BLOCKS_SNOW_LAYER = "aC";
    public static final String IBA_GET_BLOCK = "a";
    public static final String BLOCK_GET_ICON_META = "a";
    public static final String RENDER_CLASS = "bno";
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
    public static final String MC_RUN_GAME_LOOP = "ak";
    public static final String MC_RUN_GAME_LOOP_DESC = "()V";
    public static final String MC_LAUNCH_INTEGRATED_SERVER = "a";
    public static final String MINECRAFT_GET_MINECRAFT = "B";
    public static final String MINECRAFT_GET_MINECRAFT_DESC = "()Lbao;";
    public static final String MINECRAFT_RENDER_VIEW_ENTITY = "i";

    public static final String RG_MARK_BLOCK_FOR_UPDATE = "a";
    public static final String RG_MARK_BLOCK_FOR_UPDATE_DESC = "(III)V";
    public static final String RG_MARK_BLOCKS_FOR_UPDATE = "b";
    public static final String RG_MARK_BLOCKS_FOR_UPDATE_DESC = "(IIIIII)V";
    public static final String RG_UPDATE_RENDERERS = "a";
    public static final String RG_UPDATE_RENDERERS_DESC = "(Lsv;Z)Z";
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

    public static final String ENTITY_POS_X = "s";
    public static final String ENTITY_POS_Y = "t";
    public static final String ENTITY_POS_Z = "u";

    /** Field added to WorldRenderer by the transformer. */
    public static final String ADDED_IMMEDIATE_FIELD = "vertex$immediate";

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
    public static final String TM_UPDATE_ANIMATIONS = "d";
    public static final String TM_UPDATE_ANIMATIONS_DESC = "()V";
    public static final String WC_DO_VOID_FOG_PARTICLES = "C";
    public static final String WC_DO_VOID_FOG_PARTICLES_DESC = "(III)V";

    private Mappings()
    {
    }
}
