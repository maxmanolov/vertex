package vertex.benchmark.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Defines one repeatable benchmark suite. */
public final class BenchmarkPlan
{
    private Integer schemaVersion;
    private String suiteId;
    private String baselineProfile;
    private Integer repetitions;
    private Integer warmupSeconds;
    private Integer captureSeconds;
    private Integer cooldownSeconds;
    private Long seed;
    private String resultDirectory;
    private CollectorPlan collector;
    private List<ProfilePlan> profiles = new ArrayList<ProfilePlan>();

    public BenchmarkPlan()
    {
    }

    public int getSchemaVersion()
    {
        return schemaVersion == null ? 0 : schemaVersion.intValue();
    }

    public void setSchemaVersion(int schemaVersion)
    {
        this.schemaVersion = Integer.valueOf(schemaVersion);
    }

    public String getSuiteId()
    {
        return suiteId;
    }

    public void setSuiteId(String suiteId)
    {
        this.suiteId = suiteId;
    }

    public String getBaselineProfile()
    {
        return baselineProfile;
    }

    public void setBaselineProfile(String baselineProfile)
    {
        this.baselineProfile = baselineProfile;
    }

    public int getRepetitions()
    {
        return repetitions == null ? 0 : repetitions.intValue();
    }

    public void setRepetitions(int repetitions)
    {
        this.repetitions = Integer.valueOf(repetitions);
    }

    public int getWarmupSeconds()
    {
        return warmupSeconds == null ? 0 : warmupSeconds.intValue();
    }

    public void setWarmupSeconds(int warmupSeconds)
    {
        this.warmupSeconds = Integer.valueOf(warmupSeconds);
    }

    public int getCaptureSeconds()
    {
        return captureSeconds == null ? 0 : captureSeconds.intValue();
    }

    public void setCaptureSeconds(int captureSeconds)
    {
        this.captureSeconds = Integer.valueOf(captureSeconds);
    }

    public int getCooldownSeconds()
    {
        return cooldownSeconds == null ? 0 : cooldownSeconds.intValue();
    }

    public void setCooldownSeconds(int cooldownSeconds)
    {
        this.cooldownSeconds = Integer.valueOf(cooldownSeconds);
    }

    public long getSeed()
    {
        return seed == null ? 0L : seed.longValue();
    }

    public void setSeed(long seed)
    {
        this.seed = Long.valueOf(seed);
    }

    public String getResultDirectory()
    {
        return resultDirectory;
    }

    public void setResultDirectory(String resultDirectory)
    {
        this.resultDirectory = resultDirectory;
    }

    public CollectorPlan getCollector()
    {
        return collector;
    }

    public void setCollector(CollectorPlan collector)
    {
        this.collector = collector;
    }

    public List<ProfilePlan> getProfiles()
    {
        if (profiles == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(profiles);
    }

    public void setProfiles(List<ProfilePlan> profiles)
    {
        this.profiles = profiles == null ? null : new ArrayList<ProfilePlan>(profiles);
    }

    boolean hasSchemaVersion()
    {
        return schemaVersion != null;
    }

    boolean hasRepetitions()
    {
        return repetitions != null;
    }

    boolean hasWarmupSeconds()
    {
        return warmupSeconds != null;
    }

    boolean hasCaptureSeconds()
    {
        return captureSeconds != null;
    }

    boolean hasCooldownSeconds()
    {
        return cooldownSeconds != null;
    }

    boolean hasSeed()
    {
        return seed != null;
    }

    List<ProfilePlan> profilesRaw()
    {
        return profiles;
    }
}
