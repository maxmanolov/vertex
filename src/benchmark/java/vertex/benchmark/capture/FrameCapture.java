package vertex.benchmark.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stores parsed samples, series, and parse diagnostics. */
public final class FrameCapture
{
    private final List<FrameSample> samples;
    private final Map<FrameSeriesKey, List<FrameSample>> series;
    private final List<String> warnings;
    private final int invalidRowCount;
    private final int droppedRowCount;
    private final Map<FrameSeriesKey, Integer> droppedRowsBySeries;
    private final String frameTimeSource;

    FrameCapture(List<FrameSample> samples, List<String> warnings,
        int invalidRowCount, int droppedRowCount,
        Map<FrameSeriesKey, Integer> droppedRowsBySeries, String frameTimeSource)
    {
        this.samples = Collections.unmodifiableList(new ArrayList<FrameSample>(samples));
        LinkedHashMap<FrameSeriesKey, List<FrameSample>> grouped =
            new LinkedHashMap<FrameSeriesKey, List<FrameSample>>();

        for (FrameSample sample : samples)
        {
            FrameSeriesKey key = sample.getSeriesKey();
            List<FrameSample> values = grouped.get(key);

            if (values == null)
            {
                values = new ArrayList<FrameSample>();
                grouped.put(key, values);
            }

            values.add(sample);
        }

        LinkedHashMap<FrameSeriesKey, List<FrameSample>> readOnly =
            new LinkedHashMap<FrameSeriesKey, List<FrameSample>>();

        for (Map.Entry<FrameSeriesKey, List<FrameSample>> entry : grouped.entrySet())
        {
            readOnly.put(entry.getKey(), Collections.unmodifiableList(
                new ArrayList<FrameSample>(entry.getValue())));
        }

        this.series = Collections.unmodifiableMap(readOnly);
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        this.invalidRowCount = invalidRowCount;
        this.droppedRowCount = droppedRowCount;
        this.droppedRowsBySeries = Collections.unmodifiableMap(
            new LinkedHashMap<FrameSeriesKey, Integer>(droppedRowsBySeries));
        this.frameTimeSource = frameTimeSource;
    }

    public List<FrameSample> getSamples()
    {
        return samples;
    }

    public Map<FrameSeriesKey, List<FrameSample>> getSeries()
    {
        return series;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public int getInvalidRowCount()
    {
        return invalidRowCount;
    }

    public int getDroppedRowCount()
    {
        return droppedRowCount;
    }

    public int getDroppedRowCount(FrameSeriesKey key)
    {
        Integer count = droppedRowsBySeries.get(key);
        return count == null ? 0 : count.intValue();
    }

    public String getFrameTimeSource()
    {
        return frameTimeSource;
    }

    /** Selects the series that has the most valid frames. */
    public FrameSeriesSelection selectLargestSeries()
    {
        return selectLargestSeries(series);
    }

    /** Selects the largest swap chain for one explicit process. */
    public FrameSeriesSelection selectLargestSeriesForProcess(long processId)
    {
        Map<FrameSeriesKey, List<FrameSample>> matches =
            new LinkedHashMap<FrameSeriesKey, List<FrameSample>>();

        for (Map.Entry<FrameSeriesKey, List<FrameSample>> entry : series.entrySet())
        {
            if (entry.getKey().getProcessId() == processId)
            {
                matches.put(entry.getKey(), entry.getValue());
            }
        }

        if (matches.isEmpty())
        {
            return new FrameSeriesSelection(null, Collections.<FrameSample>emptyList(),
                Collections.singletonList("Process ID " + processId
                    + " is not in the capture."));
        }

        return selectLargestSeries(matches);
    }

    /** Selects one exact process and swap-chain series. */
    public FrameSeriesSelection selectSeries(FrameSeriesKey key)
    {
        if (key == null)
        {
            throw new IllegalArgumentException("Series key must not be null.");
        }

        List<FrameSample> selected = series.get(key);

        if (selected == null)
        {
            return new FrameSeriesSelection(null, Collections.<FrameSample>emptyList(),
                Collections.singletonList("The selected frame series is not in the capture."));
        }

        return new FrameSeriesSelection(key, selected, Collections.<String>emptyList());
    }

    private static FrameSeriesSelection selectLargestSeries(
        Map<FrameSeriesKey, List<FrameSample>> candidates)
    {
        if (candidates.isEmpty())
        {
            return new FrameSeriesSelection(null, Collections.<FrameSample>emptyList(),
                Collections.singletonList("No frame series is available."));
        }

        FrameSeriesKey selected = null;
        int largest = -1;
        int largestCount = 0;

        for (Map.Entry<FrameSeriesKey, List<FrameSample>> entry : candidates.entrySet())
        {
            int count = entry.getValue().size();

            if (count > largest)
            {
                selected = entry.getKey();
                largest = count;
                largestCount = 1;
            }
            else if (count == largest)
            {
                ++largestCount;
            }
        }

        List<String> selectionWarnings = new ArrayList<String>();

        if (candidates.size() > 1)
        {
            selectionWarnings.add("The selection has " + candidates.size()
                + " frame series. The largest series was selected.");
        }

        if (largestCount > 1)
        {
            selectionWarnings.add(largestCount + " frame series have " + largest
                + " valid frames. The first series was selected.");
        }

        return new FrameSeriesSelection(selected, candidates.get(selected), selectionWarnings);
    }
}
