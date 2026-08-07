package vertex.benchmark.capture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stores one valid PresentMon frame row. */
public final class FrameSample
{
    public enum DroppedState
    {
        DROPPED,
        DISPLAYED,
        UNKNOWN
    }

    private final long rowNumber;
    private final String processName;
    private final long processId;
    private final String swapChain;
    private final DroppedState droppedState;
    private final double frameTimeMillis;
    private final String frameTimeSource;
    private final Map<String, String> timestamps;

    FrameSample(long rowNumber, String processName, long processId, String swapChain,
        DroppedState droppedState, double frameTimeMillis, String frameTimeSource,
        Map<String, String> timestamps)
    {
        this.rowNumber = rowNumber;
        this.processName = processName;
        this.processId = processId;
        this.swapChain = swapChain;
        this.droppedState = droppedState;
        this.frameTimeMillis = frameTimeMillis;
        this.frameTimeSource = frameTimeSource;
        this.timestamps = Collections.unmodifiableMap(
            new LinkedHashMap<String, String>(timestamps));
    }

    public long getRowNumber()
    {
        return rowNumber;
    }

    public String getProcessName()
    {
        return processName;
    }

    public long getProcessId()
    {
        return processId;
    }

    public String getSwapChain()
    {
        return swapChain;
    }

    public FrameSeriesKey getSeriesKey()
    {
        return new FrameSeriesKey(processId, swapChain);
    }

    public DroppedState getDroppedState()
    {
        return droppedState;
    }

    public boolean isDropped()
    {
        return droppedState == DroppedState.DROPPED;
    }

    public double getFrameTimeMillis()
    {
        return frameTimeMillis;
    }

    public String getFrameTimeSource()
    {
        return frameTimeSource;
    }

    /** Returns each timestamp value by its CSV header. */
    public Map<String, String> getTimestamps()
    {
        return timestamps;
    }
}
