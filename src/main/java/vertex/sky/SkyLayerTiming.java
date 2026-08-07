package vertex.sky;

/**
 * Fade timing for a custom sky layer, from the documented pack format: day-clock times
 * (hh:mm, where 06:00 is tick 0 of Minecraft's 24000-tick day) define a fade-in ramp and
 * a fade-out ramp; the layer holds full opacity between them. Windows may wrap midnight.
 * The documented endFadeOut field determines startFadeOut so both ramps have equal length.
 * Pure math, no game types: opacity(tick) is what the sky render hook multiplies into the
 * layer's blend.
 */
public class SkyLayerTiming
{
    public static final int DAY_TICKS = 24000;

    private final int startFadeIn;
    private final int endFadeIn;
    private final int startFadeOut;
    private final int endFadeOut;

    public SkyLayerTiming(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut)
    {
        this.startFadeIn = startFadeIn;
        this.endFadeIn = endFadeIn;
        this.startFadeOut = startFadeOut;
        this.endFadeOut = endFadeOut;
    }

    public static SkyLayerTiming parse(String startIn, String endIn, String startOut, String endOut)
    {
        int fadeInStart = parseClock(startIn);
        int fadeInEnd = parseClock(endIn);
        int fadeOutStart = parseClock(startOut);
        int fadeOutEnd;

        if (endOut != null)
        {
            fadeOutEnd = parseClock(endOut);
        }
        else
        {
            fadeOutEnd = (fadeOutStart + span(fadeInStart, fadeInEnd)) % DAY_TICKS;
        }

        return new SkyLayerTiming(fadeInStart, fadeInEnd, fadeOutStart, fadeOutEnd);
    }

    /** Documented form: derive fade-out start so its duration matches the fade-in ramp. */
    public static SkyLayerTiming parse(String startIn, String endIn, String endOut)
    {
        int fadeInStart = parseClock(startIn);
        int fadeInEnd = parseClock(endIn);
        int fadeOutEnd = parseClock(endOut);
        int fadeOutStart = (fadeOutEnd - span(fadeInStart, fadeInEnd) + DAY_TICKS) % DAY_TICKS;
        return new SkyLayerTiming(fadeInStart, fadeInEnd, fadeOutStart, fadeOutEnd);
    }

    /** Layer opacity in [0,1] at a world time (any tick value; only day phase matters). */
    public float opacity(long worldTime)
    {
        int tick = (int)(worldTime % DAY_TICKS);

        if (tick < 0)
        {
            tick += DAY_TICKS;
        }

        if (within(tick, this.endFadeIn, this.startFadeOut))
        {
            return 1.0F;
        }

        if (within(tick, this.startFadeIn, this.endFadeIn))
        {
            return (float)offset(this.startFadeIn, tick) / (float)span(this.startFadeIn, this.endFadeIn);
        }

        if (within(tick, this.startFadeOut, this.endFadeOut))
        {
            return 1.0F - (float)offset(this.startFadeOut, tick) / (float)span(this.startFadeOut, this.endFadeOut);
        }

        return 0.0F;
    }

    /** Converts "hh:mm" day-clock to ticks; 06:00 is tick 0. */
    static int parseClock(String value)
    {
        String[] parts = value.trim().split(":");

        if (parts.length != 2)
        {
            throw new IllegalArgumentException("Bad clock value: " + value);
        }

        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);

        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59)
        {
            throw new IllegalArgumentException("Bad clock value: " + value);
        }

        int sinceSix = ((hours - 6) + 24) % 24;
        return sinceSix * 1000 + minutes * 1000 / 60;
    }

    private static int span(int from, int to)
    {
        return ((to - from) + DAY_TICKS) % DAY_TICKS;
    }

    private static int offset(int from, int tick)
    {
        return ((tick - from) + DAY_TICKS) % DAY_TICKS;
    }

    private static boolean within(int tick, int from, int to)
    {
        return offset(from, tick) < span(from, to);
    }
}
