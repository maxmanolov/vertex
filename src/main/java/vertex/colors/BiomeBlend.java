package vertex.colors;

/**
 * Allocation-free biome color blending: averages RGB over an odd-sized square of samples
 * (the smooth-biomes radius), integer math only. Callers stream samples in via add() and
 * read the running average - no arrays, no per-vertex garbage on the tessellation path.
 */
public final class BiomeBlend
{
    private int red;
    private int green;
    private int blue;
    private int count;

    public void reset()
    {
        this.red = 0;
        this.green = 0;
        this.blue = 0;
        this.count = 0;
    }

    public void add(int rgb)
    {
        this.red += rgb >> 16 & 0xFF;
        this.green += rgb >> 8 & 0xFF;
        this.blue += rgb & 0xFF;
        ++this.count;
    }

    public int average()
    {
        if (this.count == 0)
        {
            return 0xFFFFFF;
        }

        return this.red / this.count << 16 | this.green / this.count << 8 | this.blue / this.count;
    }
}
