package vertex.benchmark.quick;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import vertex.benchmark.report.SuiteReportWriter;

/** Writes one overview for a completed set of scenario reports. */
public final class MultiScenarioReportWriter
{
    public static final String SUMMARY_HTML = "summary.html";
    public static final String SUMMARY_JSON = "summary.json";

    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private MultiScenarioReportWriter()
    {
    }

    /** Writes the HTML and JSON overview. Scenario order is kept. */
    public static void write(Path outputDirectory, List<Scenario> scenarios)
        throws IOException
    {
        if (outputDirectory == null)
        {
            throw new IllegalArgumentException("Output directory is required.");
        }
        if (scenarios == null || scenarios.isEmpty())
        {
            throw new IllegalArgumentException("At least one scenario is required.");
        }

        Set<String> ids = new LinkedHashSet<String>();
        List<ScenarioSummary> summaries = new ArrayList<ScenarioSummary>();

        for (Scenario scenario : scenarios)
        {
            if (scenario == null)
            {
                throw new IllegalArgumentException("Scenario is required.");
            }
            if (!ids.add(scenario.id))
            {
                throw new IllegalArgumentException("Duplicate scenario ID: " + scenario.id);
            }
            summaries.add(read(outputDirectory, scenario));
        }

        Files.createDirectories(outputDirectory);
        writeUtf8(outputDirectory.resolve(SUMMARY_JSON), json(summaries));
        writeUtf8(outputDirectory.resolve(SUMMARY_HTML), html(summaries));
    }

    private static ScenarioSummary read(Path outputDirectory, Scenario scenario)
        throws IOException
    {
        Path summaryFile = scenario.directory.resolve(SuiteReportWriter.SUMMARY_JSON);
        Path reportFile = scenario.directory.resolve(SuiteReportWriter.SUMMARY_HTML);

        if (!Files.isRegularFile(summaryFile))
        {
            throw new IOException("Scenario " + scenario.id
                + " has no completed summary.json file: " + summaryFile);
        }
        if (!Files.isRegularFile(reportFile))
        {
            throw new IOException("Scenario " + scenario.id
                + " has no completed summary.html file: " + reportFile);
        }

        JsonObject root;

        try
        {
            String text = new String(Files.readAllBytes(summaryFile), StandardCharsets.UTF_8);
            root = new JsonParser().parse(text).getAsJsonObject();
        }
        catch (RuntimeException error)
        {
            throw new IOException("Scenario " + scenario.id
                + " has an invalid summary.json file.", error);
        }

        String baselineId = requiredString(root, "baselineProfileId", scenario.id);
        int requiredRuns = integer(root, "requiredValidRepetitions");
        JsonArray profileItems = requiredArray(root, "profiles", scenario.id);
        JsonArray comparisonItems = requiredArray(root, "comparisons", scenario.id);
        Map<String, JsonObject> comparisons = new LinkedHashMap<String, JsonObject>();

        for (JsonElement item : comparisonItems)
        {
            if (item != null && item.isJsonObject())
            {
                JsonObject value = item.getAsJsonObject();
                String profileId = optionalString(value, "profileId");

                if (profileId != null)
                {
                    comparisons.put(profileId, value);
                }
            }
        }

        List<ProfileSummary> profiles = new ArrayList<ProfileSummary>();
        String baselineLabel = baselineId;

        for (JsonElement item : profileItems)
        {
            if (item == null || !item.isJsonObject())
            {
                throw new IOException("Scenario " + scenario.id
                    + " has an invalid profile entry.");
            }

            JsonObject profile = item.getAsJsonObject();
            String profileId = requiredString(profile, "profileId", scenario.id);
            String profileLabel = requiredString(profile, "profileLabel", scenario.id);
            JsonObject metrics = object(profile, "metrics");
            JsonObject comparison = comparisons.get(profileId);
            boolean baseline = baselineId.equals(profileId);
            ProfileSummary result = new ProfileSummary(profileId, profileLabel,
                integer(profile, "validRuns"), integer(profile, "invalidRuns"),
                baseline, bool(profile, "sufficient"), median(metrics, "framesPerSecond"),
                median(metrics, "onePercentLowFps"), median(metrics, "p99Millis"),
                baseline ? Double.valueOf(0.0D) : improvement(comparison,
                    "framesPerSecond"),
                baseline ? Double.valueOf(0.0D) : improvement(comparison,
                    "onePercentLowFps"),
                baseline ? Double.valueOf(0.0D) : improvement(comparison, "p99Millis"),
                baseline || (comparison != null && bool(comparison, "sufficient")));
            profiles.add(result);

            if (baseline)
            {
                baselineLabel = profileLabel;
            }
        }

        if (profiles.isEmpty())
        {
            throw new IOException("Scenario " + scenario.id + " has no profiles.");
        }

        return new ScenarioSummary(scenario.id, scenario.label,
            reportLink(outputDirectory, reportFile), baselineId, baselineLabel,
            requiredRuns, profiles);
    }

