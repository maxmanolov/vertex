package vertex.benchmark.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vertex.benchmark.capture.FrameMetrics;

/** Stores the inputs and results for one benchmark run. */
public final class RunRecord
{
    public static final int SCHEMA_VERSION = 1;

    public enum Status
    {
        VALID("valid"),
        INVALID("invalid"),
        FAILED("failed");

        private final String value;

        Status(String value)
        {
            this.value = value;
        }

        public String getValue()
        {
            return value;
        }
    }

    private final String runId;
    private final int round;
    private final int position;
    private final String profileId;
    private final String profileLabel;
    private final Map<String, String> profileMetadata;
    private final Status status;
    private final String failure;
    private final String startedAtUtc;
    private final String finishedAtUtc;
    private final Long warmupMillis;
    private final Long captureMillis;
    private final Long cooldownMillis;
    private final Long elapsedMillis;
    private final String collectorType;
    private final String requestedMetric;
    private final String selectedColumn;
    private final Long processId;
    private final String swapChain;
    private final String rawCsvSha256;
    private final int invalidRowCount;
    private final int droppedFrameCount;
    private final boolean droppedFrameCountAvailable;
    private final Map<String, String> settingsHashesBefore;
    private final Map<String, String> settingsHashesAfter;
    private final Map<String, String> hostFields;
    private final FrameMetrics metrics;
    private final List<String> warnings;

