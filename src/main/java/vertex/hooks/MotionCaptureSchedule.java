package vertex.hooks;

/** Frame-count scheduler for the GL-backed motion capture loop. */
final class MotionCaptureSchedule
{
    private final int frameStride;
    private final int captureLimit;
    private int frames;
    private int captured;

    MotionCaptureSchedule(int frameStride, int captureLimit)
    {
        if (frameStride < 1 || captureLimit < 1)
        {
            throw new IllegalArgumentException("Motion capture schedule values must be positive");
        }

        this.frameStride = frameStride;
        this.captureLimit = captureLimit;
    }

    boolean advanceFrame()
    {
        if (++frames < frameStride)
        {
            return false;
        }

        frames = 0;
        return true;
    }

    int capturedCount()
    {
        return captured;
    }

    /** Records a successful capture and returns true when the burst is complete. */
    boolean recordCapture()
    {
        return ++captured >= captureLimit;
    }
}