    private static String json(List<ScenarioSummary> scenarios)
    {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(1));
        JsonObject combined = new JsonObject();
        combined.addProperty("description",
            "Equal-weight geometric mean of mean-FPS ratios. This index is not FPS.");
        combined.addProperty("baseline", Double.valueOf(100.0D));
        combined.addProperty("totalScenarios", Integer.valueOf(scenarios.size()));
        JsonArray clientItems = new JsonArray();

        for (ClientIndex client : clientIndexes(scenarios))
        {
            JsonObject item = new JsonObject();
            item.addProperty("profileId", client.id);
            item.addProperty("profileLabel", client.label);
            item.addProperty("baseline", Boolean.valueOf(client.baseline));
            addNumber(item, "index", client.index);
            item.addProperty("completeScenarios", Integer.valueOf(client.completeScenarios));
            item.addProperty("totalScenarios", Integer.valueOf(scenarios.size()));
            item.addProperty("sufficient", Boolean.valueOf(client.sufficient));
            clientItems.add(item);
        }

        combined.add("clients", clientItems);
        root.add("combinedClientIndex", combined);
        JsonArray items = new JsonArray();

        for (ScenarioSummary scenario : scenarios)
        {
            JsonObject item = new JsonObject();
            item.addProperty("id", scenario.id);
            item.addProperty("label", scenario.label);
            item.addProperty("report", scenario.report);
            item.addProperty("baselineProfileId", scenario.baselineId);
            item.addProperty("baselineProfileLabel", scenario.baselineLabel);
            item.addProperty("requiredValidRuns", Integer.valueOf(scenario.requiredRuns));
            JsonArray profiles = new JsonArray();

            for (ProfileSummary profile : scenario.profiles)
            {
                JsonObject value = new JsonObject();
                value.addProperty("profileId", profile.id);
                value.addProperty("profileLabel", profile.label);
                value.addProperty("validRuns", Integer.valueOf(profile.validRuns));
                value.addProperty("invalidRuns", Integer.valueOf(profile.invalidRuns));
                value.addProperty("sufficient", Boolean.valueOf(profile.sufficient
                    && profile.pairedSufficient));
                JsonObject metrics = new JsonObject();
                addNumber(metrics, "meanFps", profile.meanFps);
                addNumber(metrics, "onePercentLowFps", profile.onePercentLowFps);
                addNumber(metrics, "p99Millis", profile.p99Millis);
                value.add("metrics", metrics);
                JsonObject changes = new JsonObject();
                addNumber(changes, "meanFps", profile.meanFpsChange);
                addNumber(changes, "onePercentLowFps", profile.onePercentLowFpsChange);
                addNumber(changes, "p99Millis", profile.p99Change);
                value.add("improvementPercentVsBaseline", changes);
                profiles.add(value);
            }

            item.add("profiles", profiles);
            items.add(item);
        }

