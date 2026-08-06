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

    private static void write(String content) throws Exception
    {
        FileOutputStream out = new FileOutputStream(new File(Launch.minecraftHome, "vertex.properties"));
        out.write(content.getBytes("UTF-8"));
        out.close();
    }
}
