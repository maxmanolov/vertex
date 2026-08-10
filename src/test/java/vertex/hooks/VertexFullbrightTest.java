package vertex.hooks;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Properties;
import net.minecraft.launchwrapper.Launch;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VertexFullbrightTest
{
    public static final class FailingRenderGlobal
    {
        public void a()
        {
            throw new IllegalStateException("reload failed");
        }
    }

    public static final class FakeMinecraft
    {
        private final Object f = new Object();
        private final FailingRenderGlobal g = new FailingRenderGlobal();
    }

    private File configDirectory;

    @Before
    public void resetState() throws Exception
    {
        configDirectory = File.createTempFile("vertex-fullbright", "");
        configDirectory.delete();
        configDirectory.mkdirs();
        Launch.minecraftHome = configDirectory;
        setConfig("file", null);
        setConfigLong("lastCheck", 0L);
        setConfigLong("lastModified", -1L);
        Field values = VertexConfig.class.getDeclaredField("values");
        values.setAccessible(true);
        ((Properties)values.get(null)).clear();

        setFullbright("active", Boolean.FALSE);
        setFullbright("reloadDisabled", Boolean.FALSE);
        setFullbright("resolved", Boolean.FALSE);
        setFullbright("theWorld", null);
        setFullbright("renderGlobal", null);
        setFullbright("loadRenderers", null);
    }

    @Test
    public void reloadFailureDoesNotFreezeLaterToggles() throws Exception
    {
        writeConfig(true);
        FakeMinecraft minecraft = new FakeMinecraft();

        VertexFullbright.tick(minecraft);
        assertTrue(VertexFullbright.fullbright());

        writeConfig(false);
        VertexFullbright.tick(minecraft);

        assertFalse("the feature state must keep following the hot-reloaded config",
            VertexFullbright.fullbright());
        assertFalse("light remarks must resume when the user turns fullbright off",
            VertexFullbright.interceptLightRemark(minecraft.g));
    }

    @Test
    public void entityBrightnessOnlyChangesWhileFullbrightIsActive() throws Exception
    {
        int vanilla = 0x700030;
        assertEquals(vanilla, VertexFullbright.adjustEntityBrightness(vanilla));

        setFullbright("active", Boolean.TRUE);
        assertEquals(VertexFullbright.FULLBRIGHT_PACKED,
            VertexFullbright.adjustEntityBrightness(vanilla));
    }

    private void writeConfig(boolean enabled) throws Exception
    {
        FileOutputStream output = new FileOutputStream(
            new File(configDirectory, "vertex.properties"));
        output.write(("fullbright=" + enabled + "\n").getBytes("UTF-8"));
        output.close();
        setConfigLong("lastCheck", 0L);
        setConfigLong("lastModified", -1L);
    }

    private static void setConfig(String name, Object value) throws Exception
    {
        Field field = VertexConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setConfigLong(String name, long value) throws Exception
    {
        Field field = VertexConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(null, value);
    }

    private static void setFullbright(String name, Object value) throws Exception
    {
        Field field = VertexFullbright.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
