package vertex.benchmark;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import vertex.benchmark.plan.BenchmarkPlan;
import vertex.benchmark.plan.ProfilePlan;

import static org.junit.Assert.assertTrue;

public class FairnessCheckerTest
{
    @Test
    public void reportsADeclaredSettingMismatch()
    {
        BenchmarkPlan plan = new BenchmarkPlan();
        ProfilePlan baseline = profile("vanilla", "8");
        ProfilePlan candidate = profile("vertex", "12");
        plan.setProfiles(Arrays.asList(baseline, candidate));
        List<String> warnings = FairnessChecker.check(plan);
        assertTrue(warnings.toString(), warnings.contains(
            "Metadata field renderDistance does not match across profiles."));
    }

    private static ProfilePlan profile(String id, String renderDistance)
    {
        ProfilePlan profile = new ProfilePlan();
        profile.setId(id);
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("renderDistance", renderDistance);
        profile.setMetadata(metadata);
        return profile;
    }
}
