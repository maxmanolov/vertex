package vertex.benchmark.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import vertex.benchmark.capture.FrameMetrics;

/** Writes run records and suite summaries. */
public final class SuiteReportWriter
{
    public static final String RUN_DIRECTORY = "runs";
    public static final String RUN_JSON = "run.json";
    public static final String SUMMARY_JSON = "summary.json";
    public static final String SUMMARY_CSV = "summary.csv";
    public static final String SUMMARY_MARKDOWN = "summary.md";
    public static final String SUMMARY_HTML = "summary.html";

    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private static final String[] METRIC_KEYS = {
        "frameCount",
        "durationMillis",
        "meanFrameTimeMillis",
        "framesPerSecond",
        "p50Millis",
        "p95Millis",
        "p99Millis",
        "p999Millis",
        "maxMillis",
        "onePercentLowFps",
        "pointOnePercentLowFps",
        "slowFramesOver16_67Millis",
        "slowFramesOver33_33Millis",
        "slowFramesOver50Millis",
        "slowFramesOver100Millis"
    };

    private static final String[] METRIC_CSV_KEYS = {
        "frame_count",
        "duration_ms",
        "mean_frame_time_ms",
        "mean_fps",
        "p50_ms",
        "p95_ms",
        "p99_ms",
        "p999_ms",
        "max_ms",
        "one_percent_low_fps",
        "point_one_percent_low_fps",
        "slow_frames_over_16_67_ms",
        "slow_frames_over_33_33_ms",
        "slow_frames_over_50_ms",
        "slow_frames_over_100_ms"
    };

    private static final String[] METRIC_LABELS = {
        "Frame count",
        "Duration (ms)",
        "Mean frame time (ms)",
        "Mean FPS",
        "P50 frame time (ms)",
        "P95 frame time (ms)",
        "P99 frame time (ms)",
        "P99.9 frame time (ms)",
        "Maximum frame time (ms)",
        "1% low FPS",
        "0.1% low FPS",
        "Frames over 16.67 ms",
        "Frames over 33.33 ms",
        "Frames over 50 ms",
        "Frames over 100 ms"
    };

    private static final int MEAN_FRAME_TIME = 2;
    private static final int MEAN_FPS = 3;
    private static final int P99 = 6;
    private static final int ONE_PERCENT_LOW = 9;

    private static final String[] IMPROVEMENT_KEYS = {
        "framesPerSecond",
        "meanFrameTimeMillis",
        "p99Millis",
        "onePercentLowFps"
    };

    private static final String[] IMPROVEMENT_LABELS = {
        "Mean FPS",
        "Mean frame time",
        "P99 frame time",
        "1% low FPS"
    };

    private SuiteReportWriter()
    {
    }

    /** Writes all run files and all summary files. */
    public static void write(Path outputDirectory, String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records) throws IOException
    {
        requireOutputDirectory(outputDirectory);
        Summary summary = summarize(baselineProfileId, requiredValidRepetitions, records);
        writeAll(outputDirectory, summary);
    }

    /** Writes one named suite and its run files. */
    public static void writeSuite(Path outputDirectory, String suiteId,
        String baselineProfileId, int requiredValidRepetitions,
        List<RunRecord> records, List<String> suiteWarnings) throws IOException
    {
        requireOutputDirectory(outputDirectory);
        if (suiteId == null || suiteId.trim().isEmpty())
        {
            throw new IllegalArgumentException("Suite ID is required.");
        }
        Summary summary = summarize(suiteId, baselineProfileId,
            requiredValidRepetitions, records, suiteWarnings);
        writeAll(outputDirectory, summary);
    }

    /** Writes one named suite summary without rewriting saved run files. */
    public static void writeSuiteSummary(Path outputDirectory, String suiteId,
        String baselineProfileId, int requiredValidRepetitions,
        List<RunRecord> records, List<String> suiteWarnings) throws IOException
    {
        requireOutputDirectory(outputDirectory);
        if (suiteId == null || suiteId.trim().isEmpty())
        {
            throw new IllegalArgumentException("Suite ID is required.");
        }
        Summary summary = summarize(suiteId, baselineProfileId,
            requiredValidRepetitions, records, suiteWarnings);
        Files.createDirectories(outputDirectory);
        writeSummaryFiles(outputDirectory, summary);
    }

    private static void writeAll(Path outputDirectory, Summary summary) throws IOException
    {
        Files.createDirectories(outputDirectory);
        Path runDirectory = outputDirectory.resolve(RUN_DIRECTORY);
        Files.createDirectories(runDirectory);

        Map<String, String> fileOwners = new LinkedHashMap<String, String>();
        for (RunRecord record : summary.records)
        {
            String directoryName = runDirectoryName(record.getRunId());
            String previous = fileOwners.put(directoryName, record.getRunId());
            if (previous != null)
            {
                throw new IllegalArgumentException("Run IDs map to the same directory: "
                    + previous + " and " + record.getRunId() + '.');
            }
            Path recordDirectory = runDirectory.resolve(directoryName);
            Files.createDirectories(recordDirectory);
            writeUtf8(recordDirectory.resolve(RUN_JSON), toRunJson(record));
        }

        writeSummaryFiles(outputDirectory, summary);
    }

