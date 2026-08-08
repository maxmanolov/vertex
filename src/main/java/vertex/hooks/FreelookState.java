package vertex.hooks;

/**
 * Pure freelook camera state: while active it owns the orbit angles and absorbs the mouse
 * deltas that would otherwise rotate the player. The arithmetic mirrors vanilla
 * Entity.setAngles exactly (yaw += dx * 0.15 in double precision, pitch -= dy * 0.15,
 * pitch clamped to +/-90) so the orbit feels identical to normal mouse look. Yaw is
 * deliberately unclamped - a full 360-degree orbit is the point of the feature.
 */
final class FreelookState
{
    private boolean active;
    private float yaw;
    private float pitch;

    /** Arms the orbit starting at the player's current orientation. */
    void activate(float startYaw, float startPitch)
    {
        this.active = true;
        this.yaw = startYaw;
        this.pitch = startPitch;
    }

    void deactivate()
    {
        this.active = false;
    }

    boolean active()
    {
        return this.active;
    }

    /** Absorbs one processed mouse delta pair into the orbit; no-op while inactive. */
    void consume(float dx, float dy)
    {
        if (!this.active)
        {
            return;
        }

        this.yaw = (float)((double)this.yaw + (double)dx * 0.15D);
        this.pitch = (float)((double)this.pitch - (double)dy * 0.15D);

        if (this.pitch < -90.0F)
        {
            this.pitch = -90.0F;
        }

        if (this.pitch > 90.0F)
        {
            this.pitch = 90.0F;
        }
    }

    float yaw()
    {
        return this.yaw;
    }

    float pitch()
    {
        return this.pitch;
    }
}
