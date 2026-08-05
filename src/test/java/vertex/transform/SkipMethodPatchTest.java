package vertex.transform;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Properties;
import net.minecraft.launchwrapper.Launch;
import org.junit.Before;
import org.junit.Test;
import vertex.TransformerHarness;
import vertex.TransformerHarness.ByteLoader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkipMethodPatchTest
{
    @Before
    public void isolateConfig() throws Exception
    {
        File dir = File.createTempFile("vertex-test", "");
        dir.delete();
        dir.mkdirs();
        Launch.minecraftHome = dir;
        resetConfig();
    }

    @Test
    public void enabledKeyLeavesTheMethodRunning() throws Exception
    {
        Class<?> cls = patchAndLoad("skip0");
        cls.getMethod("run").invoke(cls.newInstance());
        assertTrue(cls.getField("ran").getBoolean(null));
    }

    @Test
    public void disabledKeySkipsTheBody() throws Exception
    {
        Properties props = new Properties();
        props.setProperty("sky", "false");
        FileOutputStream out = new FileOutputStream(new File(Launch.minecraftHome, "vertex.properties"));
        props.store(out, null);
        out.close();
        Class<?> cls = patchAndLoad("skip1");
        cls.getMethod("run").invoke(cls.newInstance());
        assertFalse(cls.getField("ran").getBoolean(null));
    }

    @Test(expected = IllegalStateException.class)
    public void refusesNonVoidTargets()
    {
        SkipMethodPatch.apply(TransformerHarness.voidMethodClass("skip2", "run", "()V"),
            new SkipMethodPatch.Target[] {new SkipMethodPatch.Target("run", "()I", "sky")});
    }

    private static Class<?> patchAndLoad(String name) throws Exception
    {
        byte[] bytes = TransformerHarness.voidMethodClass(name, "run", "()V");
        bytes = SkipMethodPatch.apply(bytes, new SkipMethodPatch.Target[] {new SkipMethodPatch.Target("run", "()V", "sky")});
        return new ByteLoader().add(name, bytes).loadClass(name);
    }

    private static void resetConfig() throws Exception
    {
        Class<?> config = Class.forName("vertex.hooks.VertexConfig");

        for (String[] entry : new String[][] {{"file", null}, {"lastCheck", "0"}, {"lastModified", "-1"}})
        {
            Field field = config.getDeclaredField(entry[0]);
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

        Field values = config.getDeclaredField("values");
        values.setAccessible(true);
        ((Properties)values.get(null)).clear();
    }
}
