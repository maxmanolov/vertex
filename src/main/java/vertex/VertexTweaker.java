package vertex;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.launchwrapper.LogWrapper;

/**
 * Entry point for the Vertex tweak profile: {@code --tweakClass vertex.VertexTweaker}.
 * Registers the class transformer that applies Vertex's bytecode patches to the vanilla
 * 1.7.10 client as its classes load, then hands off to the normal client main.
 */
public class VertexTweaker implements ITweaker
{
    private final List<String> launchArguments = new ArrayList<String>();

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile)
    {
        // LaunchWrapper consumes --version/--gameDir/--assetsDir before tweakers run;
        // they must be re-appended or the client main sees an incomplete command line.
        this.launchArguments.addAll(args);

        if (!args.contains("--version") && profile != null)
        {
            this.launchArguments.add("--version");
            this.launchArguments.add(profile);
        }

        if (!args.contains("--gameDir") && gameDir != null)
        {
            this.launchArguments.add("--gameDir");
            this.launchArguments.add(gameDir.getAbsolutePath());
        }

        if (!args.contains("--assetsDir") && assetsDir != null)
        {
            this.launchArguments.add("--assetsDir");
            this.launchArguments.add(assetsDir.getAbsolutePath());
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader)
    {
        LogWrapper.info("[Vertex] Registering class transformer");
        classLoader.addTransformerExclusion("vertex.");
        classLoader.registerTransformer("vertex.transform.VertexTransformer");
    }

    @Override
    public String getLaunchTarget()
    {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments()
    {
        return this.launchArguments.toArray(new String[0]);
    }
}
