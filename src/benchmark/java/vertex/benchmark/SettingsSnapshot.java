package vertex.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records configured setting files without changing them. */
public final class SettingsSnapshot
{
    public static Map<String, String> capture(List<String> configuredPaths, Path planDirectory,
        List<String> warnings)
    {
        Map<String, String> hashes = new LinkedHashMap<String, String>();
        int index = 0;

        for (String configured : configuredPaths)
        {
            ++index;
            Path file;

            try
            {
                file = java.nio.file.Paths.get(configured);
            }
            catch (RuntimeException error)
            {
                warnings.add("Setting path is invalid: " + configured);
                hashes.put(index + ":<invalid>", null);
                continue;
            }

            if (!file.isAbsolute())
            {
                try
                {
                    file = planDirectory.resolve(file).normalize();
                }
                catch (RuntimeException error)
                {
                    warnings.add("Setting path is invalid: " + configured);
                    hashes.put(index + ":<invalid>", null);
                    continue;
                }
            }

            String key = index + ":" + file.getFileName();

            if (!Files.isRegularFile(file))
            {
                warnings.add("Setting file is not available: " + configured);
                hashes.put(key, null);
                continue;
            }

            try
            {
                hashes.put(key, Hashing.sha256(file));
            }
            catch (IOException error)
            {
                warnings.add("Cannot hash setting file: " + configured);
                hashes.put(key, null);
            }
        }

        return hashes;
    }

    public static boolean changed(Map<String, String> before, Map<String, String> after)
    {
        return !before.equals(after);
    }

    public static boolean hasFailures(Map<String, String> snapshot)
    {
        return snapshot.containsValue(null);
    }

    private SettingsSnapshot()
    {
    }
}
