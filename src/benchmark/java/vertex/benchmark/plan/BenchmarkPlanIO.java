package vertex.benchmark.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Reads and writes UTF-8 benchmark plan files. */
public final class BenchmarkPlanIO
{
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private BenchmarkPlanIO()
    {
    }

    public static BenchmarkPlan load(Path path) throws IOException
    {
        if (path == null)
        {
            throw new IllegalArgumentException("path is required");
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            JsonElement json = new JsonParser().parse(reader);
            verifyCommandArrays(json);
            BenchmarkPlan plan = GSON.fromJson(json, BenchmarkPlan.class);
            return BenchmarkPlanValidator.validate(plan);
        }
        catch (JsonParseException error)
        {
            throw new IOException("Cannot parse benchmark plan " + path, error);
        }
    }

    public static BenchmarkPlan loadResolved(Path path, Map<String, String> variables)
        throws IOException
    {
        return PlanEnvironment.resolve(load(path), variables);
    }

    public static BenchmarkPlan resolveEnvironment(BenchmarkPlan plan,
        Map<String, String> variables)
    {
        return PlanEnvironment.resolve(plan, variables);
    }

    public static void write(Path path, BenchmarkPlan plan) throws IOException
    {
        if (path == null)
        {
            throw new IllegalArgumentException("path is required");
        }
        BenchmarkPlanValidator.validate(plan);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE))
        {
            GSON.toJson(plan, writer);
        }
    }

    private static void verifyCommandArrays(JsonElement root)
    {
        if (root == null || !root.isJsonObject())
        {
            throw new PlanValidationException("root must be an object");
        }
        JsonElement profiles = root.getAsJsonObject().get("profiles");
        if (profiles == null || profiles.isJsonNull() || !profiles.isJsonArray())
        {
            return;
        }

        for (int profileIndex = 0; profileIndex < profiles.getAsJsonArray().size();
            profileIndex++)
        {
            JsonElement item = profiles.getAsJsonArray().get(profileIndex);
            if (item == null || !item.isJsonObject())
            {
                continue;
            }
            JsonObject profile = item.getAsJsonObject();
            JsonElement command = profile.get("command");
            if (command == null || command.isJsonNull())
            {
                continue;
            }
            if (!command.isJsonArray())
            {
                throw new PlanValidationException(
                    "profiles[" + profileIndex + "].command must be an argument array");
            }
            for (int argumentIndex = 0; argumentIndex < command.getAsJsonArray().size();
                argumentIndex++)
            {
                JsonElement argument = command.getAsJsonArray().get(argumentIndex);
                if (!(argument instanceof JsonPrimitive)
                    || !argument.getAsJsonPrimitive().isString())
                {
                    throw new PlanValidationException("profiles[" + profileIndex
                        + "].command[" + argumentIndex + "] must be a string");
                }
            }
        }
    }
}