    /** Writes one run file in the run directory. */
    public static Path writeRun(Path outputDirectory, RunRecord record) throws IOException
    {
        requireOutputDirectory(outputDirectory);
        requireRecord(record);
        Path runDirectory = outputDirectory.resolve(RUN_DIRECTORY)
            .resolve(runDirectoryName(record.getRunId()));
        Files.createDirectories(runDirectory);
        Path output = runDirectory.resolve(RUN_JSON);
        writeUtf8(output, toRunJson(record));
        return output;
    }

    /** Writes the suite summary files. */
    public static void writeSummary(Path outputDirectory, String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records) throws IOException
    {
        requireOutputDirectory(outputDirectory);
        Summary summary = summarize(baselineProfileId, requiredValidRepetitions, records);
        Files.createDirectories(outputDirectory);
        writeSummaryFiles(outputDirectory, summary);
    }

    public static String toRunJson(RunRecord record)
    {
        requireRecord(record);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(record.getSchemaVersion()));
        root.addProperty("runId", record.getRunId());
        root.addProperty("round", Integer.valueOf(record.getRound()));
        root.addProperty("position", Integer.valueOf(record.getPosition()));

        JsonObject profile = new JsonObject();
        profile.addProperty("id", record.getProfileId());
        profile.addProperty("label", record.getProfileLabel());
        profile.add("metadata", stringMap(record.getProfileMetadata()));
        root.add("profile", profile);

        root.addProperty("status", record.getStatus().getValue());
        addString(root, "failure", record.getFailure());

        JsonObject timing = new JsonObject();
        addString(timing, "startedAtUtc", record.getStartedAtUtc());
        addString(timing, "finishedAtUtc", record.getFinishedAtUtc());
        addLong(timing, "warmupMillis", record.getWarmupMillis());
        addLong(timing, "captureMillis", record.getCaptureMillis());
        addLong(timing, "cooldownMillis", record.getCooldownMillis());
        addLong(timing, "elapsedMillis", record.getElapsedMillis());
        root.add("timing", timing);

        JsonObject collector = new JsonObject();
        addString(collector, "type", record.getCollectorType());
        addString(collector, "requestedMetric", record.getRequestedMetric());
        addString(collector, "selectedColumn", record.getSelectedColumn());
        addLong(collector, "processId", record.getProcessId());
        addString(collector, "swapChain", record.getSwapChain());
        addString(collector, "rawCsvSha256", record.getRawCsvSha256());
        collector.addProperty("invalidRows", Integer.valueOf(record.getInvalidRowCount()));
        collector.addProperty("droppedFrameDetectionAvailable",
            Boolean.valueOf(record.isDroppedFrameCountAvailable()));
        if (record.isDroppedFrameCountAvailable())
        {
            collector.addProperty("droppedFrames",
                Integer.valueOf(record.getDroppedFrameCount()));
        }
        else
        {
            collector.add("droppedFrames", JsonNull.INSTANCE);
        }
        root.add("collector", collector);

        JsonObject hashes = new JsonObject();
        hashes.add("before", stringMap(record.getSettingsHashesBefore()));
        hashes.add("after", stringMap(record.getSettingsHashesAfter()));
        root.add("settingsHashes", hashes);
        root.add("host", stringMap(record.getHostFields()));
        root.add("metrics", metricsJson(record.getMetrics()));

