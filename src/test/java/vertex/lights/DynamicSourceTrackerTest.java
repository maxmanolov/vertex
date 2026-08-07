package vertex.lights;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DynamicSourceTrackerTest
{
    @Test
    public void movedSourceRemarksBothPositions()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        assertEquals(1, tracker.update(new int[] {10, 64, 10, 14}).length / 3);
        int[] remarks = tracker.update(new int[] {12, 64, 10, 14});
        assertEquals(2, remarks.length / 3);
    }

    @Test
    public void unchangedSourcesProduceNoRemarks()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        tracker.update(new int[] {10, 64, 10, 14});
        assertArrayEquals(new int[0], tracker.update(new int[] {10, 64, 10, 14}));
    }

    @Test
    public void changedLightLevelRemarksThePositionOnce()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        tracker.update(new int[] {10, 64, 10, 10});
        assertArrayEquals(new int[] {10, 64, 10}, tracker.update(new int[] {10, 64, 10, 14}));
    }

    @Test
    public void reenqueueAfterConsumptionStillWorks()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        tracker.update(new int[] {10, 64, 10, 10});
        assertEquals(1, tracker.update(new int[] {10, 64, 10, 14}).length / 3);
        // The position was consumed; a later change at the same position must re-emit it.
        assertEquals(1, tracker.update(new int[] {10, 64, 10, 8}).length / 3);
    }

    @Test
    public void negativeAndBoundaryCoordinatesDeduplicateExactly()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        int[] extreme = {-30000000, 0, 29999999, 10, Integer.MIN_VALUE, 255, Integer.MAX_VALUE, 9};
        tracker.update(extreme);
        int[] levelChange = {-30000000, 0, 29999999, 14, Integer.MIN_VALUE, 255, Integer.MAX_VALUE, 12};
        int[] remarks = tracker.update(levelChange);
        // One remark per changed source, never two, and coordinates survive untouched.
        assertEquals(2, remarks.length / 3);
        assertArrayEquals(new int[] {-30000000, 0, 29999999, Integer.MIN_VALUE, 255, Integer.MAX_VALUE}, remarks);
    }

    @Test
    public void movingLightStressNeverDuplicatesAndNeverLosesWork()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        java.util.Random random = new java.util.Random(20260806L);
        int[] positions = new int[6 * 3];
        int maxPending = 0;
        long emitted = 0L;

        for (int step = 0; step < 500; ++step)
        {
            // Six sources wander (occasionally flickering level), like torch-holders.
            int[] snapshot = new int[6 * 4];

            for (int s = 0; s < 6; ++s)
            {
                if (step > 0 && random.nextInt(4) == 0)
                {
                    positions[s * 3] += random.nextInt(3) - 1;
                    positions[s * 3 + 2] += random.nextInt(3) - 1;
                }
                else if (step == 0)
                {
                    positions[s * 3] = random.nextInt(200) - 100;
                    positions[s * 3 + 1] = 64;
                    positions[s * 3 + 2] = random.nextInt(200) - 100;
                }

                snapshot[s * 4] = positions[s * 3];
                snapshot[s * 4 + 1] = positions[s * 3 + 1];
                snapshot[s * 4 + 2] = positions[s * 3 + 2];
                snapshot[s * 4 + 3] = random.nextInt(8) == 0 ? 10 : 14;
            }

            int[] remarks = tracker.update(snapshot);
            emitted += remarks.length / 3;

            // No batch may contain the same position twice.
            for (int i = 0; i < remarks.length; i += 3)
            {
                for (int j = i + 3; j < remarks.length; j += 3)
                {
                    boolean same = remarks[i] == remarks[j] && remarks[i + 1] == remarks[j + 1]
                        && remarks[i + 2] == remarks[j + 2];
                    assertEquals("duplicate in batch at step " + step, false, same);
                }
            }

            int pending = pendingCount(tracker);
            maxPending = Math.max(maxPending, pending);
        }

        // Steady state: six wandering lights generate bounded backlog, not runaway growth.
        assertEquals("pending backlog must stay bounded (saw " + maxPending + ")", true, maxPending <= 24);
        assertEquals("stress must actually exercise the tracker", true, emitted > 200L);

        // Quiescence: identical snapshots drain the backlog to zero - nothing is lost.
        int[] last = new int[6 * 4];

        for (int s = 0; s < 6; ++s)
        {
            last[s * 4] = positions[s * 3];
            last[s * 4 + 1] = positions[s * 3 + 1];
            last[s * 4 + 2] = positions[s * 3 + 2];
            last[s * 4 + 3] = 14;
        }

        for (int i = 0; i < 32 && pendingCount(tracker) > 0; ++i)
        {
            tracker.update(last);
        }

        assertEquals("backlog must drain to zero at quiescence", false, tracker.hasPending());
    }

    private static int pendingCount(DynamicSourceTracker tracker)
    {
        try
        {
            java.lang.reflect.Field pending = DynamicSourceTracker.class.getDeclaredField("pending");
            pending.setAccessible(true);
            return ((java.util.List<?>)pending.get(tracker)).size();
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void burstIsCappedButNeverDropped()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        int[] many = new int[4 * 20];

        for (int i = 0; i < 20; ++i)
        {
            many[i * 4] = i * 16;
            many[i * 4 + 1] = 64;
            many[i * 4 + 2] = 0;
            many[i * 4 + 3] = 14;
        }

        // The reporter's reproduction: 20 changes, then an identical snapshot. The cap
        // must delay the remaining 12, not delete them.
        assertEquals(DynamicSourceTracker.MAX_REMARKS, tracker.update(many).length / 3);
        assertEquals(DynamicSourceTracker.MAX_REMARKS, tracker.update(many).length / 3);
        assertEquals(4, tracker.update(many).length / 3);
        assertEquals(0, tracker.update(many).length / 3);
    }
}
