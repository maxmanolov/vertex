package vertex.hooks;

/**
 * The shared icon choke point: RenderBlocks' world-aware getBlockIcon (37 internal call
 * sites - the whole in-world face surface) routes its result through here. CTM, natural
 * textures, emissive overlays and better grass all plug into this one dispatch as their
 * rulesets come online; until any feature activates, the cost is a single volatile read
 * and the vanilla icon passes through untouched.
 */
public final class VertexIcons
{
    private static volatile boolean active = false;
    private static boolean grassReady = false;
    private static boolean grassBroken = false;
    private static Object grassBlock;
    private static Object grassTopIcon;
    private static java.lang.reflect.Method getBlock;

    /** Flipped by feature loaders when a ruleset is installed. */
    public static void activate()
    {
        active = true;
    }

    /** Diagnostic: proves whether in-world sprite resolution actually flows through here. */
    public static long hits = 0L;
    public static long sideHits = 0L;

    public static Object adjust(Object icon, Object block, Object world, int x, int y, int z, int side)
    {
        ++hits;

        if (side >= 2)
        {
            ++sideHits;
        }

        // Better grass: side faces of a grass block render the top texture when the
        // neighbor across that face, one down, is also grass - the seam a player sees on
        // every hillside. Config-gated (default off, matching community expectations);
        // dispatch cost when off is one config check behind the activation flag.
        if (side >= 2 && block != null && VertexConfig.enabled("betterGrass"))
        {
            try
            {
                if (!grassReady && !grassBroken)
                {
                    initGrass(block, world);
                }

                if (grassReady && block == grassBlock)
                {
                    int nx = x + (side == 4 ? -1 : (side == 5 ? 1 : 0));
                    int nz = z + (side == 2 ? -1 : (side == 3 ? 1 : 0));

                    if (getBlock.invoke(world, Integer.valueOf(nx), Integer.valueOf(y - 1), Integer.valueOf(nz)) == grassBlock)
                    {
                        return grassTopIcon;
                    }
                }
            }
            catch (Exception e)
            {
                grassBroken = true;
                net.minecraft.launchwrapper.LogWrapper.severe("[Vertex] Better grass disabled after failure");
                e.printStackTrace();
            }
        }

        if (!active)
        {
            return icon;
        }

        // Further dispatch lands with each ruleset loader: CTM (blob class -> tile),
        // natural variants, emissive overlay scheduling. Ordering: CTM first.
        return icon;
    }

    private static void initGrass(Object sampleBlock, Object world) throws Exception
    {
        ClassLoader gameLoader = sampleBlock.getClass().getClassLoader();
        Class<?> blocks = Class.forName(vertex.Mappings.BLOCKS_REGISTRY, false, gameLoader);
        java.lang.reflect.Field grassField = blocks.getDeclaredField(vertex.Mappings.BLOCKS_GRASS);
        grassField.setAccessible(true);
        grassBlock = grassField.get(null);
        java.lang.reflect.Method getIcon = grassBlock.getClass().getMethod(vertex.Mappings.BLOCK_GET_ICON_META, Integer.TYPE, Integer.TYPE);
        grassTopIcon = getIcon.invoke(grassBlock, Integer.valueOf(1), Integer.valueOf(0));

        for (java.lang.reflect.Method method : world.getClass().getMethods())
        {
            if (method.getName().equals(vertex.Mappings.IBA_GET_BLOCK) && method.getParameterTypes().length == 3
                && method.getParameterTypes()[0] == Integer.TYPE)
            {
                getBlock = method;
            }
        }

        if (grassTopIcon == null || getBlock == null)
        {
            throw new IllegalStateException("better grass handles unresolved");
        }

        grassReady = true;
        net.minecraft.launchwrapper.LogWrapper.info("[Vertex] Better grass armed");
    }

    private VertexIcons()
    {
    }
}
