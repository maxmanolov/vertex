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
    public void burstIsCapped()
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

        assertEquals(DynamicSourceTracker.MAX_REMARKS, tracker.update(many).length / 3);
    }
}
