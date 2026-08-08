package vertex.benchmark.game;

/** Identifies one neutral benchmark workload. */
public enum ScenarioPhase
{
    STATIC("static"),
    CHUNKS("chunks"),
    BLOCKS("blocks"),
    ENTITIES("entities");

    private final String id;

    ScenarioPhase(String id)
    {
        this.id = id;
    }

    public static ScenarioPhase fromId(String value)
    {
        if (value != null)
        {
            String candidate = value.trim();

            for (ScenarioPhase phase : values())
            {
                if (phase.id.equalsIgnoreCase(candidate))
                {
                    return phase;
                }
            }
        }

        throw new IllegalArgumentException("Unknown benchmark phase: " + value);
    }

    public String getId()
    {
        return id;
    }
}
