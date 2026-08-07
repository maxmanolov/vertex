package vertex.hooks;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Properties;
import net.minecraft.launchwrapper.Launch;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VertexConfigDefaultsTest
{
    @Before
    public void isolate() throws Exception
    {
        File dir = File.createTempFile("vertex-cfg", "");
        dir.delete();
        dir.mkdirs();
        Launch.minecraftHome = dir;

        for (String[] entry : new String[][] {{"file", null}, {"lastCheck", "0"}, {"lastModified", "-1"}})
        {
            Field field = VertexConfig.class.getDeclaredField(entry[0]);
            field.setAccessible(true);

            if (entry[1] == null)
            {
                field.set(null, null);
            }
            else
            {
                field.setLong(null, Long.parseLong(entry[1]));
            }
        }

        Field values = VertexConfig.class.getDeclaredField("values");
        values.setAccessible(true);
        ((Properties)values.get(null)).clear();
    }

    @Test
    public void missingKeysResolveToDeclaredDefaults() throws Exception
    {
        // A file that predates newer keys: only one legacy key present.
        write("sky=true\n");
        assertFalse("default-false key must stay off when absent", VertexConfig.enabled("betterGrass"));
        assertFalse(VertexConfig.enabled("diagnostics"));
        assertTrue("default-true key stays on when absent", VertexConfig.enabled("fog"));
    }

    @Test
    public void malformedFileFallsBackToDeclaredDefaults() throws Exception
    {
        // The reproduction from the report: an invalid unicode escape kills the parse.
        write("betterGrass=\\uZZZZ\n");
        assertFalse(VertexConfig.enabled("betterGrass"));
        assertTrue(VertexConfig.enabled("fog"));
    }

    @Test
    public void malformedReloadDoesNotKeepPreviousValues() throws Exception
    {
        write("betterGrass=true\nfog=false\n");
        assertTrue(VertexConfig.enabled("betterGrass"));
        assertFalse(VertexConfig.enabled("fog"));

        write("betterGrass=\\uZZZZ\n");
        assertFalse("malformed reload must clear the previous enabled value", VertexConfig.enabled("betterGrass"));
        assertTrue("malformed reload must clear the previous disabled value", VertexConfig.enabled("fog"));

        write("betterGrass=true\nfog=false\n");
        assertTrue("a later valid file must load", VertexConfig.enabled("betterGrass"));
        assertFalse(VertexConfig.enabled("fog"));
    }

    @Test
    public void undeclaredKeysNeverEnable() throws Exception
    {
        write("");
        assertFalse(VertexConfig.enabled("betterGrasss"));
    }

    @Test
    public void explicitValuesStillWin() throws Exception
    {
        write("betterGrass=true\nfog=false\n");
        assertTrue(VertexConfig.enabled("betterGrass"));
        assertFalse(VertexConfig.enabled("fog"));
    }

    @Test
    public void invalidBooleanValuesUseDeclaredDefaults() throws Exception
    {
        write("betterGrass=disabled\nfog=disabled\n");
        assertFalse(VertexConfig.enabled("betterGrass"));
        assertTrue(VertexConfig.enabled("fog"));
    }

    @Test
    public void booleanParsingTable() throws Exception
    {
        // Values that must parse as their literal meaning, whatever the key's default.
        String[] trueSpellings = {"true", "TRUE", "True", " true ", "\ttrue"};
        String[] falseSpellings = {"false", "FALSE", "FaLsE", " false ", "false\t"};
        // Everything else must fall back to the key's declared default - never literal
        // true, or a typo could switch on a default-off feature (#85).
        String[] invalid = {"", " ", "yes", "no", "on", "off", "1", "0", "enabled",
            "disabled", "tru", "fals", "truefalse", " true", "t rue", "null"};

        for (String spelling : trueSpellings)
        {
            write("betterGrass=" + spelling + "\nfog=" + spelling + "\n");
            assertTrue("'" + spelling + "' must enable a default-off key", VertexConfig.enabled("betterGrass"));
            assertTrue("'" + spelling + "' must keep a default-on key on", VertexConfig.enabled("fog"));
        }

        for (String spelling : falseSpellings)
        {
            write("betterGrass=" + spelling + "\nfog=" + spelling + "\n");
            assertFalse("'" + spelling + "' must disable a default-off key", VertexConfig.enabled("betterGrass"));
            assertFalse("'" + spelling + "' must disable a default-on key", VertexConfig.enabled("fog"));
        }

        for (String junk : invalid)
        {
            write("betterGrass=" + junk + "\ndiagnostics=" + junk + "\nfog=" + junk + "\n");
            assertFalse("'" + junk + "' must not enable default-off betterGrass", VertexConfig.enabled("betterGrass"));
            assertFalse("'" + junk + "' must not enable default-off diagnostics", VertexConfig.enabled("diagnostics"));
            assertTrue("'" + junk + "' must leave default-on fog at its default", VertexConfig.enabled("fog"));
        }
    }

    private static void write(String content) throws Exception
    {
        FileOutputStream out = new FileOutputStream(new File(Launch.minecraftHome, "vertex.properties"));
        out.write(content.getBytes("UTF-8"));
        out.close();
        // Defeat the once-per-second refresh throttle and mtime granularity so every
        // write in a loop is observed by the very next enabled() call.
        Field lastCheck = VertexConfig.class.getDeclaredField("lastCheck");
        lastCheck.setAccessible(true);
        lastCheck.setLong(null, 0L);
        Field lastModified = VertexConfig.class.getDeclaredField("lastModified");
        lastModified.setAccessible(true);
        lastModified.setLong(null, -1L);
    }
}
