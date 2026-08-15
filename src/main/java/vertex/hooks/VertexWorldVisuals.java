package vertex.hooks;

/**
 * Client-visual world tweaks from the Other page.
 *
 * Time override: an added WorldClient.getCelestialAngle override routes through
 * {@link #celestialAngle}; only client rendering (sky, celestial bodies, sky-light
 * factor) consults the client world's angle, so Day/Night here never touches gameplay
 * (the integrated server's Worlds keep the vanilla method).
 *
 * Autosave: the integrated server's tick saves whenever tickCounter % interval == 0;
 * the vanilla 900-tick constant is replaced by {@link #autosaveTicks}.
 */
public final class VertexWorldVisuals
{
    /** (F)F adjuster on the added WorldClient.getCelestialAngle override. */
    public static float celestialAngle(float vanilla)
    {
        return celestialAngle(vanilla, VertexConfig.value("timeOverride", "default"));
    }

    /** ()I replacement for the integrated server's autosave interval constant. */
    public static int autosaveTicks()
    {
        return autosaveTicks(VertexConfig.value("autosave", "45"));
    }

    // ---- pure decision logic (unit-tested) -------------------------------------------

    /** Noon pins the angle to 0, midnight to 0.5; anything else is vanilla. */
    static float celestialAngle(float vanilla, String mode)
    {
        String trimmed = mode == null ? "" : mode.trim();
        return trimmed.equals("day") ? 0.0F : trimmed.equals("night") ? 0.5F : vanilla;
    }

    /** Seconds-to-ticks with the vanilla 45s as the never-surprise fallback. */
    static int autosaveTicks(String rawSeconds)
    {
        String trimmed = rawSeconds == null ? "" : rawSeconds.trim();
        int seconds = trimmed.equals("180") ? 180 : trimmed.equals("1800") ? 1800 : 45;
        return seconds * 20;
    }

    private VertexWorldVisuals()
    {
    }
}
