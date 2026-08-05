package vertex.colors;

/**
 * A 256x256 colormap sampled by biome climate, using the vanilla convention: x indexes
 * falling temperature, y indexes falling temperature-scaled humidity, both clamped to
 * [0,1]. Backing pixels are ARGB ints (as decoded from a pack's colormap PNG); sampling
 * is allocation-free. Also exposes the average color, which packs expect for far/particle
 * tinting when a single representative color is needed.
 */
public final class ColorMap
{
    public static final int SIZE = 256;

    private final int[] pixels;
    private final int average;

    public ColorMap(int[] pixels)
    {
        if (pixels.length != SIZE * SIZE)
        {
            throw new IllegalArgumentException("Colormap must be 256x256, got " + pixels.length + " pixels");
        }

        this.pixels = pixels;
        long r = 0L;
        long g = 0L;
        long b = 0L;

        for (int pixel : pixels)
        {
            r += pixel >> 16 & 0xFF;
            g += pixel >> 8 & 0xFF;
            b += pixel & 0xFF;
        }

        int count = pixels.length;
        this.average = (int)(r / count) << 16 | (int)(g / count) << 8 | (int)(b / count);
    }

    public int sample(float temperature, float humidity)
    {
        float clampedTemperature = clamp(temperature);
        float clampedHumidity = clamp(humidity) * clampedTemperature;
        int x = (int)((1.0F - clampedTemperature) * 255.0F);
        int y = (int)((1.0F - clampedHumidity) * 255.0F);
        return this.pixels[y << 8 | x] & 0xFFFFFF;
    }

    public int average()
    {
        return this.average;
    }

    private static float clamp(float value)
    {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

}