    private RunRecord(Builder builder)
    {
        requireText(builder.runId, "Run ID");
        requireText(builder.profileId, "Profile ID");

        if (builder.round < 1)
        {
            throw new IllegalArgumentException("Round must be at least 1.");
        }
        if (builder.position < 1)
        {
            throw new IllegalArgumentException("Position must be at least 1.");
        }
        if (builder.status == null)
        {
            throw new IllegalArgumentException("Status is required.");
        }
        if (builder.status == Status.VALID && builder.metrics == null)
        {
            throw new IllegalArgumentException("Metrics are required for a valid run.");
        }
        requireNotNegative(builder.warmupMillis, "Warmup time");
        requireNotNegative(builder.captureMillis, "Capture time");
        requireNotNegative(builder.cooldownMillis, "Cooldown time");
        requireNotNegative(builder.elapsedMillis, "Elapsed time");
        if (builder.processId != null && builder.processId.longValue() < 0L)
        {
            throw new IllegalArgumentException("Process ID must not be negative.");
        }
        if (builder.invalidRowCount < 0)
        {
            throw new IllegalArgumentException("Invalid row count must not be negative.");
        }
        if (builder.droppedFrameCount < 0)
        {
            throw new IllegalArgumentException("Dropped frame count must not be negative.");
        }

        runId = builder.runId;
        round = builder.round;
        position = builder.position;
        profileId = builder.profileId;
        profileLabel = builder.profileLabel == null ? builder.profileId : builder.profileLabel;
        profileMetadata = copyMap(builder.profileMetadata);
        status = builder.status;
        failure = builder.failure;
        startedAtUtc = builder.startedAtUtc;
        finishedAtUtc = builder.finishedAtUtc;
        warmupMillis = builder.warmupMillis;
        captureMillis = builder.captureMillis;
        cooldownMillis = builder.cooldownMillis;
        elapsedMillis = builder.elapsedMillis;
        collectorType = builder.collectorType;
        requestedMetric = builder.requestedMetric;
        selectedColumn = builder.selectedColumn;
        processId = builder.processId;
        swapChain = builder.swapChain;
        rawCsvSha256 = builder.rawCsvSha256;
        invalidRowCount = builder.invalidRowCount;
        droppedFrameCount = builder.droppedFrameCount;
        droppedFrameCountAvailable = builder.droppedFrameCountAvailable;
        settingsHashesBefore = copyMap(builder.settingsHashesBefore);
        settingsHashesAfter = copyMap(builder.settingsHashesAfter);
        hostFields = copyMap(builder.hostFields);
        metrics = builder.metrics;
        warnings = copyList(builder.warnings);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(String runId, int round, int position, String profileId)
    {
        return new Builder(runId, round, position, profileId);
    }

    public int getSchemaVersion()
    {
        return SCHEMA_VERSION;
    }

    public String getRunId()
    {
        return runId;
    }

    public int getRound()
    {
        return round;
    }

    public int getPosition()
    {
        return position;
    }

    public String getProfileId()
    {
        return profileId;
    }

    public String getProfileLabel()
    {
        return profileLabel;
    }

    public Map<String, String> getProfileMetadata()
    {
        return profileMetadata;
    }

    public Status getStatus()
    {
        return status;
    }

    public boolean isValid()
    {
        return status == Status.VALID;
    }

    public String getFailure()
    {
        return failure;
    }

    public String getStartedAtUtc()
    {
        return startedAtUtc;
    }

    public String getFinishedAtUtc()
    {
        return finishedAtUtc;
    }

    public Long getWarmupMillis()
    {
        return warmupMillis;
    }

    public Long getCaptureMillis()
    {
        return captureMillis;
    }

    public Long getCooldownMillis()
    {
        return cooldownMillis;
    }

    public Long getElapsedMillis()
    {
        return elapsedMillis;
    }

    public String getCollectorType()
    {
        return collectorType;
    }

    public String getRequestedMetric()
    {
        return requestedMetric;
    }

    public String getSelectedColumn()
    {
        return selectedColumn;
    }

    public Long getProcessId()
    {
        return processId;
    }

    public String getSwapChain()
    {
        return swapChain;
    }

    public String getRawCsvSha256()
    {
        return rawCsvSha256;
    }

    public int getInvalidRowCount()
    {
        return invalidRowCount;
    }

    public int getDroppedFrameCount()
    {
        return droppedFrameCount;
    }

    public boolean isDroppedFrameCountAvailable()
    {
        return droppedFrameCountAvailable;
    }

    public Map<String, String> getSettingsHashesBefore()
    {
        return settingsHashesBefore;
    }

    public Map<String, String> getSettingsHashesAfter()
    {
        return settingsHashesAfter;
    }

    public Map<String, String> getHostFields()
    {
        return hostFields;
    }

    public FrameMetrics getMetrics()
    {
        return metrics;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    private static Map<String, String> copyMap(Map<String, String> values)
    {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }

    private static List<String> copyList(List<String> values)
    {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static void requireText(String value, String name)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(name + " is required.");
        }
    }

    private static void requireNotNegative(Long value, String name)
    {
        if (value != null && value.longValue() < 0L)
        {
            throw new IllegalArgumentException(name + " must not be negative.");
        }
    }

    /** Builds one run record. */
    public static final class Builder
    {
        private String runId;
        private int round;
        private int position;
        private String profileId;
        private String profileLabel;
        private Map<String, String> profileMetadata = new LinkedHashMap<String, String>();
        private Status status = Status.VALID;
        private String failure;
        private String startedAtUtc;
        private String finishedAtUtc;
        private Long warmupMillis;
        private Long captureMillis;
        private Long cooldownMillis;
        private Long elapsedMillis;
        private String collectorType;
        private String requestedMetric;
        private String selectedColumn;
        private Long processId;
        private String swapChain;
        private String rawCsvSha256;
        private int invalidRowCount;
        private int droppedFrameCount;
        private boolean droppedFrameCountAvailable = true;
        private Map<String, String> settingsHashesBefore =
            new LinkedHashMap<String, String>();
        private Map<String, String> settingsHashesAfter =
            new LinkedHashMap<String, String>();
        private Map<String, String> hostFields = new LinkedHashMap<String, String>();
        private FrameMetrics metrics;
        private List<String> warnings = new ArrayList<String>();

        public Builder()
        {
        }

        public Builder(String runId, int round, int position, String profileId)
        {
            this.runId = runId;
            this.round = round;
            this.position = position;
            this.profileId = profileId;
        }

        public Builder runId(String value)
        {
            runId = value;
            return this;
        }

        public Builder round(int value)
        {
            round = value;
            return this;
        }

        public Builder position(int value)
        {
            position = value;
            return this;
        }

        public Builder profileId(String value)
        {
            profileId = value;
            return this;
        }

        public Builder profileLabel(String value)
        {
            profileLabel = value;
            return this;
        }

        public Builder profileMetadata(Map<String, String> values)
        {
            profileMetadata = mutableCopy(values);
            return this;
        }

        public Builder profileMetadata(String name, String value)
        {
            profileMetadata.put(name, value);
            return this;
        }

        public Builder status(Status value)
        {
            status = value;
            return this;
        }

        public Builder failure(String value)
        {
            failure = value;
            return this;
        }

        public Builder startedAtUtc(String value)
        {
            startedAtUtc = value;
            return this;
        }

        public Builder finishedAtUtc(String value)
        {
            finishedAtUtc = value;
            return this;
        }

        public Builder timingMillis(Long warmup, Long capture, Long cooldown, Long elapsed)
        {
            warmupMillis = warmup;
            captureMillis = capture;
            cooldownMillis = cooldown;
            elapsedMillis = elapsed;
            return this;
        }

        public Builder warmupMillis(long value)
        {
            warmupMillis = Long.valueOf(value);
            return this;
        }

        public Builder captureMillis(long value)
        {
            captureMillis = Long.valueOf(value);
            return this;
        }

        public Builder cooldownMillis(long value)
        {
            cooldownMillis = Long.valueOf(value);
            return this;
        }

        public Builder elapsedMillis(long value)
        {
            elapsedMillis = Long.valueOf(value);
            return this;
        }

        public Builder collector(String type, String metric, String column)
        {
            collectorType = type;
            requestedMetric = metric;
            selectedColumn = column;
            return this;
        }

        public Builder collectorType(String value)
        {
            collectorType = value;
            return this;
        }

        public Builder requestedMetric(String value)
        {
            requestedMetric = value;
            return this;
        }

        public Builder selectedColumn(String value)
        {
            selectedColumn = value;
            return this;
        }

        public Builder processId(long value)
        {
            processId = Long.valueOf(value);
            return this;
        }

        public Builder swapChain(String value)
        {
            swapChain = value;
            return this;
        }

        public Builder rawCsvSha256(String value)
        {
            rawCsvSha256 = value;
            return this;
        }

        public Builder invalidRowCount(int value)
        {
            invalidRowCount = value;
            return this;
        }

        public Builder droppedFrameCount(int value)
        {
            droppedFrameCount = value;
            return this;
        }

        public Builder droppedFrameCountAvailable(boolean value)
        {
            droppedFrameCountAvailable = value;
            return this;
        }

        public Builder settingsHashesBefore(Map<String, String> values)
        {
            settingsHashesBefore = mutableCopy(values);
            return this;
        }

        public Builder settingsHashBefore(String path, String sha256)
        {
            settingsHashesBefore.put(path, sha256);
            return this;
        }

        public Builder settingsHashesAfter(Map<String, String> values)
        {
            settingsHashesAfter = mutableCopy(values);
            return this;
        }

        public Builder settingsHashAfter(String path, String sha256)
        {
            settingsHashesAfter.put(path, sha256);
            return this;
        }

        public Builder hostFields(Map<String, String> values)
        {
            hostFields = mutableCopy(values);
            return this;
        }

        public Builder hostField(String name, String value)
        {
            hostFields.put(name, value);
            return this;
        }

        public Builder metrics(FrameMetrics value)
        {
            metrics = value;
            return this;
        }

        public Builder warnings(List<String> values)
        {
            warnings = values == null
                ? new ArrayList<String>() : new ArrayList<String>(values);
            return this;
        }

        public Builder warning(String value)
        {
            warnings.add(value);
            return this;
        }

        public RunRecord build()
        {
            return new RunRecord(this);
        }

        private static Map<String, String> mutableCopy(Map<String, String> values)
        {
            return values == null ? new LinkedHashMap<String, String>()
                : new LinkedHashMap<String, String>(values);
        }
    }
}
