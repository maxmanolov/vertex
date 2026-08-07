package vertex.benchmark.game;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

/** Loads the same small scenario controller in each standard 1.7.10 client. */
public final class BenchmarkTweaker implements ITweaker
{
    private final List<String> launchArguments = new ArrayList<String>();

    @Override
    public void acceptOptions(List<String> arguments, File gameDirectory,
        File assetsDirectory, String profile)
    {
        writeProcessId();

        if (!Boolean.getBoolean("vertex.benchmark.delegateArguments"))
        {
            launchArguments.addAll(arguments);

            if (!arguments.contains("--version") && profile != null)
            {
                launchArguments.add("--version");
                launchArguments.add(profile);
            }

            if (!arguments.contains("--gameDir") && gameDirectory != null)
            {
                launchArguments.add("--gameDir");
                launchArguments.add(gameDirectory.getAbsolutePath());
            }

            if (!arguments.contains("--assetsDir") && assetsDirectory != null)
            {
                launchArguments.add("--assetsDir");
                launchArguments.add(assetsDirectory.getAbsolutePath());
            }
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader)
    {
        classLoader.registerTransformer(BenchmarkTransformer.class.getName());
    }

    @Override
    public String getLaunchTarget()
    {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments()
    {
        return launchArguments.toArray(new String[launchArguments.size()]);
    }

    private static void writeProcessId()
    {
        String controlPath = System.getProperty("vertex.benchmark.controlDir");

        if (controlPath == null)
        {
            return;
        }

        FileOutputStream output = null;

        try
        {
            File control = new File(controlPath);
            control.mkdirs();
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            int separator = runtimeName.indexOf('@');
            String processId = separator < 0 ? runtimeName : runtimeName.substring(0, separator);
            output = new FileOutputStream(new File(control, "pid.txt"));
            output.write(processId.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception failure)
        {
            throw new IllegalStateException("Cannot write the benchmark process ID.", failure);
        }
        finally
        {
            if (output != null)
            {
                try
                {
                    output.close();
                }
                catch (Exception ignored)
                {
                }
            }
        }
    }
}
