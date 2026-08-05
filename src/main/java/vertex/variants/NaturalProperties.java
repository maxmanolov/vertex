package vertex.variants;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * First-cut grammar for natural.properties: each line is tile=<spec> where spec is a
 * rotation count (1, 2 or 4), an F for horizontal flip, or both (e.g. "4F"). Rotations
 * beyond {1,2,4} or unknown spec characters skip the line loudly-in-log rather than
 * failing the pack. The engine side: a tile with rotations R and flip allowed has R*2
 * variants; variant selection is DeterministicVariants.pick over that count, decoded back
 * into (rotationSteps, flipped) by the render hook.
 */
public final class NaturalProperties
{
    public static final class Spec
    {
        public final int rotations;
        public final boolean flip;

        Spec(int rotations, boolean flip)
        {
            this.rotations = rotations;
            this.flip = flip;
        }

        public int variantCount()
        {
            return this.rotations * (this.flip ? 2 : 1);
        }

        public int rotationSteps(int variant)
        {
            return variant % this.rotations;
        }

        public boolean flipped(int variant)
        {
            return this.flip && variant >= this.rotations;
        }
    }

    private final Map<String, Spec> specs = new HashMap<String, Spec>();

    public NaturalProperties(Properties props)
    {
        for (String key : props.stringPropertyNames())
        {
            Spec spec = parseSpec(props.getProperty(key));

            if (spec != null)
            {
                this.specs.put(key, spec);
            }
        }
    }

    public Spec spec(String tile)
    {
        return this.specs.get(tile);
    }

    public int size()
    {
        return this.specs.size();
    }

    static Spec parseSpec(String value)
    {
        if (value == null)
        {
            return null;
        }

        String trimmed = value.trim().toUpperCase();
        boolean flip = trimmed.endsWith("F");

        if (flip)
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        int rotations = 1;

        if (!trimmed.isEmpty())
        {
            try
            {
                rotations = Integer.parseInt(trimmed);
            }
            catch (NumberFormatException invalid)
            {
                return null;
            }
        }

        if (rotations != 1 && rotations != 2 && rotations != 4)
        {
            return null;
        }

        if (rotations == 1 && !flip)
        {
            return null;
        }

        return new Spec(rotations, flip);
    }
}
