package vertex.benchmark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses the small command-line surface without shell expansion. */
public final class CliArguments
{
    private final String command;
    private final Map<String, String> options;

    private CliArguments(String command, Map<String, String> options)
    {
        this.command = command;
        this.options = Collections.unmodifiableMap(options);
    }

    public static CliArguments parse(String[] arguments)
    {
        if (arguments.length == 0)
        {
            return new CliArguments("help", new LinkedHashMap<String, String>());
        }

        String command = arguments[0];
        Map<String, String> options = new LinkedHashMap<String, String>();

        for (int index = 1; index < arguments.length; ++index)
        {
            String argument = arguments[index];

            if (!argument.startsWith("--"))
            {
                throw new IllegalArgumentException("Unexpected argument: " + argument);
            }

            if (argument.equals("--dry-run") || argument.equals("--help"))
            {
                options.put(argument.substring(2), "true");
                continue;
            }

            if (index + 1 >= arguments.length)
            {
                throw new IllegalArgumentException(argument + " requires a value.");
            }

            String value = arguments[++index];

            if (value.startsWith("--"))
            {
                throw new IllegalArgumentException(argument + " requires a value.");
            }

            options.put(argument.substring(2), value);
        }

        return new CliArguments(command, options);
    }

    public String getCommand()
    {
        return command;
    }

    public String option(String name)
    {
        return options.get(name);
    }

    public String require(String name)
    {
        String value = option(name);

        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException("--" + name + " is required.");
        }

        return value;
    }

    public boolean flag(String name)
    {
        return "true".equals(options.get(name));
    }
}
