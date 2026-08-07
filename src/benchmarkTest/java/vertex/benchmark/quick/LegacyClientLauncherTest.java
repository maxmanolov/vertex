package vertex.benchmark.quick;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LegacyClientLauncherTest
{
    @Test
    public void extractsNativeFilesInsideTarget() throws Exception
    {
        Path root = Files.createTempDirectory("native-safe");
        Path archive = root.resolve("natives.jar");
        writeArchive(archive, "folder/native.dll", new byte[] {1, 2, 3});
        Path output = root.resolve("output");
        Files.createDirectories(output);

        LegacyClientLauncher.extractNatives(archive, output);

        assertArrayEquals(new byte[] {1, 2, 3},
            Files.readAllBytes(output.resolve("folder").resolve("native.dll")));
    }

    @Test
    public void rejectsArchivePathTraversal() throws Exception
    {
        Path root = Files.createTempDirectory("native-unsafe");
        Path archive = root.resolve("natives.jar");
        writeArchive(archive, "../outside.dll", new byte[] {1});
        Path output = root.resolve("output");
        Files.createDirectories(output);

        try
        {
            LegacyClientLauncher.extractNatives(archive, output);
            fail("Expected unsafe native path to fail.");
        }
        catch (IOException expected)
        {
            assertTrue(expected.getMessage().contains("unsafe path"));
        }

        assertTrue(!Files.exists(root.resolve("outside.dll")));
    }

    private static void writeArchive(Path path, String name, byte[] data) throws Exception
    {
        OutputStream file = Files.newOutputStream(path);
        ZipOutputStream output = new ZipOutputStream(file);

        try
        {
            output.putNextEntry(new ZipEntry(name));
            output.write(data);
            output.closeEntry();
        }
        finally
        {
            output.close();
        }
    }
}
