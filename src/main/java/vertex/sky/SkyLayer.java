package vertex.sky;

import java.util.Properties;

/**
 * One custom sky layer parsed from the documented pack format
 * (mcpatcher/sky/world0/skyN.properties): a texture source, a fade window, an optional
 * celestial rotation, and a blend mode. Immutable; the render hook consumes an ordered
 * list of these and multiplies each layer's opacity(worldTime) into its draw.
 *
 * A layer without a usable source is rejected at parse time rather than half-loaded.
 */
public final class SkyLayer
{
    public final String source;
    public final SkyLayerTiming timing;
    public final String blend;
    public final boolean rotate;
    public final float speed;
    public final float[] axis;

    private SkyLayer(String source, SkyLayerTiming timing, String blend, boolean rotate, float speed, float[] axis)
    {
        this.source = source;
        this.timing = timing;
        this.blend = blend;
        this.rotate = rotate;
        this.speed = speed;
        this.axis = axis;
    }

    /**
     * Parses one layer. defaultSource is the conventional sibling texture (skyN.png) used
     * when the file omits an explicit source. Returns null when the layer is unusable.
     */
    public static SkyLayer parse(Properties props, String defaultSource)
    {
        if (props == null)
        {
            return null;
        }

        String source = trimmed(props.getProperty("source"), defaultSource);

        if (source == null || source.isEmpty())
        {
            return null;
        }

        SkyLayerTiming timing;
        String startFadeIn = trimmed(props.getProperty("startFadeIn"), null);

        if (startFadeIn != null)
        {
            timing = SkyLayerTiming.parse(startFadeIn,
                required(props, "endFadeIn"),
                required(props, "startFadeOut"),
                trimmed(props.getProperty("endFadeOut"), null));
        }
        else
        {
            // No fade window: the layer is always fully visible.
            timing = new SkyLayerTiming(0, 0, 0, 0)
            {
                public float opacity(long worldTime)
                {
                    return 1.0F;
                }
            };
        }

        String blend = trimmed(props.getProperty("blend"), "add").toLowerCase();
        boolean rotate = !"false".equalsIgnoreCase(trimmed(props.getProperty("rotate"), "true"));
        float speed = parseFloat(props.getProperty("speed"), 1.0F);
        float[] axis = parseAxis(props.getProperty("axis"));
        return new SkyLayer(source, timing, blend, rotate, speed, axis);
    }

    private static String required(Properties props, String key)
    {
        String value = trimmed(props.getProperty(key), null);

        if (value == null)
        {
            throw new IllegalArgumentException("Sky layer declares startFadeIn but not " + key);
        }

        return value;
    }

    private static String trimmed(String value, String fallback)
    {
        return value != null && !value.trim().isEmpty() ? value.trim() : fallback;
    }

    private static float parseFloat(String value, float fallback)
    {
        try
        {
            return value != null ? Float.parseFloat(value.trim()) : fallback;
        }
        catch (NumberFormatException invalid)
        {
            return fallback;
        }
    }

    private static float[] parseAxis(String value)
    {
        float[] fallback = {0.0F, 0.0F, 1.0F};

        if (value == null)
        {
            return fallback;
        }

        String[] parts = value.trim().split("\\s+");

        if (parts.length != 3)
        {
            return fallback;
        }

        try
        {
            return new float[] {Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2])};
        }
        catch (NumberFormatException invalid)
        {
            return fallback;
        }
    }
}
