package vertex.benchmark.capture;

/** Identifies one process and swap-chain series. */
public final class FrameSeriesKey
{
    private final long processId;
    private final String swapChain;

    public FrameSeriesKey(long processId, String swapChain)
    {
        this.processId = processId;
        this.swapChain = swapChain == null ? "" : swapChain;
    }

    public long getProcessId()
    {
        return processId;
    }

    public String getSwapChain()
    {
        return swapChain;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof FrameSeriesKey))
        {
            return false;
        }

        FrameSeriesKey key = (FrameSeriesKey)other;
        return processId == key.processId && swapChain.equals(key.swapChain);
    }

    @Override
    public int hashCode()
    {
        int result = (int)(processId ^ (processId >>> 32));
        return 31 * result + swapChain.hashCode();
    }

    @Override
    public String toString()
    {
        return processId + "/" + swapChain;
    }
}
