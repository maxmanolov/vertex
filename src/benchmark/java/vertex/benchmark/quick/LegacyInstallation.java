package vertex.benchmark.quick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Resolves the installed files needed to start an isolated vanilla 1.7.10 client. */
public final class LegacyInstallation
{
    private static final String VANILLA_SHA1 = "e80d9b3bf5085002218d4be59e668bac718abbc6";
    private final Path minecraftDirectory;
    private final Path clientJar;
    private final Path assetsDirectory;
    private final List<Path> classPath;
    private final List<Path> nativeArchives;

    private LegacyInstallation(Path minecraftDirectory, Path clientJar, Path assetsDirectory,
        List<Path> classPath, List<Path> nativeArchives)
    {
        this.minecraftDirectory = minecraftDirectory;
        this.clientJar = clientJar;
        this.assetsDirectory = assetsDirectory;
        this.classPath = Collections.unmodifiableList(new ArrayList<Path>(classPath));
        this.nativeArchives = Collections.unmodifiableList(new ArrayList<Path>(nativeArchives));
    }

    public static LegacyInstallation resolve(Path minecraftDirectory) throws IOException
    {
        Path root = minecraftDirectory.toAbsolutePath().normalize();
        Path jsonPath = root.resolve("versions").resolve("1.7.10").resolve("1.7.10.json");
        Path client = root.resolve("versions").resolve("1.7.10").resolve("1.7.10.jar");

        if (!Files.isRegularFile(jsonPath) || !Files.isRegularFile(client))
        {
            throw new IOException("Install and start Minecraft 1.7.10 once before the benchmark.");
        }

        if (!VANILLA_SHA1.equalsIgnoreCase(sha1(client)))
        {
            throw new IOException("The installed 1.7.10 client JAR does not match the official file.");
        }

        JsonObject version = readObject(jsonPath);

        if (!"1.7.10".equals(text(version, "id")))
        {
            throw new IOException("The installed version metadata is not for Minecraft 1.7.10.");
        }

        Path libraries = root.resolve("libraries");
        List<Path> classPath = new ArrayList<Path>();
        List<Path> natives = new ArrayList<Path>();
        JsonArray entries = version.getAsJsonArray("libraries");

        if (entries == null)
        {
            throw new IOException("The 1.7.10 library list is missing.");
        }

        for (JsonElement item : entries)
        {
            JsonObject library = item.getAsJsonObject();

            if (!allowed(library))
            {
                continue;
            }

            JsonObject downloads = object(library, "downloads");
            JsonObject artifact = downloads == null ? null : object(downloads, "artifact");

            if (artifact != null && text(artifact, "path") != null)
            {
                Path file = safeResolve(libraries, text(artifact, "path"));
                requireFile(file, "library");
                classPath.add(file);
            }

            JsonObject nativeNames = object(library, "natives");

            if (nativeNames != null)
            {
                String classifier = text(nativeNames, osName());

                if (classifier != null)
                {
                    classifier = classifier.replace("${arch}", is64Bit() ? "64" : "32");
                    JsonObject classifiers = downloads == null ? null
                        : object(downloads, "classifiers");
                    JsonObject nativeFile = classifiers == null ? null
                        : object(classifiers, classifier);

                    if (nativeFile == null || text(nativeFile, "path") == null)
                    {
                        throw new IOException("A required native library is missing from the version metadata.");
                    }

                    Path file = safeResolve(libraries, text(nativeFile, "path"));
                    requireFile(file, "native library");
                    natives.add(file);
                }
            }
        }

        Path assets = root.resolve("assets");
        Path assetIndex = assets.resolve("indexes").resolve("1.7.10.json");
        requireFile(assetIndex, "1.7.10 asset index");
        return new LegacyInstallation(root, client, assets, classPath, natives);
    }

    public Path getMinecraftDirectory()
    {
        return minecraftDirectory;
    }

    public Path getClientJar()
    {
        return clientJar;
    }

    public Path getAssetsDirectory()
    {
        return assetsDirectory;
    }

    public List<Path> getClassPath()
    {
        return classPath;
    }

    public List<Path> getNativeArchives()
    {
        return nativeArchives;
    }

    private static boolean allowed(JsonObject library)
    {
        JsonArray rules = library.getAsJsonArray("rules");

        if (rules == null || rules.size() == 0)
        {
            return true;
        }

        boolean allowed = false;

        for (JsonElement item : rules)
        {
            JsonObject rule = item.getAsJsonObject();
            JsonObject os = object(rule, "os");

            if (os == null || text(os, "name") == null
                || osName().equals(text(os, "name")))
            {
                allowed = "allow".equals(text(rule, "action"));
            }
        }

        return allowed;
    }

    private static String osName()
    {
        String value = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (value.contains("win"))
        {
            return "windows";
        }

        if (value.contains("mac"))
        {
            return "osx";
        }

        return "linux";
    }

    private static boolean is64Bit()
    {
        return System.getProperty("os.arch", "").contains("64");
    }

    private static Path safeResolve(Path root, String child) throws IOException
    {
        Path result = root.resolve(child.replace('/', java.io.File.separatorChar)).normalize();

        if (!result.startsWith(root))
        {
            throw new IOException("A library path leaves the installation directory.");
        }

        return result;
    }

    private static JsonObject readObject(Path path) throws IOException
    {
        Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);

        try
        {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
        catch (RuntimeException invalid)
        {
            throw new IOException("The 1.7.10 version metadata is invalid.", invalid);
        }
        finally
        {
            reader.close();
        }
    }

    private static JsonObject object(JsonObject owner, String name)
    {
        JsonElement value = owner.get(name);
        return value == null || !value.isJsonObject() ? null : value.getAsJsonObject();
    }

    private static String text(JsonObject owner, String name)
    {
        JsonElement value = owner.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static void requireFile(Path path, String label) throws IOException
    {
        if (!Files.isRegularFile(path))
        {
            throw new IOException("The installed " + label + " is missing: " + path);
        }
    }

    private static String sha1(Path path) throws IOException
    {
        try
        {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            java.io.InputStream input = Files.newInputStream(path);

            try
            {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) >= 0)
                {
                    digest.update(buffer, 0, read);
                }
            }
            finally
            {
                input.close();
            }

            StringBuilder value = new StringBuilder();

            for (byte item : digest.digest())
            {
                value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }

            return value.toString();
        }
        catch (java.security.NoSuchAlgorithmException unavailable)
        {
            throw new IOException("SHA-1 is not available.", unavailable);
        }
    }
}
