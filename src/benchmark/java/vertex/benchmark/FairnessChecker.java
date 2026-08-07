package vertex.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vertex.benchmark.plan.BenchmarkPlan;
import vertex.benchmark.plan.ProfilePlan;

/** Checks declared settings that must match across profiles. */
public final class FairnessChecker
{
    private static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
        "minecraftVersion", "preset", "scenario", "world", "resolution", "fullscreen",
        "renderDistance", "vsync", "fpsLimit", "graphics", "resourcePack", "shaders",
        "fov", "difficulty", "time", "weather", "heapMB", "javaVersion"));

    public static List<String> check(BenchmarkPlan plan)
    {
        List<String> warnings = new ArrayList<String>();

        for (String key : KEYS)
        {
            Set<String> values = new LinkedHashSet<String>();
            int present = 0;

            for (ProfilePlan profile : plan.getProfiles())
            {
                Map<String, String> metadata = profile.getMetadata();

                if (metadata.containsKey(key))
                {
                    ++present;
                    values.add(metadata.get(key));
                }
            }

            if (present > 0 && present < plan.getProfiles().size())
            {
                warnings.add("Metadata field " + key + " is missing from one or more profiles.");
            }
            else if (values.size() > 1)
            {
                warnings.add("Metadata field " + key + " does not match across profiles.");
            }
        }

        return warnings;
    }

    private FairnessChecker()
    {
    }
}
