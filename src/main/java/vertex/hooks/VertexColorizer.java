package vertex.hooks;

import vertex.colors.ColorMap;

/**
 * Custom-colors consumers for the colorizer choke points: when a pack supplies a grass or
 * foliage colormap (loaded by VertexPackLoader), the vanilla static colorizer defers to
 * the pack map sampled with vanilla's own climate coordinates. Without a pack map (or with
 * customColors=false) the vanilla body runs untouched.
 */
public final class VertexColorizer
{
    public static boolean hasGrass()
    {
        return VertexPackLoader.grassMap() != null && VertexConfig.enabled("customColors");
    }

    public static int grass(double temperature, double humidity)
    {
        ColorMap map = VertexPackLoader.grassMap();
        return map != null ? map.sample((float)temperature, (float)humidity) : 0xFFFFFF;
    }

    public static boolean hasFoliage()
    {
        return VertexPackLoader.foliageMap() != null && VertexConfig.enabled("customColors");
    }

    public static int foliage(double temperature, double humidity)
    {
        ColorMap map = VertexPackLoader.foliageMap();
        return map != null ? map.sample((float)temperature, (float)humidity) : 0xFFFFFF;
    }

    private VertexColorizer()
    {
    }
}
