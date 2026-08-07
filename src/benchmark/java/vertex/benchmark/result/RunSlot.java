package vertex.benchmark.result;

/** Identifies one profile run in a benchmark order. */
public final class RunSlot {
    private final String profileId;
    private final int round;
    private final int position;

    public RunSlot(String profileId, int round, int position) {
        if (profileId == null || profileId.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile ID is required");
        }
        if (round < 1) {
            throw new IllegalArgumentException("Round must be at least 1");
        }
        if (position < 1) {
            throw new IllegalArgumentException("Position must be at least 1");
        }
        this.profileId = profileId;
        this.round = round;
        this.position = position;
    }

    public String getProfileId() {
        return profileId;
    }

    public int getRound() {
        return round;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RunSlot)) {
            return false;
        }
        RunSlot slot = (RunSlot) other;
        return round == slot.round
            && position == slot.position
            && profileId.equals(slot.profileId);
    }

    @Override
    public int hashCode() {
        int result = profileId.hashCode();
        result = 31 * result + round;
        result = 31 * result + position;
        return result;
    }

    @Override
    public String toString() {
        return "RunSlot{" + profileId + ", round=" + round + ", position=" + position + '}';
    }
}
