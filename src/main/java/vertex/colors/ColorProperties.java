package vertex.colors;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Parser for the community-documented color.properties format: keys map to hex RGB values
 * (with or without a leading #). Unknown keys are retained so hooks can query any of the
 * documented namespaces (fog.*, sky.*, potion.*, particle.*, lilypad, ...); malformed
 * values are skipped rather than fatal - a pack with one bad line keeps its good colors.
 */
public final class ColorProperties
{
    private final Map<String, Integer> colors = new HashMap<String, Integer>();

    public ColorProperties(Properties props)
    {
        for (String key : props.stringPropertyNames())
        {
            Integer parsed = parseHex(props.getProperty(key));

            if (parsed != null)
            {
                this.colors.put(key, parsed);
            }
        }
    }

    /** Returns the RGB color for the key, or fallback when absent or unparseable. */
    public int get(String key, int fallback)
    {
        Integer value = this.colors.get(key);
        return value != null ? value.intValue() : fallback;
    }

    public boolean has(String key)
    {
        return this.colors.containsKey(key);
    }

    public int size()
    {
        return this.colors.size();
    }

    static Integer parseHex(String value)
    {
        if (value == null)
        {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.startsWith("#"))
        {
            trimmed = trimmed.substring(1);
        }

        if (trimmed.isEmpty() || trimmed.length() > 6)
        {
            return null;
        }

        try
        {
            return Integer.valueOf(Integer.parseInt(trimmed, 16));
        }
        catch (NumberFormatException invalid)
        {
            return null;
        }
    }
}
