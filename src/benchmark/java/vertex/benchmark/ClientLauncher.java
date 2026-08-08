package vertex.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import vertex.benchmark.plan.ProfilePlan;
import vertex.benchmark.result.RunSlot;

/** Starts an optional client command as an argument array. */
public final class ClientLauncher
{
    public Process launch(ProfilePlan profile, RunSlot slot, Path runDirectory)
        throws IOException
    {
        if (profile.getLaunchMode() != ProfilePlan.LaunchMode.COMMAND)
        {
            return null;
        }

        List<String> command = new ArrayList<String>();

        for (String argument : profile.getCommand())
        {
            command.add(argument
                .replace("{runDir}", runDirectory.toAbsolutePath().toString())
                .replace("{profileId}", profile.getId())
                .replace("{round}", Integer.toString(slot.getRound()))
                .replace("{position}", Integer.toString(slot.getPosition())));
        }

        Files.createDirectories(runDirectory);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }
}