        JsonArray warnings = new JsonArray();
        for (String warning : record.getWarnings())
        {
            if (warning == null)
            {
                warnings.add(JsonNull.INSTANCE);
            }
            else
            {
                warnings.add(new com.google.gson.JsonPrimitive(warning));
            }
        }
        root.add("warnings", warnings);
        return GSON.toJson(root) + '\n';
    }

    public static String toSummaryJson(String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records)
    {
        return summaryJson(summarize(baselineProfileId, requiredValidRepetitions, records));
    }

    public static String toSummaryCsv(String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records)
    {
        return summaryCsv(summarize(baselineProfileId, requiredValidRepetitions, records));
    }

    public static String toSummaryMarkdown(String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records)
    {
        return summaryMarkdown(summarize(baselineProfileId,
            requiredValidRepetitions, records));
    }

    public static String toSummaryHtml(String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records)
    {
        return summaryHtml(summarize(baselineProfileId,
            requiredValidRepetitions, records));
    }

    /** Returns the directory name for one run ID. */
    public static String runDirectoryName(String runId)
    {
        if (runId == null || runId.trim().isEmpty())
        {
            throw new IllegalArgumentException("Run ID is required.");
        }
        StringBuilder name = new StringBuilder(runId.length());
        for (int index = 0; index < runId.length(); ++index)
        {
            char value = runId.charAt(index);
            if ((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9') || value == '-' || value == '_'
                || value == '.')
            {
                name.append(value);
            }
            else
            {
                name.append('_');
            }
        }
        if (name.length() == 0 || ".".contentEquals(name) || "..".contentEquals(name))
        {
            name.insert(0, "run_");
        }
        return name.toString();
    }

    private static void writeSummaryFiles(Path outputDirectory, Summary summary)
        throws IOException
    {
        writeUtf8(outputDirectory.resolve(SUMMARY_JSON), summaryJson(summary));
        writeUtf8(outputDirectory.resolve(SUMMARY_CSV), summaryCsv(summary));
        writeUtf8(outputDirectory.resolve(SUMMARY_MARKDOWN), summaryMarkdown(summary));
        writeUtf8(outputDirectory.resolve(SUMMARY_HTML), summaryHtml(summary));
    }

    private static String summaryJson(Summary summary)
    {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(RunRecord.SCHEMA_VERSION));
        addString(root, "suiteId", summary.suiteId);
        root.addProperty("baselineProfileId", summary.baselineProfileId);
        root.addProperty("requiredValidRepetitions",
            Integer.valueOf(summary.requiredValidRepetitions));
        root.addProperty("outliersRemoved", Boolean.FALSE);

        JsonArray profiles = new JsonArray();
        for (ProfileAggregate profile : summary.profiles)
        {
            JsonObject item = new JsonObject();
            item.addProperty("profileId", profile.profileId);
            item.addProperty("profileLabel", profile.profileLabel);
            item.add("profileMetadata", stringMap(profile.profileMetadata));
            item.addProperty("validRuns", Integer.valueOf(profile.validCount));
            item.addProperty("invalidRuns", Integer.valueOf(profile.invalidCount));
            item.addProperty("sufficient", Boolean.valueOf(profile.sufficient));
            JsonObject metrics = new JsonObject();
            for (int index = 0; index < METRIC_KEYS.length; ++index)
            {
                metrics.add(METRIC_KEYS[index], statsJson(profile.metrics[index]));
            }
            item.add("metrics", metrics);
            profiles.add(item);
        }
        root.add("profiles", profiles);

        JsonArray comparisons = new JsonArray();
        for (ComparisonAggregate comparison : summary.comparisons)
        {
            JsonObject item = new JsonObject();
            item.addProperty("profileId", comparison.profileId);
            item.addProperty("baselineProfileId", summary.baselineProfileId);
            item.addProperty("pairedRuns", Integer.valueOf(comparison.pairedCount));
            item.addProperty("sufficient", Boolean.valueOf(comparison.sufficient));
            JsonObject improvements = new JsonObject();
            for (int index = 0; index < IMPROVEMENT_KEYS.length; ++index)
            {
                improvements.add(IMPROVEMENT_KEYS[index],
                    statsJson(comparison.improvements[index]));
            }
            item.add("medianImprovementPercent", improvements);
            comparisons.add(item);
        }
        root.add("comparisons", comparisons);

        JsonArray warnings = new JsonArray();
        for (String warning : summary.suiteWarnings)
        {
            if (warning == null)
            {
                warnings.add(JsonNull.INSTANCE);
            }
            else
            {
                warnings.add(new com.google.gson.JsonPrimitive(warning));
            }
        }
        root.add("warnings", warnings);
        return GSON.toJson(root) + '\n';
    }

    private static String summaryCsv(Summary summary)
    {
        StringBuilder csv = new StringBuilder();
        List<String> header = new ArrayList<String>();
        Collections.addAll(header, "suite_id", "profile_id", "profile_label", "valid_runs",
            "invalid_runs", "sufficient", "paired_runs", "paired_sufficient",
            "mean_fps_improvement_percent", "mean_frame_time_improvement_percent",
            "p99_improvement_percent", "one_percent_low_fps_improvement_percent");
        for (String key : METRIC_CSV_KEYS)
        {
            header.add(key + "_median");
            header.add(key + "_minimum");
            header.add(key + "_maximum");
        }
        appendCsvRow(csv, header);

        Map<String, ComparisonAggregate> comparisonByProfile =
            new LinkedHashMap<String, ComparisonAggregate>();
        for (ComparisonAggregate comparison : summary.comparisons)
        {
            comparisonByProfile.put(comparison.profileId, comparison);
        }

        for (ProfileAggregate profile : summary.profiles)
        {
            List<String> row = new ArrayList<String>();
            row.add(summary.suiteId == null ? "" : summary.suiteId);
            row.add(profile.profileId);
            row.add(profile.profileLabel);
            row.add(Integer.toString(profile.validCount));
            row.add(Integer.toString(profile.invalidCount));
            row.add(Boolean.toString(profile.sufficient));
            ComparisonAggregate comparison = comparisonByProfile.get(profile.profileId);
            if (comparison == null)
            {
                Collections.addAll(row, "", "", "", "", "", "");
            }
            else
            {
                row.add(Integer.toString(comparison.pairedCount));
                row.add(Boolean.toString(comparison.sufficient));
                for (MetricStats improvement : comparison.improvements)
                {
                    row.add(csvNumber(improvement.median()));
                }
            }
            for (MetricStats metric : profile.metrics)
            {
                row.add(csvNumber(metric.median()));
                row.add(csvNumber(metric.minimum()));
                row.add(csvNumber(metric.maximum()));
            }
            appendCsvRow(csv, row);
        }
        return csv.toString();
    }

    private static String summaryMarkdown(Summary summary)
    {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Benchmark summary\n\n");
        if (summary.suiteId != null)
        {
            markdown.append("Suite: `").append(markdown(summary.suiteId)).append("`\n\n");
        }
        markdown.append("Baseline: `").append(markdown(summary.baselineProfileId))
            .append("`\n\n");
        markdown.append("Required valid runs: ")
            .append(summary.requiredValidRepetitions).append("\n\n");
        markdown.append("No outlier was removed.\n\n");
        if (!summary.suiteWarnings.isEmpty())
        {
            markdown.append("## Warnings\n\n");
            for (String warning : summary.suiteWarnings)
            {
                markdown.append("- ").append(markdown(warning)).append('\n');
            }
            markdown.append('\n');
        }
        markdown.append("## Profiles\n\n");
        markdown.append("| Profile | Valid | Invalid | Status | Mean FPS | ")
            .append("Mean frame time (ms) | P99 (ms) | 1% low FPS |\n");
        markdown.append("|---|---:|---:|---|---:|---:|---:|---:|\n");
        for (ProfileAggregate profile : summary.profiles)
        {
            markdown.append("| ").append(markdown(profile.profileLabel)).append(" (`")
                .append(markdown(profile.profileId)).append("`) | ")
                .append(profile.validCount).append(" | ")
                .append(profile.invalidCount).append(" | ")
                .append(profile.sufficient ? "sufficient" : "insufficient").append(" | ")
                .append(range(profile.metrics[MEAN_FPS])).append(" | ")
                .append(range(profile.metrics[MEAN_FRAME_TIME])).append(" | ")
                .append(range(profile.metrics[P99])).append(" | ")
                .append(range(profile.metrics[ONE_PERCENT_LOW])).append(" |\n");
        }

        markdown.append("\n## Paired improvements\n\n");
        markdown.append("Positive values mean that the candidate performs better.\n\n");
        markdown.append("| Profile | Pairs | Status | Mean FPS | Mean frame time | ")
            .append("P99 frame time | 1% low FPS |\n");
        markdown.append("|---|---:|---|---:|---:|---:|---:|\n");
        for (ComparisonAggregate comparison : summary.comparisons)
        {
            ProfileAggregate profile = summary.profileById.get(comparison.profileId);
            markdown.append("| ").append(markdown(profile.profileLabel)).append(" (`")
                .append(markdown(comparison.profileId)).append("`) | ")
                .append(comparison.pairedCount).append(" | ")
                .append(comparison.sufficient ? "sufficient" : "insufficient");
            for (MetricStats improvement : comparison.improvements)
            {
                markdown.append(" | ").append(percent(improvement.median()));
            }
            markdown.append(" |\n");
        }

        markdown.append("\n## Full profile metrics\n");
        for (ProfileAggregate profile : summary.profiles)
        {
            markdown.append("\n### ").append(markdown(profile.profileLabel)).append(" (`")
                .append(markdown(profile.profileId)).append("`)\n\n");
            markdown.append("| Metric | Samples | Median | Minimum | Maximum |\n");
            markdown.append("|---|---:|---:|---:|---:|\n");
            for (int index = 0; index < METRIC_LABELS.length; ++index)
            {
                MetricStats metric = profile.metrics[index];
                markdown.append("| ").append(METRIC_LABELS[index]).append(" | ")
                    .append(metric.size()).append(" | ")
                    .append(number(metric.median())).append(" | ")
                    .append(number(metric.minimum())).append(" | ")
                    .append(number(metric.maximum())).append(" |\n");
            }
        }
        return markdown.toString();
    }

    private static String summaryHtml(Summary summary)
    {
        StringBuilder html = new StringBuilder(8192);
        html.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
            .append("<meta charset=\"utf-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
            .append("<title>Minecraft benchmark results</title>\n")
            .append("<style>\n")
            .append(":root{color-scheme:light dark;font-family:system-ui,-apple-system,Segoe UI,sans-serif}")
            .append("body{max-width:1100px;margin:0 auto;padding:32px 20px;line-height:1.45}")
            .append("h1{margin-bottom:8px}h2{margin-top:32px}")
            .append(".meta,.note{color:#667085}.warning{border-left:4px solid #d97706;padding:8px 12px;background:#d9770618}")
            .append(".table-wrap{overflow-x:auto}table{border-collapse:collapse;width:100%}")
            .append("th,td{padding:10px 12px;border-bottom:1px solid #8885;text-align:right;white-space:nowrap}")
            .append("th:first-child,td:first-child{text-align:left}.positive{color:#16803c;font-weight:700}")
            .append(".negative{color:#c52a2a;font-weight:700}.neutral{font-weight:700}")
            .append("a{color:#2563eb}code{font-family:ui-monospace,Consolas,monospace}\n")
            .append("</style>\n</head>\n<body>\n")
            .append("<h1>Minecraft benchmark results</h1>\n");

        if (summary.suiteId != null)
        {
            html.append("<p class=\"meta\">Suite: <code>")
                .append(html(summary.suiteId)).append("</code></p>\n");
        }
        html.append("<p class=\"meta\">Baseline: <code>")
            .append(html(summary.baselineProfileId)).append("</code> &middot; Required valid runs: ")
            .append(summary.requiredValidRepetitions).append("</p>\n");

        if (!summary.suiteWarnings.isEmpty())
        {
            html.append("<section class=\"warning\"><h2>Warnings</h2><ul>\n");
            for (String warning : summary.suiteWarnings)
            {
                html.append("<li>").append(html(warning)).append("</li>\n");
            }
            html.append("</ul></section>\n");
        }

        html.append("<h2>Results</h2>\n")
            .append("<p class=\"note\">These values are medians. Positive improvement means that the candidate performs better.</p>\n")
            .append("<div class=\"table-wrap\"><table>\n<thead><tr>")
            .append("<th>Profile</th><th>Valid runs</th><th>Mean FPS</th><th>1% low FPS</th>")
            .append("<th>Mean frame time</th><th>P99 frame time</th></tr></thead>\n<tbody>\n");
        for (ProfileAggregate profile : summary.profiles)
        {
            html.append("<tr><td>").append(html(profile.profileLabel)).append(" <code>")
                .append(html(profile.profileId)).append("</code></td><td>")
                .append(profile.validCount).append(" / ")
                .append(summary.requiredValidRepetitions).append(profile.sufficient ? "" : " (insufficient)")
                .append("</td><td>").append(number(profile.metrics[MEAN_FPS].median()))
                .append("</td><td>").append(number(profile.metrics[ONE_PERCENT_LOW].median()))
                .append("</td><td>").append(number(profile.metrics[MEAN_FRAME_TIME].median()))
                .append(" ms</td><td>").append(number(profile.metrics[P99].median()))
                .append(" ms</td></tr>\n");
        }
        html.append("</tbody></table></div>\n");

        if (!summary.comparisons.isEmpty())
        {
            html.append("<h2>Improvement from baseline</h2>\n")
                .append("<div class=\"table-wrap\"><table>\n<thead><tr>")
                .append("<th>Profile</th><th>Paired runs</th><th>Mean FPS</th><th>1% low FPS</th>")
                .append("<th>Mean frame time</th><th>P99 frame time</th></tr></thead>\n<tbody>\n");
            for (ComparisonAggregate comparison : summary.comparisons)
            {
                ProfileAggregate profile = summary.profileById.get(comparison.profileId);
                html.append("<tr><td>").append(html(profile.profileLabel)).append(" <code>")
                    .append(html(comparison.profileId)).append("</code></td><td>")
                    .append(comparison.pairedCount)
                    .append(comparison.sufficient ? "" : " (insufficient)").append("</td>")
                    .append(improvementCell(comparison.improvements[0].median()))
                    .append(improvementCell(comparison.improvements[3].median()))
                    .append(improvementCell(comparison.improvements[1].median()))
                    .append(improvementCell(comparison.improvements[2].median()))
                    .append("</tr>\n");
            }
            html.append("</tbody></table></div>\n");
        }

        html.append("<p class=\"note\">No outlier was removed. Open ")
            .append("<a href=\"summary.csv\">summary.csv</a>, ")
            .append("<a href=\"summary.json\">summary.json</a>, or ")
            .append("<a href=\"summary.md\">summary.md</a> for more data.</p>\n")
            .append("</body>\n</html>\n");
        return html.toString();
    }

    private static String improvementCell(double value)
    {
        String style = "neutral";
        if (!Double.isNaN(value) && !Double.isInfinite(value))
        {
            if (value > 0.0000001D)
            {
                style = "positive";
            }
            else if (value < -0.0000001D)
            {
                style = "negative";
            }
        }
        return "<td class=\"" + style + "\">" + percent(value) + "</td>";
    }

    private static String html(String value)
    {
        if (value == null)
        {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); ++index)
        {
            char character = value.charAt(index);
            switch (character)
            {
                case '&': escaped.append("&amp;"); break;
                case '<': escaped.append("&lt;"); break;
                case '>': escaped.append("&gt;"); break;
                case '"': escaped.append("&quot;"); break;
                case '\'': escaped.append("&#39;"); break;
                default: escaped.append(character); break;
            }
        }
        return escaped.toString();
    }

    private static Summary summarize(String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records)
    {
        return summarize(null, baselineProfileId, requiredValidRepetitions, records,
            Collections.<String>emptyList());
    }

    private static Summary summarize(String suiteId, String baselineProfileId,
        int requiredValidRepetitions, Collection<RunRecord> records,
        List<String> suiteWarnings)
    {
        if (baselineProfileId == null || baselineProfileId.trim().isEmpty())
        {
            throw new IllegalArgumentException("Baseline profile ID is required.");
        }
        if (requiredValidRepetitions < 1)
        {
            throw new IllegalArgumentException("Required valid runs must be at least 1.");
        }
        if (records == null || records.isEmpty())
        {
            throw new IllegalArgumentException("Run records are required.");
        }

        List<RunRecord> sortedRecords = new ArrayList<RunRecord>(records.size());
        Set<String> runIds = new TreeSet<String>();
        for (RunRecord record : records)
        {
            requireRecord(record);
            if (!runIds.add(record.getRunId()))
            {
                throw new IllegalArgumentException("Duplicate run ID: " + record.getRunId());
            }
            sortedRecords.add(record);
        }
        Collections.sort(sortedRecords, RUN_ORDER);

        Map<String, List<RunRecord>> recordsByProfile =
            new LinkedHashMap<String, List<RunRecord>>();
        for (RunRecord record : sortedRecords)
        {
            List<RunRecord> profileRecords = recordsByProfile.get(record.getProfileId());
            if (profileRecords == null)
            {
                profileRecords = new ArrayList<RunRecord>();
                recordsByProfile.put(record.getProfileId(), profileRecords);
            }
            profileRecords.add(record);
        }
        if (!recordsByProfile.containsKey(baselineProfileId))
        {
            throw new IllegalArgumentException("Missing baseline profile: "
                + baselineProfileId + '.');
        }

        List<String> profileOrder = new ArrayList<String>();
        profileOrder.add(baselineProfileId);
        TreeSet<String> candidates = new TreeSet<String>(recordsByProfile.keySet());
        candidates.remove(baselineProfileId);
        profileOrder.addAll(candidates);

        List<ProfileAggregate> profiles = new ArrayList<ProfileAggregate>();
        Map<String, ProfileAggregate> profileById =
            new LinkedHashMap<String, ProfileAggregate>();
        for (String profileId : profileOrder)
        {
            ProfileAggregate profile = new ProfileAggregate(profileId,
                recordsByProfile.get(profileId), requiredValidRepetitions);
            profiles.add(profile);
            profileById.put(profileId, profile);
        }

        Map<Integer, RunRecord> baselineByRound = indexRounds(baselineProfileId,
            recordsByProfile.get(baselineProfileId));
        List<ComparisonAggregate> comparisons = new ArrayList<ComparisonAggregate>();
        for (String profileId : candidates)
        {
            comparisons.add(new ComparisonAggregate(profileId, baselineByRound,
                indexRounds(profileId, recordsByProfile.get(profileId)),
                requiredValidRepetitions));
        }
        return new Summary(suiteId, baselineProfileId, requiredValidRepetitions,
            sortedRecords, profiles, comparisons, profileById,
            suiteWarnings == null ? Collections.<String>emptyList()
                : new ArrayList<String>(suiteWarnings));
    }

    private static Map<Integer, RunRecord> indexRounds(String profileId,
        List<RunRecord> records)
    {
        Map<Integer, RunRecord> byRound = new LinkedHashMap<Integer, RunRecord>();
        for (RunRecord record : records)
        {
            Integer round = Integer.valueOf(record.getRound());
            if (byRound.put(round, record) != null)
            {
                throw new IllegalArgumentException("Duplicate round " + round
                    + " for profile " + profileId + '.');
            }
        }
        return byRound;
    }

    private static JsonObject metricsJson(FrameMetrics metrics)
    {
        if (metrics == null)
        {
            return null;
        }
        JsonObject result = new JsonObject();
        for (int index = 0; index < METRIC_KEYS.length; ++index)
        {
            addDouble(result, METRIC_KEYS[index], metricValue(metrics, index));
        }
        return result;
    }

    private static JsonObject statsJson(MetricStats stats)
    {
        JsonObject result = new JsonObject();
        result.addProperty("samples", Integer.valueOf(stats.size()));
        addDouble(result, "median", stats.median());
        addDouble(result, "minimum", stats.minimum());
        addDouble(result, "maximum", stats.maximum());
        return result;
    }

    private static JsonObject stringMap(Map<String, String> values)
    {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, String> entry : values.entrySet())
        {
            addString(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static void addString(JsonObject object, String name, String value)
    {
        if (value == null)
        {
            object.add(name, JsonNull.INSTANCE);
        }
        else
        {
            object.addProperty(name, value);
        }
    }

    private static void addLong(JsonObject object, String name, Long value)
    {
        if (value == null)
        {
            object.add(name, JsonNull.INSTANCE);
        }
        else
        {
            object.addProperty(name, value);
        }
    }

    private static void addDouble(JsonObject object, String name, double value)
    {
        if (Double.isNaN(value) || Double.isInfinite(value))
        {
            object.add(name, JsonNull.INSTANCE);
        }
        else
        {
            object.addProperty(name, Double.valueOf(value));
        }
    }

    private static double metricValue(FrameMetrics metrics, int index)
    {
        switch (index)
        {
            case 0: return metrics.getFrameCount();
            case 1: return metrics.getDurationMillis();
            case 2: return metrics.getMeanFrameTimeMillis();
            case 3: return metrics.getFramesPerSecond();
            case 4: return metrics.getP50Millis();
            case 5: return metrics.getP95Millis();
            case 6: return metrics.getP99Millis();
            case 7: return metrics.getP999Millis();
            case 8: return metrics.getMaxMillis();
            case 9: return metrics.getOnePercentLowFps();
            case 10: return metrics.getPointOnePercentLowFps();
            case 11: return metrics.getSlowFrameCountOver16_67Millis();
            case 12: return metrics.getSlowFrameCountOver33_33Millis();
            case 13: return metrics.getSlowFrameCountOver50Millis();
            case 14: return metrics.getSlowFrameCountOver100Millis();
            default: throw new IllegalArgumentException("Unknown metric index: " + index);
        }
    }

    private static void appendCsvRow(StringBuilder csv, List<String> values)
    {
        for (int index = 0; index < values.size(); ++index)
        {
            if (index > 0)
            {
                csv.append(',');
            }
            appendCsvValue(csv, values.get(index));
        }
        csv.append('\n');
    }

    private static void appendCsvValue(StringBuilder csv, String value)
    {
        if (value == null)
        {
            return;
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
            && value.indexOf('\r') < 0 && value.indexOf('\n') < 0)
        {
            csv.append(value);
            return;
        }
        csv.append('"');
        for (int index = 0; index < value.length(); ++index)
        {
            char character = value.charAt(index);
            if (character == '"')
            {
                csv.append("\"\"");
            }
            else
            {
                csv.append(character);
            }
        }
        csv.append('"');
    }

    private static String csvNumber(double value)
    {
        if (Double.isNaN(value) || Double.isInfinite(value))
        {
            return "";
        }
        return String.format(Locale.ROOT, "%.6f", Double.valueOf(value));
    }

    private static String range(MetricStats stats)
    {
        if (stats.size() == 0)
        {
            return "n/a";
        }
        return number(stats.median()) + " [" + number(stats.minimum()) + ", "
            + number(stats.maximum()) + ']';
    }

    private static String percent(double value)
    {
        if (Double.isNaN(value) || Double.isInfinite(value))
        {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%+.3f%%", Double.valueOf(value));
    }

    private static String number(double value)
    {
        if (Double.isNaN(value) || Double.isInfinite(value))
        {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.3f", Double.valueOf(value));
    }

    private static String markdown(String value)
    {
        return value == null ? "" : value.replace("\\", "\\\\")
            .replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static void writeUtf8(Path path, String value) throws IOException
    {
        Path parent = path.getParent();
        if (parent == null)
        {
            parent = path.toAbsolutePath().getParent();
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent,
            "." + path.getFileName().toString() + '.', ".tmp");
        try
        {
            Files.write(temporary, value.getBytes(StandardCharsets.UTF_8));
            try
            {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    private static void requireOutputDirectory(Path outputDirectory)
    {
        if (outputDirectory == null)
        {
            throw new IllegalArgumentException("Output directory is required.");
        }
    }

    private static void requireRecord(RunRecord record)
    {
        if (record == null)
        {
            throw new IllegalArgumentException("Run record is required.");
        }
    }

    private static final Comparator<RunRecord> RUN_ORDER = new Comparator<RunRecord>()
    {
        @Override
        public int compare(RunRecord left, RunRecord right)
        {
            int round = Integer.compare(left.getRound(), right.getRound());
            if (round != 0)
            {
                return round;
            }
            int position = Integer.compare(left.getPosition(), right.getPosition());
            if (position != 0)
            {
                return position;
            }
            int profile = left.getProfileId().compareTo(right.getProfileId());
            if (profile != 0)
            {
                return profile;
            }
            return left.getRunId().compareTo(right.getRunId());
        }
    };

    private static final class Summary
    {
        private final String suiteId;
        private final String baselineProfileId;
        private final int requiredValidRepetitions;
        private final List<RunRecord> records;
        private final List<ProfileAggregate> profiles;
        private final List<ComparisonAggregate> comparisons;
        private final Map<String, ProfileAggregate> profileById;
        private final List<String> suiteWarnings;

        private Summary(String suiteId, String baselineProfileId,
            int requiredValidRepetitions,
            List<RunRecord> records, List<ProfileAggregate> profiles,
            List<ComparisonAggregate> comparisons,
            Map<String, ProfileAggregate> profileById, List<String> suiteWarnings)
        {
            this.suiteId = suiteId;
            this.baselineProfileId = baselineProfileId;
            this.requiredValidRepetitions = requiredValidRepetitions;
            this.records = records;
            this.profiles = profiles;
            this.comparisons = comparisons;
            this.profileById = profileById;
            this.suiteWarnings = suiteWarnings;
        }
    }

    private static final class ProfileAggregate
    {
        private final String profileId;
        private final String profileLabel;
        private final Map<String, String> profileMetadata;
        private final int validCount;
        private final int invalidCount;
        private final boolean sufficient;
        private final MetricStats[] metrics;

        private ProfileAggregate(String profileId, List<RunRecord> records,
            int requiredValidRepetitions)
        {
            this.profileId = profileId;
            profileLabel = records.get(0).getProfileLabel();
            profileMetadata = records.get(0).getProfileMetadata();
            metrics = newStats(METRIC_KEYS.length);
            int valid = 0;
            int invalid = 0;
            for (RunRecord record : records)
            {
                if (!record.isValid())
                {
                    ++invalid;
                    continue;
                }
                ++valid;
                for (int index = 0; index < metrics.length; ++index)
                {
                    metrics[index].add(metricValue(record.getMetrics(), index));
                }
            }
            validCount = valid;
            invalidCount = invalid;
            sufficient = valid >= requiredValidRepetitions;
        }
    }

    private static final class ComparisonAggregate
    {
        private final String profileId;
        private final int pairedCount;
        private final boolean sufficient;
        private final MetricStats[] improvements;

        private ComparisonAggregate(String profileId,
            Map<Integer, RunRecord> baselineByRound,
            Map<Integer, RunRecord> candidateByRound,
            int requiredValidRepetitions)
        {
            this.profileId = profileId;
            improvements = newStats(IMPROVEMENT_KEYS.length);
            int pairs = 0;
            for (Map.Entry<Integer, RunRecord> entry : candidateByRound.entrySet())
            {
                RunRecord candidate = entry.getValue();
                RunRecord baseline = baselineByRound.get(entry.getKey());
                if (!candidate.isValid() || baseline == null || !baseline.isValid())
                {
                    continue;
                }
                ++pairs;
                addImprovement(improvements[0], candidate, baseline, MEAN_FPS, true);
                addImprovement(improvements[1], candidate, baseline,
                    MEAN_FRAME_TIME, false);
                addImprovement(improvements[2], candidate, baseline, P99, false);
                addImprovement(improvements[3], candidate, baseline,
                    ONE_PERCENT_LOW, true);
            }
            pairedCount = pairs;
            sufficient = pairs >= requiredValidRepetitions;
        }

        private static void addImprovement(MetricStats target, RunRecord candidate,
            RunRecord baseline, int metric, boolean higherIsBetter)
        {
            double baselineValue = metricValue(baseline.getMetrics(), metric);
            double candidateValue = metricValue(candidate.getMetrics(), metric);
            if (baselineValue == 0.0D || Double.isNaN(baselineValue)
                || Double.isInfinite(baselineValue) || Double.isNaN(candidateValue)
                || Double.isInfinite(candidateValue))
            {
                return;
            }
            double change = higherIsBetter
                ? (candidateValue - baselineValue) * 100.0D / baselineValue
                : (baselineValue - candidateValue) * 100.0D / baselineValue;
            target.add(change);
        }
    }

    private static MetricStats[] newStats(int count)
    {
        MetricStats[] result = new MetricStats[count];
        for (int index = 0; index < count; ++index)
        {
            result[index] = new MetricStats();
        }
        return result;
    }

    private static final class MetricStats
    {
        private final List<Double> values = new ArrayList<Double>();

        private void add(double value)
        {
            if (!Double.isNaN(value) && !Double.isInfinite(value))
            {
                values.add(Double.valueOf(value));
            }
        }

        private int size()
        {
            return values.size();
        }

        private double median()
        {
            if (values.isEmpty())
            {
                return Double.NaN;
            }
            List<Double> sorted = new ArrayList<Double>(values);
            Collections.sort(sorted);
            int middle = sorted.size() / 2;
            if (sorted.size() % 2 != 0)
            {
                return sorted.get(middle).doubleValue();
            }
            return sorted.get(middle - 1).doubleValue() / 2.0D
                + sorted.get(middle).doubleValue() / 2.0D;
        }

        private double minimum()
        {
            return values.isEmpty() ? Double.NaN
                : Collections.min(values).doubleValue();
        }

        private double maximum()
        {
            return values.isEmpty() ? Double.NaN
                : Collections.max(values).doubleValue();
        }
    }
}
