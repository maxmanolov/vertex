package vertex.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Installs the Vertex tweak profile into a Minecraft launcher directory:
 *
 *   java -jar vertex-x.y.z.jar install [--mcdir /path/to/.minecraft]
 *
 * Creates versions/1.7.10-Vertex/ from the vanilla 1.7.10 profile with LaunchWrapper as
 * the main class and --tweakClass vertex.VertexTweaker appended, and copies this jar into
 * libraries/vertex/vertex/<version>/. Vanilla 1.7.10 must have been run at least once so
 * its jar and json exist.
 */
public final class VertexInstaller
{
    private static final String VANILLA_ID = "1.7.10";
    private static final String PROFILE_ID = "1.7.10-Vertex";

    public static void main(String[] args) throws Exception
    {
        String version = version();

        if (args.length == 0 || !args[0].equals("install"))
        {
            System.out.println("Vertex " + version + " - performance tweaks for Minecraft 1.7.10");
            System.out.println();
            System.out.println("Usage: java -jar vertex-" + version + ".jar install [--mcdir /path/to/.minecraft]");
            return;
        }

        File mcDir = minecraftDir(args);
        File vanillaJson = new File(mcDir, "versions/" + VANILLA_ID + "/" + VANILLA_ID + ".json");
        File vanillaJar = new File(mcDir, "versions/" + VANILLA_ID + "/" + VANILLA_ID + ".jar");

        if (!vanillaJson.isFile() || !vanillaJar.isFile())
        {
            System.err.println("Vanilla " + VANILLA_ID + " not found under " + mcDir);
            System.err.println("Run Minecraft " + VANILLA_ID + " once from the official launcher, then install again.");
            System.exit(1);
        }

        File self = new File(VertexInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        if (!self.isFile())
        {
            System.err.println("Cannot locate the Vertex jar (running from " + self + "); run the installer from the built jar.");
            System.exit(1);
        }

        // 1. Library: libraries/vertex/vertex/<version>/vertex-<version>.jar
        File libraryJar = new File(mcDir, "libraries/vertex/vertex/" + version + "/vertex-" + version + ".jar");
        copy(self, libraryJar);

        // 2. Version profile: clone the vanilla json, retarget it at LaunchWrapper.
        JsonObject profile = new JsonParser().parse(new InputStreamReader(new FileInputStream(vanillaJson), StandardCharsets.UTF_8)).getAsJsonObject();
        profile.addProperty("id", PROFILE_ID);
        profile.addProperty("mainClass", "net.minecraft.launchwrapper.Launch");
        String arguments = profile.get("minecraftArguments").getAsString();

        if (!arguments.contains("--tweakClass vertex.VertexTweaker"))
        {
            profile.addProperty("minecraftArguments", arguments + " --tweakClass vertex.VertexTweaker");
        }

        JsonArray libraries = profile.getAsJsonArray("libraries");
        JsonArray patched = new JsonArray();
        patched.add(library("vertex:vertex:" + version));
        patched.add(library("net.minecraft:launchwrapper:1.12"));
        patched.addAll(libraries);
        profile.add("libraries", patched);

        File profileDir = new File(mcDir, "versions/" + PROFILE_ID);
        profileDir.mkdirs();
        File profileJson = new File(profileDir, PROFILE_ID + ".json");
        Writer writer = new OutputStreamWriter(new FileOutputStream(profileJson), StandardCharsets.UTF_8);
        new GsonBuilder().setPrettyPrinting().create().toJson(profile, writer);
        writer.close();

        // 3. The old launcher format expects the client jar beside the json.
        copy(vanillaJar, new File(profileDir, PROFILE_ID + ".jar"));

        System.out.println("Installed profile '" + PROFILE_ID + "' into " + mcDir);
        System.out.println("Select it in the launcher to play with Vertex.");
    }

    private static JsonObject library(String name)
    {
        JsonObject lib = new JsonObject();
        lib.addProperty("name", name);
        return lib;
    }

    private static File minecraftDir(String[] args)
    {
        for (int i = 0; i < args.length - 1; ++i)
        {
            if (args[i].equals("--mcdir"))
            {
                return new File(args[i + 1]);
            }
        }

        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String home = System.getProperty("user.home");

        if (os.contains("win"))
        {
            return new File(System.getenv("APPDATA"), ".minecraft");
        }
        else if (os.contains("mac"))
        {
            return new File(home, "Library/Application Support/minecraft");
        }
        else
        {
            return new File(home, ".minecraft");
        }
    }

    private static String version()
    {
        String version = VertexInstaller.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    static void copy(File from, File to) throws Exception
    {
        // Running the installer from its installed library path makes from and to the
        // same file; FileOutputStream would truncate the source to zero bytes before the
        // first read (kyrofx #29). Same-file copies are already complete.
        if (from.getCanonicalFile().equals(to.getCanonicalFile()))
        {
            return;
        }

        to.getParentFile().mkdirs();
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        byte[] buffer = new byte[65536];
        int read;

        while ((read = in.read(buffer)) > 0)
        {
            out.write(buffer, 0, read);
        }

        in.close();
        out.close();
    }

    private VertexInstaller()
    {
    }
}
