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
    public static final String ENTITY_LIVING_BASE = "sv";

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
