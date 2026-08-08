package vertex.benchmark.quick;

import java.nio.file.Path;

/** Describes a client JAR after a read-only structure check. */
public final class DetectedClient
{
    public enum Type
    {
        VANILLA_1_7_10,
        OPTIFINE,
        VERTEX,
        UNKNOWN
    }

    private final Path path;
    private final Type type;
    private final String reason;

    DetectedClient(Path path, Type type, String reason)
    {
        this.path = path;
        this.type = type;
        this.reason = reason;
    }

    public Path getPath()
    {
        return path;
    }

    public Type getType()
    {
        return type;
    }

    public String getLabel()
    {
        if (type == Type.VANILLA_1_7_10)
        {
            return "Minecraft 1.7.10";
        }
        if (type == Type.OPTIFINE)
        {
            return "OptiFine";
        }
        if (type == Type.VERTEX)
        {
            return "Vertex";
        }
        return "Unknown client";
    }

    public String getReason()
    {
        return reason;
    }

    public boolean isSupported()
    {
        return type != Type.UNKNOWN;
    }
}
