package vertex.benchmark.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates a benchmark plan before a run or a write. */
public final class BenchmarkPlanValidator
{
    private static final Pattern SAFE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private BenchmarkPlanValidator()
    {
    }

    public static BenchmarkPlan validate(BenchmarkPlan plan)
    {
        List<String> errors = new ArrayList<String>();
        if (plan == null)
        {
            throw new PlanValidationException("plan is required");
        }

        if (!plan.hasSchemaVersion() || plan.getSchemaVersion() != 1)
        {
            errors.add("schemaVersion must be 1");
        }
        requireSafeId("suiteId", plan.getSuiteId(), errors);
        requireSafeId("baselineProfile", plan.getBaselineProfile(), errors);

        if (!plan.hasRepetitions()
            || plan.getRepetitions() < 1 || plan.getRepetitions() > 10)
        {
            errors.add("repetitions must be from 1 through 10");
        }
        if (!plan.hasWarmupSeconds()
            || plan.getWarmupSeconds() < 0 || plan.getWarmupSeconds() > 1800)
        {
            errors.add("warmupSeconds must be from 0 through 1800");
        }
        if (!plan.hasCaptureSeconds()
            || plan.getCaptureSeconds() < 5 || plan.getCaptureSeconds() > 1800)
        {
            errors.add("captureSeconds must be from 5 through 1800");
        }
        if (!plan.hasCooldownSeconds()
            || plan.getCooldownSeconds() < 0 || plan.getCooldownSeconds() > 1800)
        {
            errors.add("cooldownSeconds must be from 0 through 1800");
        }
        if (!plan.hasSeed())
        {
            errors.add("seed is required");
        }
        requireText("resultDirectory", plan.getResultDirectory(), errors);
        validateCollector(plan.getCollector(), errors);
        validateProfiles(plan, errors);

        if (!errors.isEmpty())
        {
            throw new PlanValidationException(errors);
        }
        return plan;
    }

    public static boolean isSafeId(String value)
    {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    private static void validateCollector(CollectorPlan collector, List<String> errors)
    {
        if (collector == null)
        {
            errors.add("collector is required");
            return;
        }
        if (collector.getType() == null)
        {
            errors.add("collector.type must be presentmon or import");
        }
        if (collector.getMetric() == null
            || collector.getMetric() == CollectorPlan.Metric.AUTO)
        {
            errors.add("collector.metric must be presented or displayed");
        }
        if (collector.getExecutable() != null)
        {
            requireText("collector.executable", collector.getExecutable(), errors);
        }
    }

    private static void validateProfiles(BenchmarkPlan plan, List<String> errors)
    {
        List<ProfilePlan> profiles = plan.profilesRaw();
        if (profiles == null || profiles.size() < 2)
        {
            errors.add("profiles must contain at least two profiles");
        }
        if (profiles == null)
        {
            return;
        }

        Set<String> ids = new HashSet<String>();
        int baselineCount = 0;
        int profileCount = 0;
        for (int index = 0; index < profiles.size(); index++)
        {
            ProfilePlan profile = profiles.get(index);
            String field = "profiles[" + index + "]";
            if (profile == null)
            {
                errors.add(field + " is required");
                continue;
            }
            profileCount++;
            requireSafeId(field + ".id", profile.getId(), errors);
            if (profile.getId() != null && !ids.add(profile.getId()))
            {
                errors.add("profile id " + profile.getId() + " is not unique");
            }
            if (profile.getId() != null
                && profile.getId().equals(plan.getBaselineProfile()))
            {
                baselineCount++;
            }

            requireText(field + ".label", profile.getLabel(), errors);
            validateLaunch(profile, field, errors);
            validatePaths(profile, field, errors);
            validateMetadata(profile, field, errors);

            requireText(field + ".processName", profile.getProcessName(), errors);
        }

        if (profileCount < 2 && profiles.size() >= 2)
        {
            errors.add("profiles must contain at least two profiles");
        }
        if (baselineCount != 1)
        {
            errors.add("baselineProfile must match exactly one profile id");
        }
    }

    private static void validateLaunch(ProfilePlan profile, String field,
        List<String> errors)
    {
        if (profile.getLaunchMode() == null)
        {
            errors.add(field + ".launchMode must be manual or command");
            return;
        }

        List<String> command = profile.commandRaw();
        if (profile.getLaunchMode() == ProfilePlan.LaunchMode.COMMAND)
        {
            if (command == null || command.isEmpty())
            {
                errors.add(field + ".command must contain an executable argument");
                return;
            }
            requireText(field + ".command[0]", command.get(0), errors);
            for (int index = 1; index < command.size(); index++)
            {
                if (command.get(index) == null)
                {
                    errors.add(field + ".command[" + index + "] is required");
                }
            }
        }
        else if (command != null && !command.isEmpty())
        {
            errors.add(field + ".command must be empty for manual launch mode");
        }
    }

    private static void validatePaths(ProfilePlan profile, String field,
        List<String> errors)
    {
        List<String> settings = profile.settingsFilesRaw();
        if (settings == null)
        {
            errors.add(field + ".settingsFiles must be an array");
        }
        else
        {
            for (int index = 0; index < settings.size(); index++)
            {
                requireText(field + ".settingsFiles[" + index + "]",
                    settings.get(index), errors);
            }
        }

        List<String> instructions = profile.instructionsRaw();
        if (instructions == null)
        {
            errors.add(field + ".instructions must be an array");
        }
        else
        {
            for (int index = 0; index < instructions.size(); index++)
            {
                requireText(field + ".instructions[" + index + "]",
                    instructions.get(index), errors);
            }
        }
    }

    private static void validateMetadata(ProfilePlan profile, String field,
        List<String> errors)
    {
        Map<String, String> metadata = profile.metadataRaw();
        if (metadata == null)
        {
            errors.add(field + ".metadata must be an object");
            return;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet())
        {
            if (isBlank(entry.getKey()))
            {
                errors.add(field + ".metadata keys must not be blank");
            }
            else if (PlanEnvironment.isCredentialName(entry.getKey()))
            {
                errors.add(field + ".metadata key " + entry.getKey()
                    + " must not name a credential");
            }
            if (entry.getValue() == null)
            {
                errors.add(field + ".metadata[" + entry.getKey() + "] is required");
            }
        }
    }

    private static void requireSafeId(String field, String value, List<String> errors)
    {
        if (!isSafeId(value))
        {
            errors.add(field + " must use 1 to 64 letters, numbers, dots, dashes, or underscores");
        }
    }

    private static void requireText(String field, String value, List<String> errors)
    {
        if (isBlank(value))
        {
            errors.add(field + " must not be blank");
        }
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().length() == 0;
    }
}
