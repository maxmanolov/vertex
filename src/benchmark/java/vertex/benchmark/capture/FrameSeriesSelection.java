package vertex.benchmark.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores the result of primary-series selection. */
public final class FrameSeriesSelection
{
    private final FrameSeriesKey key;
    private final List<FrameSample> samples;
    private final List<String> warnings;

    FrameSeriesSelection(FrameSeriesKey key, List<FrameSample> samples,
        List<String> warnings)
    {
        this.key = key;
        this.samples = Collections.unmodifiableList(new ArrayList<FrameSample>(samples));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public boolean isPresent()
    {
        return key != null;
    }

    public FrameSeriesKey getKey()
    {
        return key;
    }

    public List<FrameSample> getSamples()
    {
        return samples;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }
}
