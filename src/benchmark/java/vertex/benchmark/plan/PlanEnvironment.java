package vertex.benchmark.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands selected variables without access to the process environment. */
public final class PlanEnvironment
{
    private static final Pattern VARIABLE = Pattern.compile(
        "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}|%([A-Za-z_][A-Za-z0-9_]*)%");
    private static final Pattern CREDENTIAL_NAME = Pattern.compile(
        "(^|_)(TOKEN|PASSWORD|PASSWD|SECRET|SESSION|BEARER|COOKIE|CREDENTIALS?|API_?KEY|ACCESS_?KEY|PRIVATE_?KEY|AUTH|AUTHENTICATION|AUTHORIZATION)(_|$)");
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile(
        "([a-z0-9])([A-Z])");
    private static final Pattern NAME_SEPARATOR = Pattern.compile("[^A-Za-z0-9]+");

    private PlanEnvironment()
    {
    }

    public static BenchmarkPlan resolve(BenchmarkPlan source,
        Map<String, String> variables)
    {
        BenchmarkPlanValidator.validate(source);
        if (variables == null)
        {
            throw new IllegalArgumentException("variables are required");
        }

        BenchmarkPlan target = new BenchmarkPlan();
        target.setSchemaVersion(source.getSchemaVersion());
        target.setSuiteId(source.getSuiteId());
        target.setBaselineProfile(source.getBaselineProfile());
        target.setRepetitions(source.getRepetitions());
        target.setWarmupSeconds(source.getWarmupSeconds());
        target.setCaptureSeconds(source.getCaptureSeconds());
        target.setCooldownSeconds(source.getCooldownSeconds());
        target.setSeed(source.getSeed());
        target.setResultDirectory(expand(source.getResultDirectory(), variables));
        target.setCollector(copyCollector(source.getCollector(), variables));

        List<ProfilePlan> profiles = new ArrayList<ProfilePlan>();
        for (ProfilePlan profile : source.getProfiles())
        {
            profiles.add(copyProfile(profile, variables));
        }
        target.setProfiles(profiles);
        return BenchmarkPlanValidator.validate(target);
    }

    private static CollectorPlan copyCollector(CollectorPlan source,
        Map<String, String> variables)
    {
        CollectorPlan target = new CollectorPlan();
        target.setType(source.getType());
        target.setMetric(source.getMetric());
        target.setExecutable(expand(source.getExecutable(), variables));
        return target;
    }

    private static ProfilePlan copyProfile(ProfilePlan source,
        Map<String, String> variables)
    {
        ProfilePlan target = new ProfilePlan();
        target.setId(source.getId());
        target.setLabel(source.getLabel());
        target.setLaunchMode(source.getLaunchMode());
        target.setProcessName(expand(source.getProcessName(), variables));
        target.setCommand(expand(source.getCommand(), variables));
        target.setSettingsFiles(expand(source.getSettingsFiles(), variables));
        target.setInstructions(source.getInstructions());
        target.setMetadata(new LinkedHashMap<String, String>(source.getMetadata()));
        return target;
    }

    private static List<String> expand(List<String> values,
        Map<String, String> variables)
    {
        List<String> resolved = new ArrayList<String>();
        for (String value : values)
        {
            resolved.add(expand(value, variables));
        }
        return resolved;
    }

    private static String expand(String value, Map<String, String> variables)
    {
        if (value == null)
        {
            return null;
        }

        Matcher matcher = VARIABLE.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find())
        {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (isCredentialName(name))
            {
                throw new IllegalArgumentException(
                    "credential variable " + name + " is not permitted");
            }
            if (!variables.containsKey(name) || variables.get(name) == null)
            {
                throw new IllegalArgumentException(
                    "environment variable " + name + " is not available");
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(variables.get(name)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    static boolean isCredentialName(String name)
    {
        if (name == null)
        {
            return false;
        }

        String separated = CAMEL_CASE_BOUNDARY.matcher(name).replaceAll("$1_$2");
        String normalized = NAME_SEPARATOR.matcher(separated).replaceAll("_")
            .toUpperCase(Locale.ROOT);
        return CREDENTIAL_NAME.matcher(normalized).find();
    }
}
