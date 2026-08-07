package vertex.benchmark.quick;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Identifies supported client JARs without loading code from them. */
public final class ClientArtifactClassifier
{
    private static final int MAX_VERSION_CLASS_BYTES = 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 50000;
    private static final String MAIN_CLASS = "net/minecraft/client/main/Main.class";
    private static final String MINECRAFT_CLASS = "bao.class";
    private static final String VERTEX_TWEAKER = "vertex/VertexTweaker.class";
    private static final String VERTEX_TRANSFORMER =
        "vertex/transform/VertexTransformer.class";
    private static final String OPTIFINE_TWEAKER = "optifine/OptiFineTweaker.class";
    private static final String OPTIFINE_TRANSFORMER =
        "optifine/OptiFineClassTransformer.class";
    private static final String OPTIFINE_CONFIG = "Config.class";
    private static final String BENCHMARK_MAIN =
        "vertex/benchmark/BenchmarkMain.class";

    public DetectedClient classify(Path artifact)
    {
        if (artifact == null)
        {
            return unknown(null, "No JAR was supplied.");
        }

        Path path = artifact.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path))
        {
            return unknown(path, "The path does not contain a file.");
        }

        JarFile jar = null;
        try
        {
            jar = new JarFile(path.toFile(), false);

            if (jar.size() > MAX_ARCHIVE_ENTRIES)
            {
                return unknown(path, "The JAR has too many entries to inspect.");
            }

            if (hasFile(jar, BENCHMARK_MAIN))
            {
                return unknown(path,
                    "This is the benchmark JAR. Drop a Minecraft client JAR.");
            }

            boolean vertex = hasFile(jar, VERTEX_TWEAKER)
                && hasFile(jar, VERTEX_TRANSFORMER);
            boolean optifine = hasFile(jar, OPTIFINE_TWEAKER)
                && hasFile(jar, OPTIFINE_TRANSFORMER);

            if (vertex && optifine)
            {
                return unknown(path, "The JAR contains conflicting client markers.");
            }

            if (vertex)
            {
                return detected(path, DetectedClient.Type.VERTEX);
            }

            if (optifine)
            {
                return classContains(jar, OPTIFINE_CONFIG, "1.7.10")
                    ? detected(path, DetectedClient.Type.OPTIFINE)
                    : unknown(path, "The OptiFine JAR is not for Minecraft 1.7.10.");
            }

            if (hasFile(jar, MAIN_CLASS) && hasFile(jar, MINECRAFT_CLASS)
                && classContains(jar, MINECRAFT_CLASS, "1.7.10"))
            {
                return detected(path, DetectedClient.Type.VANILLA_1_7_10);
            }

            return unknown(path, "The JAR is not a supported 1.7.10 client.");
        }
        catch (IOException error)
        {
            return unknown(path, "The file is not a readable JAR archive.");
        }
        catch (SecurityException error)
        {
            return unknown(path, "The JAR cannot be read.");
        }
        finally
        {
            if (jar != null)
            {
                try
                {
                    jar.close();
                }
                catch (IOException ignored)
                {
                    // The structure check is complete.
                }
            }
        }
    }

    private static DetectedClient detected(Path path, DetectedClient.Type type)
    {
        return new DetectedClient(path, type, null);
    }

    private static DetectedClient unknown(Path path, String reason)
    {
        return new DetectedClient(path, DetectedClient.Type.UNKNOWN, reason);
    }

    private static boolean hasFile(JarFile jar, String name)
    {
        JarEntry entry = jar.getJarEntry(name);
        return entry != null && !entry.isDirectory();
    }

    private static boolean classContains(JarFile jar, String name, String text)
        throws IOException
    {
        JarEntry entry = jar.getJarEntry(name);
        if (entry == null || entry.isDirectory()
            || entry.getSize() > MAX_VERSION_CLASS_BYTES)
        {
            return false;
        }

        InputStream input = jar.getInputStream(entry);
        try
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;

            while ((count = input.read(buffer)) >= 0)
            {
                if (count == 0)
                {
                    continue;
                }
                total += count;
                if (total > MAX_VERSION_CLASS_BYTES)
                {
                    return false;
                }
                output.write(buffer, 0, count);
            }

            String content = new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
            return content.contains(text);
        }
        finally
        {
            input.close();
        }
    }
}
