package vertex.benchmark.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class BenchmarkOrderTest {
    @Test
    public void orderIsDeterministicForSeed() {
        List<String> profiles = Arrays.asList("vanilla", "optifine", "lunar", "vertex");

        List<RunSlot> first = BenchmarkOrder.create(profiles, 8, 4729L);
        List<RunSlot> second = BenchmarkOrder.create(profiles, 8, 4729L);

        assertEquals(first, second);
        assertFalse(first.equals(BenchmarkOrder.create(profiles, 8, 4730L)));
    }

    @Test
    public void eachRoundContainsEveryProfileOnce() {
        List<String> profiles = Arrays.asList("a", "b", "c", "d", "e");
        int rounds = BenchmarkOrder.fullBlockSize(profiles.size());
        List<RunSlot> order = BenchmarkOrder.create(profiles, rounds, 42L);

        for (int round = 1; round <= rounds; round++) {
            Set<String> seen = new HashSet<String>();
            for (RunSlot slot : order) {
                if (slot.getRound() == round) {
                    assertTrue(seen.add(slot.getProfileId()));
                    assertTrue(slot.getPosition() >= 1);
                    assertTrue(slot.getPosition() <= profiles.size());
                }
            }
            assertEquals(new HashSet<String>(profiles), seen);
        }
    }

    @Test
    public void positionsAndWithinRoundTransitionsAreBalancedForEvenAndOddCounts() {
        assertBalanced(Arrays.asList("a", "b", "c", "d"), 1);
        assertBalanced(Arrays.asList("a", "b", "c", "d", "e"), 2);
    }

    @Test
    public void oneProfileUsesOneRoundBlock() {
        assertEquals(1, BenchmarkOrder.fullBlockSize(1));
        assertEquals(
            Arrays.asList(new RunSlot("vanilla", 1, 1)),
            BenchmarkOrder.create(Arrays.asList("vanilla"), 1, 1L)
        );
    }

    private static void assertBalanced(List<String> profiles, int expectedCount) {
        int rounds = BenchmarkOrder.fullBlockSize(profiles.size());
        List<RunSlot> order = BenchmarkOrder.create(profiles, rounds, 8675309L);
        Map<String, Integer> positionCounts = new HashMap<String, Integer>();
        Map<String, Integer> transitionCounts = new HashMap<String, Integer>();
        Map<Integer, List<RunSlot>> byRound = new HashMap<Integer, List<RunSlot>>();

        for (RunSlot slot : order) {
            increment(positionCounts, slot.getProfileId() + ":" + slot.getPosition());
            List<RunSlot> slots = byRound.get(slot.getRound());
            if (slots == null) {
                slots = new ArrayList<RunSlot>();
                byRound.put(slot.getRound(), slots);
            }
            slots.add(slot);
        }

        for (List<RunSlot> slots : byRound.values()) {
            for (int index = 1; index < slots.size(); index++) {
                increment(
                    transitionCounts,
                    slots.get(index - 1).getProfileId() + ">" + slots.get(index).getProfileId()
                );
            }
        }

        for (String profile : profiles) {
            for (int position = 1; position <= profiles.size(); position++) {
                assertEquals(
                    expectedCount,
                    positionCounts.get(profile + ":" + position).intValue()
                );
            }
            for (String next : profiles) {
                if (!profile.equals(next)) {
                    assertEquals(
                        expectedCount,
                        transitionCounts.get(profile + ">" + next).intValue()
                    );
                }
            }
        }
    }

    private static void increment(Map<String, Integer> counts, String key) {
        Integer count = counts.get(key);
        counts.put(key, count == null ? 1 : count + 1);
    }
}
