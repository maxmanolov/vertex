package vertex.lights;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DynamicSourceTrackerTest
{
    @Test
    public void movedSourceRemarksOldAndNewAffectedSections()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        drain(tracker, new int[] {8, 72, 8, 14});
        Set<String> remarks = drain(tracker, new int[] {24, 72, 8, 14});
        assertTrue("old fringe section must rebuild", remarks.contains(key(-16, 64, 0)));
        assertTrue("new fringe section must rebuild", remarks.contains(key(32, 64, 0)));
    }

    @Test
    public void unchangedSourcesProduceNoRemarks()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        int[] source = {10, 64, 10, 14};
        drain(tracker, source);
        assertArrayEquals(new int[0], tracker.update(source));
    }

    @Test
    public void changedLightLevelRemarksEachSectionOnce()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        drain(tracker, new int[] {10, 64, 10, 10});
        Set<String> remarks = drain(tracker, new int[] {10, 64, 10, 14});
        assertTrue(remarks.contains(key(0, 64, 0)));
        assertTrue("a level change must rebuild affected sections", remarks.size() > 1);
    }

    @Test
    public void reenqueueAfterConsumptionStillWorks()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        drain(tracker, new int[] {10, 64, 10, 10});
        assertTrue(drain(tracker, new int[] {10, 64, 10, 14}).contains(key(0, 64, 0)));
        assertTrue(drain(tracker, new int[] {10, 64, 10, 8}).contains(key(0, 64, 0)));
    }

    @Test
    public void extremeCoordinatesStayAlignedAndDoNotOverflow()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        int[] initial = {-30000000, 0, 29999999, 10, Integer.MIN_VALUE, 255, Integer.MAX_VALUE, 9};
        drain(tracker, initial);
        int[] changed = {-30000000, 0, 29999999, 14, Integer.MIN_VALUE, 255, Integer.MAX_VALUE, 12};
        Set<String> remarks = drain(tracker, changed);
        assertTrue(remarks.contains(key(sectionOrigin(-30000000), 0, sectionOrigin(29999999))));
        assertTrue(remarks.contains(key(Integer.MIN_VALUE, 240, sectionOrigin(Integer.MAX_VALUE))));

        for (String remark : remarks)
        {
            String[] coordinates = remark.split(",");
            int sectionY = Integer.parseInt(coordinates[1]);
            assertTrue("vertical section must stay inside the world", sectionY >= 0 && sectionY <= 240);

            for (String coordinate : coordinates)
            {
                assertEquals("section origin must be aligned", 0, Integer.parseInt(coordinate) & 15);
            }
        }
    }

    @Test
    public void movingLightStressNeverDuplicatesAndNeverLosesWork()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        java.util.Random random = new java.util.Random(20260806L);
        int[] positions = new int[6 * 3];
        int maxPending = 0;
        long emitted = 0L;
        int[] snapshot = null;

        for (int step = 0; step < 500; ++step)
        {
            snapshot = new int[6 * 4];

            for (int source = 0; source < 6; ++source)
            {
                if (step > 0 && random.nextInt(4) == 0)
                {
                    positions[source * 3] += random.nextInt(3) - 1;
                    positions[source * 3 + 2] += random.nextInt(3) - 1;
                }
                else if (step == 0)
                {
                    positions[source * 3] = random.nextInt(200) - 100;
                    positions[source * 3 + 1] = 64;
                    positions[source * 3 + 2] = random.nextInt(200) - 100;
                }

                snapshot[source * 4] = positions[source * 3];
                snapshot[source * 4 + 1] = positions[source * 3 + 1];
                snapshot[source * 4 + 2] = positions[source * 3 + 2];
                snapshot[source * 4 + 3] = random.nextInt(8) == 0 ? 10 : 14;
            }

            int[] remarks = tracker.update(snapshot);
            assertUniqueBatch(remarks);
            emitted += remarks.length / 3;
            maxPending = Math.max(maxPending, pendingCount(tracker));
        }

        assertTrue("pending backlog must stay bounded (saw " + maxPending + ")", maxPending <= 64);
        assertTrue("stress must exercise section expansion", emitted > 500L);
        drain(tracker, snapshot);
        assertEquals("backlog must drain to zero at quiescence", false, tracker.hasPending());
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

        Set<String> remarks = drain(tracker, many);

        for (int i = 0; i < 20; ++i)
        {
            assertTrue("source section was lost at index " + i, remarks.contains(key(i * 16, 64, 0)));
        }
    }

    @Test
    public void sourceNearBoundaryRemarksTheAdjacentSection()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        Set<String> remarks = drain(tracker, new int[] {15, 72, 8, 14});
        // The source lights x=16 at distance one, so section (1,4,0) must rebuild.
        assertTrue(remarks.contains(key(16, 64, 0)));
    }

    @Test
    public void manhattanRadiusExcludesDistantDiagonalSections()
    {
        DynamicSourceTracker tracker = new DynamicSourceTracker();
        Set<String> remarks = drain(tracker, new int[] {8, 72, 8, 14});
        assertTrue("face-adjacent section is inside the radius", remarks.contains(key(-16, 64, 0)));
        assertFalse("diagonal section is outside the radius", remarks.contains(key(-16, 64, -16)));
    }

    private static Set<String> drain(DynamicSourceTracker tracker, int[] snapshot)
    {
        Set<String> all = new LinkedHashSet<String>();

        for (int pass = 0; pass < 128; ++pass)
        {
            int[] remarks = tracker.update(snapshot);
            assertTrue("remark batch exceeded its cap", remarks.length / 3 <= DynamicSourceTracker.MAX_REMARKS);
            assertUniqueBatch(remarks);

            for (int i = 0; i < remarks.length; i += 3)
            {
                assertTrue("section emitted more than once", all.add(key(remarks[i], remarks[i + 1], remarks[i + 2])));
            }

            if (!tracker.hasPending())
            {
                return all;
            }
        }

        throw new AssertionError("pending remarks did not drain");
    }

    private static void assertUniqueBatch(int[] remarks)
    {
        Set<String> batch = new LinkedHashSet<String>();

        for (int i = 0; i < remarks.length; i += 3)
        {
            assertTrue("duplicate section in one batch", batch.add(key(remarks[i], remarks[i + 1], remarks[i + 2])));
        }
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

    private static int sectionOrigin(int coordinate)
    {
        return coordinate >> 4 << 4;
    }

    private static String key(int x, int y, int z)
    {
        return x + "," + y + "," + z;
    }
}
