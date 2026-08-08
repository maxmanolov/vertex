package vertex.benchmark.quick;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public class MinecraftInstallDiscoveryTest
{
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void findsACompleteRequestedInstall() throws Exception
    {
        Path minecraft = createInstall(temporary.newFolder("requested").toPath());

        MinecraftInstallDiscovery.Installation installation =
            new MinecraftInstallDiscovery().discover(minecraft);

        assertEquals(minecraft.toAbsolutePath().normalize(),
            installation.getMinecraftDirectory());
        assertEquals(minecraft.resolve("versions/1.7.10/1.7.10.jar")
            .toAbsolutePath().normalize(), installation.getClientJar());
        assertEquals(minecraft.resolve("assets/indexes/1.7.10.json")
            .toAbsolutePath().normalize(), installation.getAssetIndex());
        assertEquals("1.7.10", installation.getAssetIndexId());
    }

    @Test
    public void findsTheWindowsLauncherDirectoryFromAppData() throws Exception
    {
        Path appData = temporary.newFolder("roaming").toPath();
        Path minecraft = createInstall(appData.resolve(".minecraft"));
        Path home = temporary.newFolder("home").toPath();
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("APPDATA", appData.toString());
        MinecraftInstallDiscovery discovery = new MinecraftInstallDiscovery(
            environment, "Windows 11", home, new ClientArtifactClassifier());

        MinecraftInstallDiscovery.Installation installation = discovery.discover();

        assertEquals(minecraft.toAbsolutePath().normalize(),
            installation.getMinecraftDirectory());
    }

    @Test
    public void findsTheStandardHomeDirectoryOnLinux() throws Exception
    {
        Path home = temporary.newFolder("linux-home").toPath();
        Path minecraft = createInstall(home.resolve(".minecraft"));
        MinecraftInstallDiscovery discovery = new MinecraftInstallDiscovery(
            Collections.<String, String>emptyMap(), "Linux", home,
            new ClientArtifactClassifier());

        assertEquals(minecraft.toAbsolutePath().normalize(),
            discovery.discover().getMinecraftDirectory());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnInstallWithoutItsAssetIndex() throws Exception
    {
        Path minecraft = createInstall(temporary.newFolder("no-assets").toPath());
        Files.delete(minecraft.resolve("assets/indexes/1.7.10.json"));

        new MinecraftInstallDiscovery().discover(minecraft);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnInstallWithTheWrongVersionMetadata() throws Exception
    {
        Path minecraft = createInstall(temporary.newFolder("wrong-version").toPath());
        Files.write(minecraft.resolve("versions/1.7.10/1.7.10.json"),
            versionJson("1.8.9").getBytes(StandardCharsets.UTF_8));

        new MinecraftInstallDiscovery().discover(minecraft);
    }

    @Test(expected = IllegalStateException.class)
    public void reportsWhenNoDefaultInstallExists() throws Exception
    {
        Path home = temporary.newFolder("empty-home").toPath();
        MinecraftInstallDiscovery discovery = new MinecraftInstallDiscovery(
            Collections.<String, String>emptyMap(), "Linux", home,
            new ClientArtifactClassifier());

        discovery.discover();
    }

    private Path createInstall(Path minecraft) throws Exception
    {
        Path version = minecraft.resolve("versions/1.7.10");
        Path assets = minecraft.resolve("assets/indexes");
        Files.createDirectories(version);
        Files.createDirectories(assets);
        Files.createDirectories(minecraft.resolve("libraries"));

        Path jarPath = version.resolve("1.7.10.jar");
        OutputStream file = Files.newOutputStream(jarPath);
        JarOutputStream jar = new JarOutputStream(file);
        try
        {
            addEntry(jar, "net/minecraft/client/main/Main.class", "main");
            addEntry(jar, "bao.class", "class data 1.7.10 class data");
        }
        finally
        {
            jar.close();
        }

        Files.write(version.resolve("1.7.10.json"),
            versionJson("1.7.10").getBytes(StandardCharsets.UTF_8));
        Files.write(assets.resolve("1.7.10.json"),
            "{}".getBytes(StandardCharsets.UTF_8));
        return minecraft;
    }

    private static void addEntry(JarOutputStream jar, String name, String content)
        throws Exception
    {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content.getBytes(StandardCharsets.ISO_8859_1));
        jar.closeEntry();
    }

    private static String versionJson(String version)
    {
        return "{\"id\":\"" + version
            + "\",\"assetIndex\":{\"id\":\"1.7.10\"}}";
    }
}
