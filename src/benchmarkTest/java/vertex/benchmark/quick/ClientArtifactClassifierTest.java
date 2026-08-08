package vertex.benchmark.quick;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClientArtifactClassifierTest
{
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private final ClientArtifactClassifier classifier = new ClientArtifactClassifier();

    @Test
    public void detectsVertexFromClassEntriesInsteadOfTheFileName() throws Exception
    {
        Path jar = createJar("plain-client.jar",
            "vertex/VertexTweaker.class", "tweaker",
            "vertex/transform/VertexTransformer.class", "transformer");

        DetectedClient detected = classifier.classify(jar);

        assertEquals(DetectedClient.Type.VERTEX, detected.getType());
        assertEquals("Vertex", detected.getLabel());
        assertTrue(detected.isSupported());
    }

    @Test
    public void detectsOptiFineFromClassEntriesInsteadOfTheFileName() throws Exception
    {
        Path jar = createJar("Vertex.jar",
            "optifine/OptiFineTweaker.class", "tweaker",
            "optifine/OptiFineClassTransformer.class", "transformer",
            "Config.class", "OptiFine for Minecraft 1.7.10");

        assertEquals(DetectedClient.Type.OPTIFINE, classifier.classify(jar).getType());
    }

    @Test
    public void rejectsOptiFineForAnotherMinecraftVersion() throws Exception
    {
        Path jar = createJar("wrong-version.jar",
            "optifine/OptiFineTweaker.class", "tweaker",
            "optifine/OptiFineClassTransformer.class", "transformer",
            "Config.class", "OptiFine for Minecraft 1.8.9");

        DetectedClient detected = classifier.classify(jar);

        assertEquals(DetectedClient.Type.UNKNOWN, detected.getType());
        assertTrue(detected.getReason().contains("not for Minecraft 1.7.10"));
    }

    @Test
    public void rejectsConflictingClientMarkers() throws Exception
    {
        Path jar = createJar("ambiguous.jar",
            "vertex/VertexTweaker.class", "tweaker",
            "vertex/transform/VertexTransformer.class", "transformer",
            "optifine/OptiFineTweaker.class", "tweaker",
            "optifine/OptiFineClassTransformer.class", "transformer",
            "Config.class", "OptiFine for Minecraft 1.7.10");

        DetectedClient detected = classifier.classify(jar);

        assertEquals(DetectedClient.Type.UNKNOWN, detected.getType());
        assertTrue(detected.getReason().contains("conflicting"));
    }

    @Test
    public void detectsVanillaOnlyWhenTheClientClassHasTheExpectedVersion()
        throws Exception
    {
        Path jar = createJar("renamed.jar",
            "net/minecraft/client/main/Main.class", "main",
            "bao.class", "class data 1.7.10 class data");

        assertEquals(DetectedClient.Type.VANILLA_1_7_10,
            classifier.classify(jar).getType());
    }

    @Test
    public void rejectsAClientFromAnotherVersion() throws Exception
    {
        Path jar = createJar("minecraft-1.7.10.jar",
            "net/minecraft/client/main/Main.class", "main",
            "bao.class", "class data 1.8.9 class data");

        DetectedClient detected = classifier.classify(jar);

        assertEquals(DetectedClient.Type.UNKNOWN, detected.getType());
        assertFalse(detected.isSupported());
        assertNotNull(detected.getReason());
    }

    @Test
    public void rejectsAnUnreadableArchive() throws Exception
    {
        Path file = temporary.newFile("not-a-client.jar").toPath();
        Files.write(file, "not a zip".getBytes(StandardCharsets.UTF_8));

        DetectedClient detected = classifier.classify(file);

        assertEquals(DetectedClient.Type.UNKNOWN, detected.getType());
        assertTrue(detected.getReason().contains("readable JAR"));
    }

    @Test
    public void explainsWhenTheBenchmarkJarIsDropped() throws Exception
    {
        Path jar = createJar("vertex-benchmark.jar",
            "vertex/benchmark/BenchmarkMain.class", "main");

        DetectedClient detected = classifier.classify(jar);

        assertEquals(DetectedClient.Type.UNKNOWN, detected.getType());
        assertTrue(detected.getReason().contains("benchmark JAR"));
        assertTrue(detected.getReason().contains("Minecraft client JAR"));
    }

    @Test
    public void stopsBeforeItInspectsAnArchiveWithTooManyEntries() throws Exception
    {
        Path path = temporary.getRoot().toPath().resolve("large.jar");
        JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path));
        try
        {
            for (int index = 0; index <= 50000; ++index)
            {
                jar.putNextEntry(new JarEntry("entry-" + index));
                jar.closeEntry();
            }
        }
        finally
        {
            jar.close();
        }

        DetectedClient detected = classifier.classify(path);

        assertEquals(DetectedClient.Type.UNKNOWN, detected.getType());
        assertTrue(detected.getReason().contains("too many entries"));
    }

    @Test
    public void rejectsAMissingPath()
    {
        Path path = temporary.getRoot().toPath().resolve("missing.jar");

        DetectedClient detected = classifier.classify(path);

        assertEquals(DetectedClient.Type.UNKNOWN, detected.getType());
        assertTrue(detected.getReason().contains("does not contain a file"));
    }

    private Path createJar(String name, String... entries) throws Exception
    {
        Path path = temporary.getRoot().toPath().resolve(name);
        OutputStream file = Files.newOutputStream(path);
        JarOutputStream jar = new JarOutputStream(file);
        try
        {
            for (int index = 0; index < entries.length; index += 2)
            {
                jar.putNextEntry(new JarEntry(entries[index]));
                jar.write(entries[index + 1].getBytes(StandardCharsets.ISO_8859_1));
                jar.closeEntry();
            }
        }
        finally
        {
            jar.close();
        }
        return path;
    }
}
