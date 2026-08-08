package vertex.benchmark.plan;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Defines one client configuration in a benchmark suite. */
public final class ProfilePlan
{
    public enum LaunchMode
    {
        @SerializedName("manual")
        MANUAL,
        @SerializedName("command")
        COMMAND
    }

    private String id;
    private String label;
    private LaunchMode launchMode;
    private String processName;
    private List<String> command = new ArrayList<String>();
    private List<String> settingsFiles = new ArrayList<String>();
    private List<String> instructions = new ArrayList<String>();
    private Map<String, String> metadata = new LinkedHashMap<String, String>();

    public ProfilePlan()
    {
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public LaunchMode getLaunchMode()
    {
        return launchMode;
    }

    public void setLaunchMode(LaunchMode launchMode)
    {
        this.launchMode = launchMode;
    }

    public String getProcessName()
    {
        return processName;
    }

    public void setProcessName(String processName)
    {
        this.processName = processName;
    }

    public List<String> getCommand()
    {
        return readOnly(command);
    }

    public void setCommand(List<String> command)
    {
        this.command = copy(command);
    }

    public List<String> getSettingsFiles()
    {
        return readOnly(settingsFiles);
    }

    public void setSettingsFiles(List<String> settingsFiles)
    {
        this.settingsFiles = copy(settingsFiles);
    }

    public List<String> getInstructions()
    {
        return readOnly(instructions);
    }

    public void setInstructions(List<String> instructions)
    {
        this.instructions = copy(instructions);
    }

    public Map<String, String> getMetadata()
    {
        if (metadata == null)
        {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(metadata);
    }

    public void setMetadata(Map<String, String> metadata)
    {
        this.metadata = metadata == null
            ? null : new LinkedHashMap<String, String>(metadata);
    }

    List<String> commandRaw()
    {
        return command;
    }

    List<String> settingsFilesRaw()
    {
        return settingsFiles;
    }

    List<String> instructionsRaw()
    {
        return instructions;
    }

    Map<String, String> metadataRaw()
    {
        return metadata;
    }

    private static List<String> copy(List<String> source)
    {
        return source == null ? null : new ArrayList<String>(source);
    }

    private static List<String> readOnly(List<String> source)
    {
        if (source == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(source);
    }
}
