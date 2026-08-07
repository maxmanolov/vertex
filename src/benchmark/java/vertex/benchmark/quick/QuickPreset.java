package vertex.benchmark.quick;

/** Defines the two no-plan quick benchmark lengths. */
public enum QuickPreset
{
    FAST("fast", 1, 10000L, 15, 2),
    STANDARD("standard", 3, 20000L, 30, 5);

    private final String value;
    private final int repetitions;
    private final long settleMillis;
    private final int captureSeconds;
    private final int cooldownSeconds;

    QuickPreset(String value, int repetitions, long settleMillis, int captureSeconds,
        int cooldownSeconds)
    {
        this.value = value;
        this.repetitions = repetitions;
        this.settleMillis = settleMillis;
        this.captureSeconds = captureSeconds;
        this.cooldownSeconds = cooldownSeconds;
    }

    public static QuickPreset parse(String value)
    {
        if (value == null || value.trim().isEmpty()
            || "standard".equalsIgnoreCase(value.trim()))
        {
            return STANDARD;
        }

        if ("fast".equalsIgnoreCase(value.trim()))
        {
            return FAST;
        }

        throw new IllegalArgumentException("--preset must be fast or standard.");
    }

    public String getValue()
    {
        return value;
    }

    public int getRepetitions()
    {
        return repetitions;
    }

    public long getSettleMillis()
    {
        return settleMillis;
    }

    public int getCaptureSeconds()
    {
        return captureSeconds;
    }

    public int getCooldownSeconds()
    {
        return cooldownSeconds;
    }
}
