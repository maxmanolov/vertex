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

    /** Flipped by feature loaders when a ruleset is installed. */
    public static void activate()
    {
        active = true;
    }

    public static Object adjust(Object icon, Object block, Object world, int x, int y, int z, int side)
    {
        if (!active)
        {
            return icon;
        }

        // Feature dispatch lands with each ruleset loader: CTM (blob class -> tile),
        // natural (deterministic rotation/flip via UV-variant icons), emissive overlay
        // scheduling, better grass side substitution. Ordering: CTM first, then natural.
        return icon;
    }

    private VertexIcons()
    {
    }
}