        root.add("scenarios", items);
        return GSON.toJson(root) + '\n';
    }

    private static String html(List<ScenarioSummary> scenarios)
    {
        List<ClientIndex> indexes = clientIndexes(scenarios);
        StringBuilder output = new StringBuilder();
        output.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
            .append("<meta charset=\"utf-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
            .append("<title>Benchmark overview</title>\n<style>\n")
            .append(":root{color-scheme:dark;background:#10131a;color:#eef2f8;font-family:system-ui,sans-serif}")
            .append("body{max-width:1120px;margin:0 auto;padding:32px 20px 56px}")
            .append("h1{margin:0 0 8px}h2{margin:0;font-size:1.25rem}")
            .append("a{color:#8ec5ff}p{color:#bac4d3}.scenario{background:#181d27;border:1px solid #30394a;border-radius:10px;padding:20px;margin-top:20px}")
            .append(".meta{margin:6px 0 16px}.table{overflow-x:auto}table{width:100%;border-collapse:collapse;white-space:nowrap}")
            .append("th,td{text-align:right;padding:9px 10px;border-bottom:1px solid #30394a}th:first-child,td:first-child{text-align:left}")
            .append("th{color:#bac4d3;font-weight:600}.positive{color:#65d18b}.negative{color:#ff8b8b}.neutral{color:#bac4d3}")
            .append(".status-ok{color:#65d18b}.status-bad{color:#ffb86b}.detail{margin:14px 0 0}")
            .append("</style>\n</head>\n<body>\n<h1>Benchmark overview</h1>\n")
            .append("<p>Positive changes mean that the client performs better than the baseline.</p>\n")
            .append("<section class=\"scenario\">\n<h2>Combined client index</h2>\n")
            .append("<p class=\"meta\">This is an equal-weight index across scenarios, not FPS. The baseline is 100.</p>\n")
            .append("<div class=\"table\"><table>\n<thead><tr><th>Client</th>")
            .append("<th>Index</th><th>Complete scenarios</th><th>Status</th></tr></thead>\n<tbody>\n");

        for (ClientIndex client : indexes)
        {
            output.append("<tr><td>").append(htmlText(client.label)).append("</td><td>")
                .append(number(client.index)).append("</td><td>")
                .append(client.completeScenarios).append(" / ").append(scenarios.size())
                .append("</td><td class=\"")
                .append(client.sufficient ? "status-ok\">Sufficient"
                    : "status-bad\">Insufficient")
                .append("</td></tr>\n");
        }

        output.append("</tbody></table></div>\n</section>\n");

        for (ScenarioSummary scenario : scenarios)
        {
            output.append("<section class=\"scenario\">\n<h2><a href=\"")
                .append(htmlText(scenario.report)).append("\">")
                .append(htmlText(scenario.label)).append("</a></h2>\n<p class=\"meta\">Baseline: ")
                .append(htmlText(scenario.baselineLabel)).append(" &middot; Required valid runs: ")
                .append(scenario.requiredRuns).append("</p>\n<div class=\"table\"><table>\n")
                .append("<thead><tr><th>Client</th><th>Mean FPS</th><th>vs baseline</th>")
                .append("<th>1% low FPS</th><th>vs baseline</th><th>P99 (ms)</th>")
                .append("<th>vs baseline</th><th>Valid runs</th><th>Status</th></tr></thead>\n<tbody>\n");

            for (ProfileSummary profile : scenario.profiles)
            {
                boolean sufficient = profile.sufficient && profile.pairedSufficient;
                output.append("<tr><td>").append(htmlText(profile.label)).append("</td><td>")
                    .append(number(profile.meanFps)).append("</td><td>")
                    .append(change(profile.meanFpsChange)).append("</td><td>")
                    .append(number(profile.onePercentLowFps)).append("</td><td>")
                    .append(change(profile.onePercentLowFpsChange)).append("</td><td>")
                    .append(number(profile.p99Millis)).append("</td><td>")
                    .append(change(profile.p99Change)).append("</td><td>")
                    .append(profile.validRuns).append("</td><td class=\"")
                    .append(sufficient ? "status-ok\">Sufficient" : "status-bad\">Insufficient")
                    .append("</td></tr>\n");
            }

            output.append("</tbody></table></div>\n<p class=\"detail\"><a href=\"")
                .append(htmlText(scenario.report)).append("\">Open detailed report</a></p>\n")
                .append("</section>\n");
        }

        output.append("</body>\n</html>\n");
        return output.toString();
    }

    private static List<ClientIndex> clientIndexes(List<ScenarioSummary> scenarios)
    {
        List<ClientIndex> result = new ArrayList<ClientIndex>();
        ScenarioSummary first = scenarios.get(0);
        int baselineComplete = 0;

        for (ScenarioSummary scenario : scenarios)
        {
            ProfileSummary baseline = scenario.profile(scenario.baselineId);

            if (baseline != null && baseline.sufficient)
            {
                ++baselineComplete;
            }
        }

        result.add(new ClientIndex(first.baselineId, first.baselineLabel, true,
            Double.valueOf(100.0D), baselineComplete,
            baselineComplete == scenarios.size()));

        Map<String, String> candidateLabels = new LinkedHashMap<String, String>();

        for (ScenarioSummary scenario : scenarios)
        {
            for (ProfileSummary profile : scenario.profiles)
            {
                if (!profile.baseline && !candidateLabels.containsKey(profile.id))
                {
                    candidateLabels.put(profile.id, profile.label);
                }
            }
        }

        for (Map.Entry<String, String> candidate : candidateLabels.entrySet())
        {
            int complete = 0;
            double logarithm = 0.0D;

            for (ScenarioSummary scenario : scenarios)
            {
                ProfileSummary profile = scenario.profile(candidate.getKey());

                if (profile == null || !profile.sufficient || !profile.pairedSufficient
                    || profile.meanFpsChange == null)
                {
                    continue;
                }

                double ratio = 1.0D + profile.meanFpsChange.doubleValue() / 100.0D;

                if (ratio <= 0.0D || Double.isNaN(ratio) || Double.isInfinite(ratio))
                {
                    continue;
                }

                logarithm += Math.log(ratio);
                ++complete;
            }

            Double index = complete == 0 ? null
                : Double.valueOf(100.0D * Math.exp(logarithm / complete));
            result.add(new ClientIndex(candidate.getKey(), candidate.getValue(), false,
                index, complete, complete == scenarios.size()));
        }

        return result;
    }

    private static String reportLink(Path outputDirectory, Path reportFile)
        throws IOException
    {
        Path output = outputDirectory.toAbsolutePath().normalize();
        Path report = reportFile.toAbsolutePath().normalize();
        String path;

        try
        {
            path = output.relativize(report).toString().replace('\\', '/');
        }
        catch (IllegalArgumentException differentRoots)
        {
            return report.toUri().toASCIIString();
        }

        try
        {
            return new URI(null, null, path, null).toASCIIString();
        }
        catch (URISyntaxException error)
        {
            throw new IOException("Cannot create a scenario report link.", error);
        }
    }

    private static String requiredString(JsonObject object, String name, String scenarioId)
        throws IOException
    {
        String value = optionalString(object, name);

        if (value == null || value.trim().isEmpty())
        {
            throw new IOException("Scenario " + scenarioId + " has no " + name + '.');
        }

        return value;
    }

    private static String optionalString(JsonObject object, String name)
    {
        JsonElement value = object == null ? null : object.get(name);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive()
            ? null : value.getAsString();
    }

    private static JsonArray requiredArray(JsonObject object, String name, String scenarioId)
        throws IOException
    {
        JsonElement value = object.get(name);

        if (value == null || !value.isJsonArray())
        {
            throw new IOException("Scenario " + scenarioId + " has no " + name + " array.");
        }

        return value.getAsJsonArray();
    }

    private static JsonObject object(JsonObject parent, String name)
    {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static Double median(JsonObject metrics, String name)
    {
        return finite(object(metrics, name), "median");
    }

    private static Double improvement(JsonObject comparison, String name)
    {
        return finite(object(object(comparison, "medianImprovementPercent"), name),
            "median");
    }

    private static Double finite(JsonObject object, String name)
    {
        JsonElement value = object == null ? null : object.get(name);

        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
        {
            return null;
        }

        try
        {
            double number = value.getAsDouble();
            return Double.isNaN(number) || Double.isInfinite(number)
                ? null : Double.valueOf(number);
        }
        catch (RuntimeException error)
        {
            return null;
        }
    }

    private static int integer(JsonObject object, String name)
    {
        JsonElement value = object == null ? null : object.get(name);

        try
        {
            return value == null || value.isJsonNull() ? 0 : value.getAsInt();
        }
        catch (RuntimeException error)
        {
            return 0;
        }
    }

    private static boolean bool(JsonObject object, String name)
    {
        JsonElement value = object == null ? null : object.get(name);

        try
        {
            return value != null && !value.isJsonNull() && value.getAsBoolean();
        }
        catch (RuntimeException error)
        {
            return false;
        }
    }

    private static void addNumber(JsonObject object, String name, Double value)
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

    private static String number(Double value)
    {
        return value == null ? "n/a" : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String change(Double value)
    {
        if (value == null)
        {
            return "<span class=\"neutral\">n/a</span>";
        }

        String type = value.doubleValue() > 0.0000001D ? "positive"
            : value.doubleValue() < -0.0000001D ? "negative" : "neutral";
        return "<span class=\"" + type + "\">"
            + String.format(Locale.ROOT, "%+.2f%%", value) + "</span>";
    }

    private static String htmlText(String value)
    {
        if (value == null)
        {
            return "";
        }

        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static void writeUtf8(Path output, String value) throws IOException
    {
        Path parent = output.toAbsolutePath().getParent();

        if (parent == null)
        {
            throw new IOException("Output file has no parent: " + output);
        }

        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, output.getFileName().toString(), ".tmp");

        try
        {
            Files.write(temporary, value.getBytes(StandardCharsets.UTF_8));

            try
            {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException unsupported)
            {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    /** Identifies one completed scenario report. */
    public static final class Scenario
    {
        private final String id;
        private final String label;
        private final Path directory;

        public Scenario(String id, String label, Path directory)
        {
            if (id == null || id.trim().isEmpty())
            {
                throw new IllegalArgumentException("Scenario ID is required.");
            }
            if (label == null || label.trim().isEmpty())
            {
                throw new IllegalArgumentException("Scenario label is required.");
            }
            if (directory == null)
            {
                throw new IllegalArgumentException("Scenario directory is required.");
            }

            this.id = id;
            this.label = label;
            this.directory = directory;
        }

        public String getId()
        {
            return id;
        }

        public String getLabel()
        {
            return label;
        }

        public Path getDirectory()
        {
            return directory;
        }
    }

    private static final class ScenarioSummary
    {
        private final String id;
        private final String label;
        private final String report;
        private final String baselineId;
        private final String baselineLabel;
        private final int requiredRuns;
        private final List<ProfileSummary> profiles;

        private ScenarioSummary(String id, String label, String report, String baselineId,
            String baselineLabel, int requiredRuns, List<ProfileSummary> profiles)
        {
            this.id = id;
            this.label = label;
            this.report = report;
            this.baselineId = baselineId;
            this.baselineLabel = baselineLabel;
            this.requiredRuns = requiredRuns;
            this.profiles = profiles;
        }

        private ProfileSummary profile(String profileId)
        {
            for (ProfileSummary profile : profiles)
            {
                if (profile.id.equals(profileId))
                {
                    return profile;
                }
            }

            return null;
        }
    }

    private static final class ProfileSummary
    {
        private final String id;
        private final String label;
        private final int validRuns;
        private final int invalidRuns;
        private final boolean baseline;
        private final boolean sufficient;
        private final Double meanFps;
        private final Double onePercentLowFps;
        private final Double p99Millis;
        private final Double meanFpsChange;
        private final Double onePercentLowFpsChange;
        private final Double p99Change;
        private final boolean pairedSufficient;

        private ProfileSummary(String id, String label, int validRuns, int invalidRuns,
            boolean baseline, boolean sufficient, Double meanFps, Double onePercentLowFps,
            Double p99Millis, Double meanFpsChange, Double onePercentLowFpsChange,
            Double p99Change, boolean pairedSufficient)
        {
            this.id = id;
            this.label = label;
            this.validRuns = validRuns;
            this.invalidRuns = invalidRuns;
            this.baseline = baseline;
            this.sufficient = sufficient;
            this.meanFps = meanFps;
            this.onePercentLowFps = onePercentLowFps;
            this.p99Millis = p99Millis;
            this.meanFpsChange = meanFpsChange;
            this.onePercentLowFpsChange = onePercentLowFpsChange;
            this.p99Change = p99Change;
            this.pairedSufficient = pairedSufficient;
        }
    }

    private static final class ClientIndex
    {
        private final String id;
        private final String label;
        private final boolean baseline;
        private final Double index;
        private final int completeScenarios;
        private final boolean sufficient;

        private ClientIndex(String id, String label, boolean baseline, Double index,
            int completeScenarios, boolean sufficient)
        {
            this.id = id;
            this.label = label;
            this.baseline = baseline;
            this.index = index;
            this.completeScenarios = completeScenarios;
            this.sufficient = sufficient;
        }
    }
}
