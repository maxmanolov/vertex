package vertex.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses the small command-line surface without shell expansion. */
public final class CliArguments
{
    private final String command;
    private final Map<String, String> options;
    private final List<String> positionals;

    private CliArguments(String command, Map<String, String> options,
        List<String> positionals)
    {
        this.command = command;
        this.options = Collections.unmodifiableMap(options);
        this.positionals = Collections.unmodifiableList(positionals);
    }

    public static CliArguments parse(String[] arguments)
    {
        if (arguments.length == 0)
        {
            return new CliArguments("help", new LinkedHashMap<String, String>(),
                new ArrayList<String>());
        }

        String command = arguments[0];
        Map<String, String> options = new LinkedHashMap<String, String>();
        List<String> positionals = new ArrayList<String>();
        boolean optionsEnded = false;

        for (int index = 1; index < arguments.length; ++index)
        {
            String argument = arguments[index];

            if (optionsEnded)
            {
                positionals.add(argument);
                continue;
            }

            if (argument.equals("--"))
            {
                optionsEnded = true;
                continue;
            }

            if (!argument.startsWith("--"))
            {
                positionals.add(argument);
                continue;
            }

            if (argument.equals("--dry-run") || argument.equals("--help")
                || argument.equals("--no-open"))
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

        return new CliArguments(command, options, positionals);
    }

    public String getCommand()
    {
        return command;
    }

    public String option(String name)
    {
        return options.get(name);
    }

    /** Returns positional values in the order supplied by the caller. */
    public List<String> getPositionals()
    {
        return positionals;
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
