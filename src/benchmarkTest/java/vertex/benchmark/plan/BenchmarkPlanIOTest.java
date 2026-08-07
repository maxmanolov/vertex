package vertex.benchmark.plan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BenchmarkPlanIOTest
{
    private static final String VALID_PLAN =
        "{\n"
        + "  \"schemaVersion\": 1,\n"
        + "  \"suiteId\": \"minecraft-1.7.10\",\n"
        + "  \"baselineProfile\": \"vanilla\",\n"
        + "  \"repetitions\": 3,\n"
        + "  \"warmupSeconds\": 15,\n"
        + "  \"captureSeconds\": 60,\n"
        + "  \"cooldownSeconds\": 5,\n"
        + "  \"seed\": 8675309,\n"
        + "  \"resultDirectory\": \"results/minecraft-1.7.10\",\n"
        + "  \"collector\": {\"type\": \"presentmon\", \"metric\": \"presented\"},\n"
        + "  \"profiles\": [\n"
        + "    {\n"
        + "      \"id\": \"vanilla\",\n"
        + "      \"label\": \"1.7.10 Vanilla\",\n"
        + "      \"launchMode\": \"manual\",\n"
        + "      \"processName\": \"javaw.exe\",\n"
        + "      \"command\": [],\n"
        + "      \"settingsFiles\": [\"options.txt\"],\n"
        + "      \"instructions\": [\"Start the vanilla profile.\"],\n"
        + "      \"metadata\": {\"client\": \"vanilla\"}\n"
        + "    },\n"
        + "    {\n"
        + "      \"id\": \"lunar\",\n"
        + "      \"label\": \"Lunar Client\",\n"
        + "      \"launchMode\": \"command\",\n"
        + "      \"processName\": \"javaw.exe\",\n"
        + "      \"command\": [\"C:/Program Files/Lunar Client/Lunar Client.exe\", \"--profile\", \"1.7.10 test\"],\n"
        + "      \"settingsFiles\": [],\n"
        + "      \"instructions\": [],\n"
        + "      \"metadata\": {\"client\": \"lunar\"}\n"
        + "    }\n"
        + "  ]\n"
        + "}\n";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsAValidExample() throws Exception
    {
        BenchmarkPlan plan = load(VALID_PLAN);

        assertEquals(1, plan.getSchemaVersion());
        assertEquals("vanilla", plan.getBaselineProfile());
        assertEquals(2, plan.getProfiles().size());
        assertEquals(CollectorPlan.Type.PRESENTMON, plan.getCollector().getType());
    }

    @Test
    public void rejectsDuplicateProfileIds() throws Exception
    {
        String json = VALID_PLAN.replace("\"id\": \"lunar\"", "\"id\": \"vanilla\"");
        assertInvalid(json, "not unique");
        assertInvalid(json, "exactly one");
    }

    @Test
    public void enforcesNumericLimits() throws Exception
    {
        assertInvalid(VALID_PLAN.replace("\"repetitions\": 3", "\"repetitions\": 0"),
            "repetitions");
        assertInvalid(VALID_PLAN.replace("\"repetitions\": 3", "\"repetitions\": 11"),
            "repetitions");
        assertInvalid(VALID_PLAN.replace("\"warmupSeconds\": 15", "\"warmupSeconds\": -1"),
            "warmupSeconds");
        assertInvalid(VALID_PLAN.replace("\"warmupSeconds\": 15", "\"warmupSeconds\": 1801"),
            "warmupSeconds");
        assertInvalid(VALID_PLAN.replace("\"captureSeconds\": 60", "\"captureSeconds\": 4"),
            "captureSeconds");
        assertInvalid(VALID_PLAN.replace("\"captureSeconds\": 60", "\"captureSeconds\": 1801"),
            "captureSeconds");
        assertInvalid(VALID_PLAN.replace("\"cooldownSeconds\": 5", "\"cooldownSeconds\": -1"),
            "cooldownSeconds");
        assertInvalid(VALID_PLAN.replace("\"cooldownSeconds\": 5", "\"cooldownSeconds\": 1801"),
            "cooldownSeconds");
    }

    @Test
    public void rejectsAutoMetricForASuite() throws Exception
    {
        assertInvalid(VALID_PLAN.replace("\"metric\": \"presented\"",
            "\"metric\": \"auto\""), "collector.metric");
    }

    @Test
    public void requiresAProcessNameForImportSuites() throws Exception
    {
        String json = VALID_PLAN
            .replace("\"type\": \"presentmon\"", "\"type\": \"import\"")
            .replace("      \"processName\": \"javaw.exe\",\n", "");
        assertInvalid(json, "processName");
    }

    @Test
    public void rejectsCredentialMetadataKeys() throws Exception
    {
        assertInvalid(VALID_PLAN.replace("\"client\": \"vanilla\"",
            "\"accessToken\": \"value\""), "must not name a credential");
        assertInvalid(VALID_PLAN.replace("\"client\": \"vanilla\"",
            "\"session-id\": \"value\""), "must not name a credential");
    }

    @Test
    public void rejectsUnsafeIds() throws Exception
    {
        assertInvalid(VALID_PLAN.replace("minecraft-1.7.10", "../outside"), "suiteId");
        assertInvalid(VALID_PLAN.replace("\"id\": \"lunar\"", "\"id\": \"lunar/client\""),
            "profiles[1].id");
    }

    @Test
    public void rejectsAnAbsentBaseline() throws Exception
    {
        String json = VALID_PLAN.replace("\"baselineProfile\": \"vanilla\",\n", "");
        assertInvalid(json, "baselineProfile");
    }

    @Test
    public void keepsArgumentsWithSpacesAsOneItem() throws Exception
    {
        BenchmarkPlan plan = load(VALID_PLAN);

        assertEquals(3, plan.getProfiles().get(1).getCommand().size());
        assertEquals("C:/Program Files/Lunar Client/Lunar Client.exe",
            plan.getProfiles().get(1).getCommand().get(0));
        assertEquals("1.7.10 test", plan.getProfiles().get(1).getCommand().get(2));
    }

    @Test
    public void toleratesUnknownFields() throws Exception
    {
        String json = VALID_PLAN
            .replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"futureRoot\": true,")
            .replace("\"metric\": \"presented\"",
                "\"metric\": \"presented\", \"futureCollector\": 4")
            .replace("\"label\": \"Lunar Client\",",
                "\"label\": \"Lunar Client\", \"futureProfile\": {},");

        assertEquals(2, load(json).getProfiles().size());
    }

    @Test
    public void rejectsACommandString() throws Exception
    {
        String json = VALID_PLAN.replace(
            "[\"C:/Program Files/Lunar Client/Lunar Client.exe\", \"--profile\", \"1.7.10 test\"]",
            "\"C:/Program Files/Lunar Client/Lunar Client.exe --profile 1.7.10\"");
        assertInvalid(json, "argument array");
    }

    @Test
    public void writesAndLoadsUtf8() throws Exception
    {
        BenchmarkPlan plan = load(VALID_PLAN);
        plan.getProfiles().get(1).setLabel("Lunární Client");
        Path output = temporaryFolder.newFile("round-trip.json").toPath();

        BenchmarkPlanIO.write(output, plan);
        BenchmarkPlan restored = BenchmarkPlanIO.load(output);

        assertEquals("Lunární Client", restored.getProfiles().get(1).getLabel());
    }

    @Test
    public void resolvesOnlyCallerSuppliedNonCredentialVariables() throws Exception
    {
        String json = VALID_PLAN.replace("results/minecraft-1.7.10", "${BENCH_ROOT}/results");
        BenchmarkPlan raw = load(json);
        assertEquals("${BENCH_ROOT}/results", raw.getResultDirectory());

        Map<String, String> variables = new HashMap<String, String>();
        variables.put("BENCH_ROOT", "D:/Bench Data");
        BenchmarkPlan resolved = BenchmarkPlanIO.resolveEnvironment(raw, variables);

        assertEquals("D:/Bench Data/results", resolved.getResultDirectory());
        assertEquals("${BENCH_ROOT}/results", raw.getResultDirectory());

        BenchmarkPlan credentialPlan = load(json.replace("BENCH_ROOT", "API_TOKEN"));
        try
        {
            BenchmarkPlanIO.resolveEnvironment(credentialPlan,
                Collections.singletonMap("API_TOKEN", "not-read"));
            fail("credential variable was accepted");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("not permitted"));
        }

        BenchmarkPlan sessionPlan = load(json.replace("BENCH_ROOT", "MC_SESSION"));
        try
        {
            BenchmarkPlanIO.resolveEnvironment(sessionPlan,
                Collections.singletonMap("MC_SESSION", "not-read"));
            fail("session variable was accepted");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("not permitted"));
        }
    }

    private BenchmarkPlan load(String json) throws Exception
    {
        Path path = temporaryFolder.newFile().toPath();
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
        return BenchmarkPlanIO.load(path);
    }

    private void assertInvalid(String json, String messagePart) throws Exception
    {
        try
        {
            load(json);
            fail("invalid plan was accepted");
        }
        catch (PlanValidationException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }
}
