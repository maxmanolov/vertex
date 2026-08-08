package vertex.benchmark.quick;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Finds the local files that are required to start Minecraft 1.7.10. */
public final class MinecraftInstallDiscovery
{
    private static final String VERSION = "1.7.10";

    private final Map<String, String> environment;
    private final String osName;
    private final Path userHome;
    private final ClientArtifactClassifier classifier;

    public MinecraftInstallDiscovery()
    {
        this(launcherEnvironment(), System.getProperty("os.name", ""),
            Paths.get(System.getProperty("user.home", ".")),
            new ClientArtifactClassifier());
    }

    MinecraftInstallDiscovery(Map<String, String> environment, String osName,
        Path userHome, ClientArtifactClassifier classifier)
    {
        this.environment = environment;
        this.osName = osName;
        this.userHome = userHome;
        this.classifier = classifier;
    }

    public Installation discover()
    {
        return discover(null);
    }

    /** Uses the requested directory, or searches the normal launcher locations. */
    public Installation discover(Path requestedMinecraftDirectory)
    {
        if (requestedMinecraftDirectory != null)
        {
            Path requested = normalize(requestedMinecraftDirectory);
            Installation installation = inspect(requested);
            if (installation == null)
            {
                throw new IllegalArgumentException(
                    "The Minecraft directory is not ready for 1.7.10: " + requested);
            }
            return installation;
        }

        for (Path candidate : defaultCandidates())
        {
            Installation installation = inspect(candidate);
            if (installation != null)
            {
                return installation;
            }
        }

        throw new IllegalStateException(
            "Minecraft 1.7.10 was not found. Start it once with the Minecraft Launcher.");
    }

    private Installation inspect(Path minecraftDirectory)
    {
        Path root = normalize(minecraftDirectory);
        Path versionDirectory = root.resolve("versions").resolve(VERSION);
        Path clientJar = versionDirectory.resolve(VERSION + ".jar");
        Path versionJson = versionDirectory.resolve(VERSION + ".json");
        Path libraries = root.resolve("libraries");
        Path assets = root.resolve("assets");

        if (!Files.isRegularFile(clientJar) || !Files.isRegularFile(versionJson)
            || !Files.isDirectory(libraries) || !Files.isDirectory(assets))
        {
            return null;
        }

        DetectedClient detected = classifier.classify(clientJar);
        if (detected.getType() != DetectedClient.Type.VANILLA_1_7_10)
        {
            return null;
        }

        String assetIndexId = readAssetIndexId(versionJson);
        if (assetIndexId == null)
        {
            return null;
        }

        Path assetIndex = assets.resolve("indexes").resolve(assetIndexId + ".json");
        if (!Files.isRegularFile(assetIndex))
        {
            return null;
        }

        return new Installation(root, versionDirectory, clientJar, versionJson,
            libraries, assets, assetIndex, assetIndexId);
    }

    private static String readAssetIndexId(Path versionJson)
    {
        Reader reader = null;
        try
        {
            reader = Files.newBufferedReader(versionJson, StandardCharsets.UTF_8);
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject())
            {
                return null;
            }

            JsonObject object = root.getAsJsonObject();
            if (!hasString(object, "id") || !VERSION.equals(object.get("id").getAsString())
                || !object.has("assetIndex") || !object.get("assetIndex").isJsonObject())
            {
                return null;
            }

            JsonObject assetIndex = object.getAsJsonObject("assetIndex");
            if (!hasString(assetIndex, "id"))
            {
                return null;
            }

            String id = assetIndex.get("id").getAsString().trim();
            if (id.isEmpty() || id.contains("/") || id.contains("\\"))
            {
                return null;
            }
            return id;
        }
        catch (Exception error)
        {
            return null;
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (IOException ignored)
                {
                    // The JSON check is complete.
                }
            }
        }
    }

    private static boolean hasString(JsonObject object, String name)
    {
        return object.has(name) && object.get(name).isJsonPrimitive()
            && object.get(name).getAsJsonPrimitive().isString();
    }

    private List<Path> defaultCandidates()
    {
        LinkedHashSet<Path> candidates = new LinkedHashSet<Path>();
        String normalizedOs = osName.toLowerCase(java.util.Locale.ROOT);

        if (normalizedOs.contains("win"))
        {
            String appData = environment.get("APPDATA");
            if (appData != null && !appData.trim().isEmpty())
            {
                candidates.add(normalize(Paths.get(appData).resolve(".minecraft")));
            }
            candidates.add(normalize(userHome.resolve("AppData")
                .resolve("Roaming").resolve(".minecraft")));
        }
        else if (normalizedOs.contains("mac"))
        {
            candidates.add(normalize(userHome.resolve("Library")
                .resolve("Application Support").resolve("minecraft")));
        }

        candidates.add(normalize(userHome.resolve(".minecraft")));
        return new ArrayList<Path>(candidates);
    }

    private static Map<String, String> launcherEnvironment()
    {
        Map<String, String> values = new HashMap<String, String>();
        String appData = System.getenv("APPDATA");
        if (appData != null)
        {
            values.put("APPDATA", appData);
        }
        return values;
    }

    private static Path normalize(Path path)
    {
        return path.toAbsolutePath().normalize();
    }

    /** Contains the paths needed by the local game launcher. */
    public static final class Installation
    {
        private final Path minecraftDirectory;
        private final Path versionDirectory;
        private final Path clientJar;
        private final Path versionJson;
        private final Path librariesDirectory;
        private final Path assetsDirectory;
        private final Path assetIndex;
        private final String assetIndexId;

        private Installation(Path minecraftDirectory, Path versionDirectory,
            Path clientJar, Path versionJson, Path librariesDirectory,
            Path assetsDirectory, Path assetIndex, String assetIndexId)
        {
            this.minecraftDirectory = minecraftDirectory;
            this.versionDirectory = versionDirectory;
            this.clientJar = clientJar;
            this.versionJson = versionJson;
            this.librariesDirectory = librariesDirectory;
            this.assetsDirectory = assetsDirectory;
            this.assetIndex = assetIndex;
            this.assetIndexId = assetIndexId;
        }

        public Path getMinecraftDirectory()
        {
            return minecraftDirectory;
        }

        public Path getVersionDirectory()
        {
            return versionDirectory;
        }

        public Path getClientJar()
        {
            return clientJar;
        }

        public Path getVersionJson()
        {
            return versionJson;
        }

        public Path getLibrariesDirectory()
        {
            return librariesDirectory;
        }

        public Path getAssetsDirectory()
        {
            return assetsDirectory;
        }

        public Path getAssetIndex()
        {
            return assetIndex;
        }

        public String getAssetIndexId()
        {
            return assetIndexId;
        }
    }
}
