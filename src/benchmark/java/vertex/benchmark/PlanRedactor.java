package vertex.benchmark;

import java.util.ArrayList;
import java.util.List;
import vertex.benchmark.plan.BenchmarkPlan;
import vertex.benchmark.plan.CollectorPlan;
import vertex.benchmark.plan.ProfilePlan;

/** Removes command credentials from the stored plan copy. */
public final class PlanRedactor
{
    public static BenchmarkPlan redact(BenchmarkPlan source)
    {
        BenchmarkPlan target = new BenchmarkPlan();
        target.setSchemaVersion(source.getSchemaVersion());
        target.setSuiteId(source.getSuiteId());
        target.setBaselineProfile(source.getBaselineProfile());
        target.setRepetitions(source.getRepetitions());
        target.setWarmupSeconds(source.getWarmupSeconds());
        target.setCaptureSeconds(source.getCaptureSeconds());
        target.setCooldownSeconds(source.getCooldownSeconds());
        target.setSeed(source.getSeed());
        target.setResultDirectory(source.getResultDirectory());
        CollectorPlan collector = new CollectorPlan();
        collector.setType(source.getCollector().getType());
        collector.setMetric(source.getCollector().getMetric());
        collector.setExecutable(source.getCollector().getExecutable());
        target.setCollector(collector);
        List<ProfilePlan> profiles = new ArrayList<ProfilePlan>();

        for (ProfilePlan sourceProfile : source.getProfiles())
        {
            ProfilePlan profile = new ProfilePlan();
            profile.setId(sourceProfile.getId());
            profile.setLabel(sourceProfile.getLabel());
            profile.setLaunchMode(sourceProfile.getLaunchMode());
            profile.setProcessName(sourceProfile.getProcessName());
            profile.setCommand(redactCommand(sourceProfile.getCommand()));
            profile.setSettingsFiles(sourceProfile.getSettingsFiles());
            profile.setInstructions(sourceProfile.getInstructions());
            profile.setMetadata(sourceProfile.getMetadata());
            profiles.add(profile);
        }

        target.setProfiles(profiles);
        return target;
    }

    static List<String> redactCommand(List<String> command)
    {
        List<String> redacted = new ArrayList<String>();

        if (!command.isEmpty())
        {
            redacted.add("<not stored>");
        }

        return redacted;
    }

    private PlanRedactor()
    {
    }
}
