package vertex.hooks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MotionCaptureScheduleTest
{
    @Test
    public void capturesTheDeclaredBurstAtTheDeclaredStride()
    {
        MotionCaptureSchedule schedule = new MotionCaptureSchedule(40, 24);
        int captures = 0;

        for (int frame = 1; frame <= 40 * 24; ++frame)
        {
            boolean due = schedule.advanceFrame();
            assertEquals("only stride boundaries capture", frame % 40 == 0, due);

            if (due)
            {
                assertEquals(captures, schedule.capturedCount());
                ++captures;

                if (captures < 24)
                {
                    assertFalse(schedule.recordCapture());
                }
                else
                {
                    assertTrue(schedule.recordCapture());
                }
            }
        }

        assertEquals(24, captures);
        assertEquals(24, schedule.capturedCount());
    }
}
