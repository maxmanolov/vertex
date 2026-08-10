package vertex.benchmark.quick;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Starts one disposable 1.7.10 client with the neutral benchmark driver. */
public final class LegacyClientLauncher
{
    public Process launch(LegacyInstallation installation, Path candidateJar,
        String candidateTweaker, Path runDirectory, long settleMillis) throws Exception
    {
        Path game = runDirectory.resolve("game");
        Path suiteDirectory = runDirectory.getParent() == null ? runDirectory
            : runDirectory.getParent().getParent();
        Path natives = suiteDirectory.resolve("natives");
        Path control = runDirectory.resolve("control");
        Files.createDirectories(game);
        Files.createDirectories(control);
        Files.write(runDirectory.resolve(".benchmark-session"),
            "vertex-benchmark-v1\n".getBytes(StandardCharsets.UTF_8));
        writeOptions(game.resolve("options.txt"));

        prepareNatives(installation, natives);

        Path self = codeSource();
        List<String> classPath = new ArrayList<String>();

        for (Path library : installation.getClassPath())
        {
            classPath.add(library.toString());
        }

        classPath.add(installation.getClientJar().toString());
        classPath.add(self.toString());

        if (candidateJar != null)
        {
            classPath.add(candidateJar.toAbsolutePath().normalize().toString());
        }

        Path javaExecutable = new LegacyJavaLocator().find(installation.getMinecraftDirectory());
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable.toString());
        addPlatformJvmArguments(command, System.getProperty("os.name"));
        command.add("-Xms512m");
        command.add("-Xmx2048m");
        command.add("-Djava.library.path=" + natives.toAbsolutePath());
        command.add("-Dorg.lwjgl.librarypath=" + natives.toAbsolutePath());
        command.add("-Dnet.java.games.input.librarypath=" + natives.toAbsolutePath());
        command.add("-Dvertex.benchmark.controlDir=" + control.toAbsolutePath());
        command.add("-Dvertex.benchmark.settleMillis=" + settleMillis);

        if (candidateTweaker != null)
        {
            command.add("-Dvertex.benchmark.delegateArguments=true");
        }

        command.add("-cp");
        command.add(join(classPath, java.io.File.pathSeparator));
        command.add("net.minecraft.launchwrapper.Launch");
        command.add("--username");
        command.add("Benchmark");
        command.add("--version");
        command.add("1.7.10-benchmark");
        command.add("--gameDir");
        command.add(game.toAbsolutePath().toString());
        command.add("--assetsDir");
        command.add(installation.getAssetsDirectory().toAbsolutePath().toString());
        command.add("--assetIndex");
        command.add("1.7.10");
        command.add("--uuid");
        command.add("00000000000000000000000000000000");
        command.add("--accessToken");
        command.add("0");
        command.add("--userProperties");
        command.add("{}");
        command.add("--userType");
        command.add("legacy");
        command.add("--width");
        command.add("1280");
        command.add("--height");
        command.add("720");
        command.add("--tweakClass");
        command.add("vertex.benchmark.game.BenchmarkTweaker");

        if (candidateTweaker != null)
        {
            command.add("--tweakClass");
            command.add(candidateTweaker);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(game.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(runDirectory.resolve("client.log").toFile());
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    static void addPlatformJvmArguments(List<String> command, String osName)
    {
        if (osName.toLowerCase(Locale.ROOT).contains("mac"))
        {
            command.add("-XstartOnFirstThread");
        }
    }

    static void extractNatives(Path archive, Path target) throws IOException
    {
        ZipInputStream input = new ZipInputStream(Files.newInputStream(archive));

        try
        {
            ZipEntry entry;

            while ((entry = input.getNextEntry()) != null)
            {
                String name = entry.getName();

                if (entry.isDirectory() || name.toUpperCase(Locale.ROOT).startsWith("META-INF/"))
                {
                    continue;
                }

                Path output = target.resolve(name.replace('/', java.io.File.separatorChar))
                    .normalize();

                if (!output.startsWith(target))
                {
                    throw new IOException("A native archive contains an unsafe path.");
                }

                Path parent = output.getParent();

                if (parent != null)
                {
                    Files.createDirectories(parent);
                }

                Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            input.close();
        }
    }

    private static void prepareNatives(LegacyInstallation installation, Path target)
        throws IOException
    {
        Path marker = target.resolve(".complete");

        if (Files.isRegularFile(marker))
        {
            return;
        }

        Files.createDirectories(target);

        for (Path archive : installation.getNativeArchives())
        {
            extractNatives(archive, target);
        }

        Files.write(marker, "minecraft-1.7.10\n".getBytes(StandardCharsets.UTF_8));
    }

    private static Path codeSource() throws Exception
    {
        CodeSource source = LegacyClientLauncher.class.getProtectionDomain().getCodeSource();

        if (source == null)
        {
            throw new IOException("The benchmark application location is not available.");
        }

        return java.nio.file.Paths.get(source.getLocation().toURI()).toAbsolutePath().normalize();
    }

    private static void writeOptions(Path path) throws IOException
    {
        BufferedWriter output = Files.newBufferedWriter(path, StandardCharsets.UTF_8);

        try
        {
            output.write("invertYMouse:false\n");
            output.write("mouseSensitivity:0.5\n");
            output.write("fov:0.0\n");
            output.write("gamma:0.0\n");
            output.write("saturation:0.0\n");
            output.write("renderDistance:8\n");
            output.write("guiScale:2\n");
            output.write("particles:2\n");
            output.write("bobView:false\n");
            output.write("anaglyph3d:false\n");
            output.write("advancedOpengl:false\n");
            output.write("maxFps:260\n");
            output.write("fboEnable:true\n");
            output.write("difficulty:0\n");
            output.write("fancyGraphics:true\n");
            output.write("ao:2\n");
            output.write("clouds:true\n");
            output.write("resourcePacks:[]\n");
            output.write("lastServer:\n");
            output.write("lang:en_US\n");
            output.write("snooperEnabled:false\n");
            output.write("fullscreen:false\n");
            output.write("enableVsync:false\n");
            output.write("pauseOnLostFocus:false\n");
            output.write("mipmapLevels:0\n");
            output.write("anisotropicFiltering:1\n");
        }
        finally
        {
            output.close();
        }
    }

    private static String join(List<String> values, String separator)
    {
        StringBuilder result = new StringBuilder();

        for (String value : values)
        {
            if (result.length() > 0)
            {
                result.append(separator);
            }

            result.append(value);
        }

        return result.toString();
    }
}
