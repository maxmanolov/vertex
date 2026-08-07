package vertex.benchmark.capture;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads PresentMon 1.x and 2.x CSV frame captures. */
public final class FrameCaptureParser
{
    private static final int MAX_ROW_WARNINGS = 100;
    private static final String[] PRESENTED_COLUMNS = {
        "MsBetweenPresents", "FrameTime", "MsBetweenAppStart"
    };
    private static final String[] DISPLAYED_COLUMNS = {
        "DisplayedTime", "MsBetweenDisplayChange"
    };
    private static final String[] TIMESTAMP_COLUMNS = {
        "CPUStartTime", "CPUStartQPC", "CPUStartQPCTime", "CPUStartDateTime",
        "TimeInSeconds", "PresentStartTime", "PresentStartQPC",
        "PresentStartQPCTime", "PresentStartDateTime", "DateTime"
    };

    public FrameCapture parse(Path path, FrameTimePreference preference) throws IOException
    {
        if (path == null)
        {
            throw new IllegalArgumentException("Path must not be null.");
        }

        String autoColumn = preference == FrameTimePreference.AUTO
            ? findAutoFrameTime(path) : null;
        Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);

        try
        {
            return parse(reader, preference, autoColumn);
        }
        finally
        {
            reader.close();
        }
    }

    /** Reads UTF-8 input and leaves the stream open. */
    public FrameCapture parse(InputStream input, FrameTimePreference preference)
        throws IOException
    {
        if (input == null)
        {
            throw new IllegalArgumentException("Input must not be null.");
        }

        return parse(new InputStreamReader(input, StandardCharsets.UTF_8), preference);
    }

    /** Reads input and leaves the reader open. */
    public FrameCapture parse(Reader input, FrameTimePreference preference)
        throws IOException
    {
        if (input == null)
        {
            throw new IllegalArgumentException("Input must not be null.");
        }

        if (preference == null)
        {
            throw new IllegalArgumentException("Frame-time preference must not be null.");
        }

        if (preference == FrameTimePreference.AUTO)
        {
            Path temporary = Files.createTempFile("vertex-frame-capture-", ".csv");

            try
            {
                Writer output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8);

                try
                {
                    char[] buffer = new char[8192];
                    int count;

                    while ((count = input.read(buffer)) >= 0)
                    {
                        output.write(buffer, 0, count);
                    }
                }
                finally
                {
                    output.close();
                }

                return parse(temporary, preference);
            }
            finally
            {
                Files.deleteIfExists(temporary);
            }
        }

        return parse(input, preference, null);
    }

    private FrameCapture parse(Reader input, FrameTimePreference preference,
        String autoColumn) throws IOException
    {

        CsvReader csv = new CsvReader(input);
        List<String> headerRecord = nextNonBlank(csv);

        if (headerRecord == null)
        {
            return new FrameCapture(Collections.<FrameSample>emptyList(),
                Collections.singletonList("The CSV file is empty."), 0, 0,
                Collections.<FrameSeriesKey, Integer>emptyMap(), null);
        }

        if (!headerRecord.isEmpty() && !headerRecord.get(0).isEmpty()
            && headerRecord.get(0).charAt(0) == '\uFEFF')
        {
            headerRecord.set(0, headerRecord.get(0).substring(1));
        }

        List<String> warnings = new ArrayList<String>();
        Columns columns = new Columns(headerRecord, warnings);
        ColumnRef frameTime = preference == FrameTimePreference.AUTO
            ? (autoColumn == null ? null : columns.first(autoColumn))
            : selectFrameTime(columns, preference);
        ColumnRef processName = columns.first("Application", "ProcessName", "Process");
        ColumnRef processId = columns.first("ProcessID", "PID");
        ColumnRef swapChain = columns.first("SwapChainAddress", "SwapChain");
        ColumnRef dropped = columns.first("Dropped", "WasDropped", "DroppedFrame");
        List<ColumnRef> timestamps = columns.all(TIMESTAMP_COLUMNS);

        if (frameTime == null && preference != FrameTimePreference.AUTO)
        {
            warnings.add("No " + preference.name().toLowerCase(Locale.ROOT)
                + " frame-time column is present.");
        }

        if (processId == null)
        {
            warnings.add("The ProcessID column is not present. Process ID 0 is used.");
        }

        if (processName == null)
        {
            warnings.add("The process-name column is not present.");
        }

        if (swapChain == null)
        {
            warnings.add("The swap-chain column is not present. An empty key is used.");
        }

        List<FrameSample> samples = new ArrayList<FrameSample>();
        int invalidRows = 0;
        int droppedRows = 0;
        Map<FrameSeriesKey, Integer> droppedRowsBySeries =
            new LinkedHashMap<FrameSeriesKey, Integer>();
        int rowWarnings = 0;
        long rowNumber = 1L;
        List<String> row;

        while ((row = csv.next()) != null)
        {
            ++rowNumber;

            if (isBlank(row))
            {
                continue;
            }

            String invalidReason = null;
            Long pid = processId == null ? Long.valueOf(0L)
                : parseProcessId(value(row, processId));
            FrameSample.DroppedState droppedState = readDroppedState(row, dropped,
                columns.first("DisplayedTime"));

            if (droppedState == FrameSample.DroppedState.DROPPED)
            {
                ++droppedRows;

                if (pid != null)
                {
                    String chain = swapChain == null ? "" : value(row, swapChain).trim();
                    FrameSeriesKey key = new FrameSeriesKey(pid.longValue(), chain);
                    Integer count = droppedRowsBySeries.get(key);
                    droppedRowsBySeries.put(key,
                        Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
            }

            if (pid == null)
            {
                invalidReason = "ProcessID is invalid.";
            }

            Double millis = frameTime == null ? null : parsePositiveFinite(value(row, frameTime));

            if (invalidReason == null && frameTime == null)
            {
                invalidReason = "A frame-time column is not available.";
            }
            else if (invalidReason == null && millis == null)
            {
                if (droppedState == FrameSample.DroppedState.DROPPED)
                {
                    continue;
                }

                invalidReason = "Frame time is not finite and positive.";
            }

            if (invalidReason != null)
            {
                ++invalidRows;

                if (rowWarnings < MAX_ROW_WARNINGS)
                {
                    warnings.add("CSV row " + rowNumber + ": " + invalidReason);
                    ++rowWarnings;
                }

                continue;
            }

            if (dropped != null && droppedState == FrameSample.DroppedState.UNKNOWN)
            {
                String raw = value(row, dropped).trim();

                if (!raw.isEmpty() && !isUnavailable(raw) && rowWarnings < MAX_ROW_WARNINGS)
                {
                    warnings.add("CSV row " + rowNumber + ": Dropped state is invalid.");
                    ++rowWarnings;
                }
            }

            Map<String, String> timestampValues = new LinkedHashMap<String, String>();

            for (ColumnRef timestamp : timestamps)
            {
                String raw = value(row, timestamp).trim();

                if (!raw.isEmpty() && !isUnavailable(raw))
                {
                    timestampValues.put(timestamp.name, raw);
                }
            }

            String name = processName == null ? "" : value(row, processName).trim();
            String chain = swapChain == null ? "" : value(row, swapChain).trim();

            samples.add(new FrameSample(rowNumber, name, pid.longValue(), chain,
                droppedState, millis.doubleValue(), frameTime.name, timestampValues));
        }

        if (invalidRows > rowWarnings && rowWarnings >= MAX_ROW_WARNINGS)
        {
            warnings.add("More invalid-row warnings are not shown.");
        }

        if (frameTime == null && preference == FrameTimePreference.AUTO)
        {
            warnings.add("No usable auto frame-time column is present.");
        }

        return new FrameCapture(samples, warnings, invalidRows, droppedRows,
            droppedRowsBySeries,
            frameTime == null ? null : frameTime.name);
    }

    private static ColumnRef selectFrameTime(Columns columns,
        FrameTimePreference preference)
    {
        if (preference == FrameTimePreference.PRESENTED)
        {
            return columns.first(PRESENTED_COLUMNS);
        }

        if (preference == FrameTimePreference.DISPLAYED)
        {
            return columns.first(DISPLAYED_COLUMNS);
        }

        return null;
    }

    private static String findAutoFrameTime(Path path) throws IOException
    {
        Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);

        try
        {
            CsvReader csv = new CsvReader(reader);
            List<String> header = nextNonBlank(csv);

            if (header == null)
            {
                return null;
            }

            if (!header.isEmpty() && !header.get(0).isEmpty()
                && header.get(0).charAt(0) == '\uFEFF')
            {
                header.set(0, header.get(0).substring(1));
            }

            Columns columns = new Columns(header, new ArrayList<String>());
            String[] names = new String[PRESENTED_COLUMNS.length + DISPLAYED_COLUMNS.length];
            System.arraycopy(PRESENTED_COLUMNS, 0, names, 0, PRESENTED_COLUMNS.length);
            System.arraycopy(DISPLAYED_COLUMNS, 0, names, PRESENTED_COLUMNS.length,
                DISPLAYED_COLUMNS.length);
            boolean[] valid = new boolean[names.length];
            List<String> row;

            while ((row = csv.next()) != null)
            {
                for (int index = 0; index < names.length; ++index)
                {
                    if (valid[index])
                    {
                        continue;
                    }

                    ColumnRef column = columns.first(names[index]);
                    valid[index] = column != null
                        && parsePositiveFinite(value(row, column)) != null;
                }
            }

            for (int index = 0; index < names.length; ++index)
            {
                if (valid[index])
                {
                    return names[index];
                }
            }

            return null;
        }
        finally
        {
            reader.close();
        }
    }

    private static FrameSample.DroppedState readDroppedState(List<String> row,
        ColumnRef dropped, ColumnRef displayedTime)
    {
        if (dropped != null)
        {
            String raw = value(row, dropped).trim().toLowerCase(Locale.ROOT);

            if (raw.equals("1") || raw.equals("true") || raw.equals("yes")
                || raw.equals("dropped"))
            {
                return FrameSample.DroppedState.DROPPED;
            }

            if (raw.equals("0") || raw.equals("false") || raw.equals("no")
                || raw.equals("displayed") || raw.equals("presented"))
            {
                return FrameSample.DroppedState.DISPLAYED;
            }

            return FrameSample.DroppedState.UNKNOWN;
        }

        if (displayedTime != null)
        {
            String raw = value(row, displayedTime).trim();

            if (isUnavailable(raw))
            {
                return FrameSample.DroppedState.DROPPED;
            }

            if (parsePositiveFinite(raw) != null)
            {
                return FrameSample.DroppedState.DISPLAYED;
            }
        }

        return FrameSample.DroppedState.UNKNOWN;
    }

    private static Long parseProcessId(String raw)
    {
        try
        {
            String value = raw.trim();
            long parsed = value.isEmpty() ? -1L : Long.parseLong(value);
            return parsed < 0L ? null : Long.valueOf(parsed);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static Double parsePositiveFinite(String raw)
    {
        try
        {
            String value = raw.trim();

            if (value.isEmpty() || isUnavailable(value))
            {
                return null;
            }

            double parsed = Double.parseDouble(value);
            return parsed > 0.0D && !Double.isNaN(parsed) && !Double.isInfinite(parsed)
                ? Double.valueOf(parsed) : null;
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static boolean isUnavailable(String value)
    {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("na") || normalized.equals("n/a")
            || normalized.equals("null") || normalized.equals("-");
    }

    private static String value(List<String> row, ColumnRef column)
    {
        return column.index < row.size() ? row.get(column.index) : "";
    }

    private static List<String> nextNonBlank(CsvReader csv) throws IOException
    {
        List<String> record;

        while ((record = csv.next()) != null)
        {
            if (!isBlank(record))
            {
                return record;
            }
        }

        return null;
    }

    private static boolean isBlank(List<String> record)
    {
        for (String value : record)
        {
            if (!value.trim().isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    private static final class ColumnRef
    {
        private final int index;
        private final String name;

        private ColumnRef(int index, String name)
        {
            this.index = index;
            this.name = name;
        }
    }

    private static final class Columns
    {
        private final Map<String, ColumnRef> byName = new HashMap<String, ColumnRef>();

        private Columns(List<String> headers, List<String> warnings)
        {
            for (int i = 0; i < headers.size(); ++i)
            {
                String name = headers.get(i).trim();
                String normalized = normalize(name);

                if (normalized.isEmpty())
                {
                    continue;
                }

                if (byName.containsKey(normalized))
                {
                    warnings.add("The CSV header has a duplicate " + name
                        + " column. The first column is used.");
                }
                else
                {
                    byName.put(normalized, new ColumnRef(i, name));
                }
            }
        }

        private ColumnRef first(String... names)
        {
            for (String name : names)
            {
                ColumnRef column = byName.get(normalize(name));

                if (column != null)
                {
                    return column;
                }
            }

            return null;
        }

        private List<ColumnRef> all(String... names)
        {
            List<ColumnRef> found = new ArrayList<ColumnRef>();

            for (String name : names)
            {
                ColumnRef column = byName.get(normalize(name));

                if (column != null && !found.contains(column))
                {
                    found.add(column);
                }
            }

            return found;
        }

        private static String normalize(String value)
        {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static final class CsvReader
    {
        private final PushbackReader input;

        private CsvReader(Reader input)
        {
            this.input = new PushbackReader(input, 1);
        }

        private List<String> next() throws IOException
        {
            List<String> fields = new ArrayList<String>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            boolean sawInput = false;

            while (true)
            {
                int next = input.read();

                if (next < 0)
                {
                    if (quoted)
                    {
                        throw new IOException("The CSV file has an unterminated quoted field.");
                    }

                    if (!sawInput && fields.isEmpty() && field.length() == 0)
                    {
                        return null;
                    }

                    fields.add(field.toString());
                    return fields;
                }

                sawInput = true;
                char current = (char)next;

                if (quoted)
                {
                    if (current == '"')
                    {
                        int afterQuote = input.read();

                        if (afterQuote == '"')
                        {
                            field.append('"');
                        }
                        else
                        {
                            quoted = false;

                            if (afterQuote >= 0)
                            {
                                input.unread(afterQuote);
                            }
                        }
                    }
                    else
                    {
                        field.append(current);
                    }

                    continue;
                }

                if (current == '"' && field.length() == 0)
                {
                    quoted = true;
                }
                else if (current == ',')
                {
                    fields.add(field.toString());
                    field.setLength(0);
                }
                else if (current == '\n')
                {
                    fields.add(field.toString());
                    return fields;
                }
                else if (current == '\r')
                {
                    int afterReturn = input.read();

                    if (afterReturn >= 0 && afterReturn != '\n')
                    {
                        input.unread(afterReturn);
                    }

                    fields.add(field.toString());
                    return fields;
                }
                else
                {
                    field.append(current);
                }
            }
        }
    }
}
