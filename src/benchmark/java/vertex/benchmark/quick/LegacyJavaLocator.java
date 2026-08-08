package vertex.benchmark.quick;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Finds a Java runtime that can start the legacy client. */
public final class LegacyJavaLocator
{
    public Path find(Path minecraftDirectory) throws IOException
    {
        List<Path> candidates = new ArrayList<Path>();
        String executable = isWindows() ? "javaw.exe" : "java";
        add(candidates, System.getenv("VERTEX_BENCHMARK_JAVA"), "bin", executable);

        if (isWindows())
        {
            String local = System.getenv("LOCALAPPDATA");
            add(candidates, local,
                "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local",
                "runtime", "jre-legacy", "windows-x64", "jre-legacy", "bin", executable);
            add(candidates, local, "Minecraft Launcher", "runtime", "jre-legacy",
                "windows-x64", "jre-legacy", "bin", executable);
            add(candidates, System.getenv("ProgramFiles(x86)"), "Minecraft Launcher",
                "runtime", "jre-x64", "bin", executable);
        }

        candidates.add(minecraftDirectory.resolve("runtime").resolve("jre-legacy")
            .resolve(isWindows() ? "windows-x64" : "linux")
            .resolve("jre-legacy").resolve("bin").resolve(executable));
        add(candidates, System.getenv("JAVA_HOME"), "bin", executable);
        candidates.add(Paths.get(System.getProperty("java.home"), "bin", executable));

        for (Path candidate : candidates)
        {
            if (candidate != null && Files.isRegularFile(candidate))
            {
                return candidate.toAbsolutePath().normalize();
            }
        }

        throw new IOException("A Java runtime for Minecraft 1.7.10 was not found.");
    }

    private static void add(List<Path> target, String root, String... children)
    {
        if (root == null || root.trim().isEmpty())
        {
            return;
        }

        Path result = Paths.get(root);

        for (String child : children)
        {
            result = result.resolve(child);
        }

        target.add(result);
    }

    private static boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
            .contains("win");
    }
}
