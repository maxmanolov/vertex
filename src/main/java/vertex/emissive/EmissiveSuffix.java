package vertex.emissive;

import java.util.Properties;

/**
 * Emissive texture convention: a sprite named <base><suffix> renders as a fullbright
 * overlay of <base>. The documented default suffix is "_e", overridable by
 * optifine-convention emissive.properties (key suffix.emissive). This tiny core owns the
 * naming decisions so the render hook contains no string logic.
 */
public final class EmissiveSuffix
{
    public static final String DEFAULT = "_e";

    private final String suffix;

    public EmissiveSuffix(Properties props)
    {
        String configured = props != null ? props.getProperty("suffix.emissive") : null;
        this.suffix = configured != null && !configured.trim().isEmpty() ? configured.trim() : DEFAULT;
    }

    public boolean isEmissive(String spriteName)
    {
        return spriteName.endsWith(this.suffix) && spriteName.length() > this.suffix.length();
    }

    public String baseOf(String emissiveName)
    {
        return emissiveName.substring(0, emissiveName.length() - this.suffix.length());
    }

    public String emissiveOf(String baseName)
    {
        return baseName + this.suffix;
    }
}
