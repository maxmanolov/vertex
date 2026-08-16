package vertex.hooks;

import java.util.HashMap;

/**
 * Pure client-thread model of the GL state the counting wrappers observe: enable/disable
 * capabilities (GL_TEXTURE_2D tracked per texture unit, everything else global) and
 * texture bindings per (unit, target). A transition is redundant only when the tracked
 * state says the call would be a no-op; unknown state is never redundant, so a skip
 * decision built on this model is conservative by construction.
 *
 * The model must be invalidated whenever GL state can change behind its back:
 * glPopAttrib restores arbitrary server state ({@link #invalidateAll}), and a deleted
 * texture id can be reissued by glGenTextures ({@link #forgetTexture}).
 */
final class GLStateTracker
{
    private static final int CAP_TRACK = 65536;
    private static final int GL_TEXTURE_2D_CAP = 3553;
    private static final byte UNKNOWN = 0;
    private static final byte ENABLED = 1;
    private static final byte DISABLED = 2;

    private final byte[] capState = new byte[CAP_TRACK];
    private final HashMap<Long, Integer> lastBound = new HashMap<Long, Integer>();
    private final HashMap<Integer, Byte> textureCapByUnit = new HashMap<Integer, Byte>();
    private int activeUnit = 0;

    void setActiveUnit(int unit)
    {
        this.activeUnit = unit;
    }

    /** Records an enable; true when the capability was already known enabled. */
    boolean redundantEnable(int cap)
    {
        if (cap == GL_TEXTURE_2D_CAP)
        {
            Byte previous = this.textureCapByUnit.put(Integer.valueOf(this.activeUnit),
                Byte.valueOf(ENABLED));
            return previous != null && previous.byteValue() == ENABLED;
        }

        if (cap >= 0 && cap < CAP_TRACK)
        {
            boolean redundant = this.capState[cap] == ENABLED;
            this.capState[cap] = ENABLED;
            return redundant;
        }

        return false;
    }

    /** Records a disable; true when the capability was already known disabled. */
    boolean redundantDisable(int cap)
    {
        if (cap == GL_TEXTURE_2D_CAP)
        {
            Byte previous = this.textureCapByUnit.put(Integer.valueOf(this.activeUnit),
                Byte.valueOf(DISABLED));
            return previous != null && previous.byteValue() == DISABLED;
        }

        if (cap >= 0 && cap < CAP_TRACK)
        {
            boolean redundant = this.capState[cap] == DISABLED;
            this.capState[cap] = DISABLED;
            return redundant;
        }

        return false;
    }

    /** Records a bind; true when this (unit, target) already held the same texture. */
    boolean redundantBind(int target, int texture)
    {
        Long key = Long.valueOf((long)this.activeUnit << 32 | (target & 0xFFFFFFFFL));
        Integer previous = this.lastBound.put(key, Integer.valueOf(texture));
        return previous != null && previous.intValue() == texture;
    }

    /** glPopAttrib restored unknown state: forget everything and re-learn. */
    void invalidateAll()
    {
        java.util.Arrays.fill(this.capState, UNKNOWN);
        this.lastBound.clear();
        this.textureCapByUnit.clear();
    }

    /** A deleted texture id may be reissued: the next bind of it must forward. */
    void forgetTexture(int texture)
    {
        this.lastBound.values().removeAll(
            java.util.Collections.singleton(Integer.valueOf(texture)));
    }
}
