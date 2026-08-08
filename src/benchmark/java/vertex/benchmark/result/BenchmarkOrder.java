package vertex.benchmark.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Creates a seeded, position-balanced run order. */
public final class BenchmarkOrder {
    private BenchmarkOrder() {
    }

    /**
     * Creates an order with one run for each profile in each round.
     * Round and position values start at 1.
     */
    public static List<RunSlot> create(List<String> profileIds, int rounds, long seed) {
        validate(profileIds, rounds);

        int profileCount = profileIds.size();
        Random random = new Random(seed);
        List<String> mappedProfiles = new ArrayList<String>(profileIds);
        shuffle(mappedProfiles, random);

        int blockSize = fullBlockSize(profileCount);
        int firstRow = random.nextInt(blockSize);
        List<RunSlot> order = new ArrayList<RunSlot>(profileCount * rounds);

        for (int roundIndex = 0; roundIndex < rounds; roundIndex++) {
            int designRow = (firstRow + roundIndex) % blockSize;
            for (int positionIndex = 0; positionIndex < profileCount; positionIndex++) {
                int profileIndex = profileAt(profileCount, designRow, positionIndex);
                order.add(new RunSlot(
                    mappedProfiles.get(profileIndex),
                    roundIndex + 1,
                    positionIndex + 1
                ));
            }
        }

        return Collections.unmodifiableList(order);
    }

    /** Returns the round count in one complete balanced block. */
    public static int fullBlockSize(int profileCount) {
        if (profileCount < 1) {
            throw new IllegalArgumentException("Profile count must be at least 1");
        }
        if (profileCount == 1) {
            return 1;
        }
        return profileCount % 2 == 0 ? profileCount : profileCount * 2;
    }

    private static int profileAt(int count, int designRow, int position) {
        if (count == 1) {
            return 0;
        }

        boolean reversed = count % 2 != 0 && designRow >= count;
        int row = designRow % count;
        int sourcePosition = reversed ? count - 1 - position : position;
        int base;
        if (sourcePosition % 2 == 0) {
            base = sourcePosition / 2;
        } else {
            base = count - 1 - sourcePosition / 2;
        }
        return (base + row) % count;
    }

    private static void shuffle(List<String> values, Random random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            String value = values.get(index);
            values.set(index, values.get(swapIndex));
            values.set(swapIndex, value);
        }
    }

    private static void validate(List<String> profileIds, int rounds) {
        if (profileIds == null || profileIds.isEmpty()) {
            throw new IllegalArgumentException("At least one profile is required");
        }
        if (rounds < 1) {
            throw new IllegalArgumentException("Rounds must be at least 1");
        }

        Set<String> uniqueIds = new HashSet<String>();
        for (String profileId : profileIds) {
            if (profileId == null || profileId.trim().isEmpty()) {
                throw new IllegalArgumentException("Profile ID is required");
            }
            if (!uniqueIds.add(profileId)) {
                throw new IllegalArgumentException("Duplicate profile ID: " + profileId);
            }
        }
    }
}
