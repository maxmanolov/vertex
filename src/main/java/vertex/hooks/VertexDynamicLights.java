package vertex.hooks;

/**
 * Dynamic lights engine (docs/ROADMAP.md #6). Pure math: every mixed-brightness lookup
 * during tessellation is adjusted by the strongest nearby dynamic source with linear
 * distance falloff, raising only the block-light component of the packed lightmap
 * coordinate. No world mutation, no lighting engine involvement - multiplayer-safe by
 * construction. Sources are published as a snapshot array by the (future) collector; with
 * no sources the adjustment is a single volatile read, so the hook is behavior-neutral
 * until the collector lands.
 *
 * Packed format (vanilla lightmap coordinates): high half sky, low half block, each in
 * texture units of 16 per light level, capped at 240.
 */
public final class VertexDynamicLights
{
    /** Immutable source snapshot: groups of four ints (x, y, z, level 0-15). */
    private static volatile int[] sources = new int[0];

    public static void publish(int[] snapshot)
    {
        sources = snapshot;
    }

    /** Called from every Block.getMixedBrightnessForBlock return site. */
    public static int adjust(int packed, int x, int y, int z)
    {
        // Fullbright overrides everything, dynamic sources included: geometry bakes at
        // max lightmap coordinates (#116). One volatile read when off.
        if (VertexFullbright.fullbright())
        {
            return VertexFullbright.FULLBRIGHT_PACKED;
        }

        int[] active = sources;

        if (active.length == 0 || !VertexConfig.enabled("dynamicLights"))
        {
            return packed;
        }

        int best = 0;

        for (int i = 0; i < active.length; i += 4)
        {
            int dx = Math.abs(active[i] - x);
            int dy = Math.abs(active[i + 1] - y);
            int dz = Math.abs(active[i + 2] - z);
            int level = active[i + 3] - (dx + dy + dz);

            if (level > best)
            {
                best = level;
            }
        }

        if (best <= 0)
        {
            return packed;
        }

        int blockTex = packed & 0xFFFF;
        int dynamicTex = Math.min(best, 15) * 16;

        if (dynamicTex <= blockTex)
        {
            return packed;
        }

        return (packed & 0xFFFF0000) | Math.min(dynamicTex, 240);
    }

    private VertexDynamicLights()
    {
    }
}
