package vertex.hooks;

/**
 * Pure latch state for ToggleSprint. The latch starts armed: enabling the feature is
 * choosing auto-sprint, so sprint engages on the first forward step without any tap,
 * and the first tap pauses rather than starts it. One raw key sample arrives per frame;
 * each rising edge flips the latch.
 */
final class ToggleSprintState
{
    private boolean latched = true;
    private boolean lastDown = false;

    /** Feeds one raw sprint-key sample; returns true when this sample flipped the latch. */
    boolean sample(boolean down)
    {
        boolean flipped = down && !this.lastDown;
        this.lastDown = down;

        if (flipped)
        {
            this.latched = !this.latched;
        }

        return flipped;
    }

    boolean latched()
    {
        return this.latched;
    }

    /** Re-arms for the next session (world join or feature re-enable). */
    void reset()
    {
        this.latched = true;
        this.lastDown = false;
    }
}
