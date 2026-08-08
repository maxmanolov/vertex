package vertex.hooks;

import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VertexStressDriverTest
{
    public static final class FakeSettings
    {
        public int c;
    }

    public static final class FakeMinecraft
    {
        public final FakeSettings u = new FakeSettings();
    }

    private Object savedGameSettings;
    private Object savedRenderDistance;
    private int savedSnapshot;
    private boolean savedSnapshotTaken;
    private boolean savedDisabled;

    @Before
    public void saveState() throws Exception
    {
        savedGameSettings = get("gameSettings");
        savedRenderDistance = get("renderDistance");
        savedSnapshot = ((Integer)get("savedRenderDistance")).intValue();
        savedSnapshotTaken = ((Boolean)get("renderDistanceSaved")).booleanValue();
        savedDisabled = ((Boolean)get("disabled")).booleanValue();
    }

    @After
    public void restoreState() throws Exception
    {
        set("gameSettings", savedGameSettings);
        set("renderDistance", savedRenderDistance);
        set("savedRenderDistance", Integer.valueOf(savedSnapshot));
        set("renderDistanceSaved", Boolean.valueOf(savedSnapshotTaken));
        set("disabled", Boolean.valueOf(savedDisabled));
    }

    @Test
    public void failureRestoresRenderDistanceExactlyOnce() throws Exception
    {
        FakeMinecraft minecraft = new FakeMinecraft();
        minecraft.u.c = 4;
        set("gameSettings", FakeMinecraft.class.getField("u"));
        set("renderDistance", FakeSettings.class.getField("c"));
        set("savedRenderDistance", Integer.valueOf(12));
        set("renderDistanceSaved", Boolean.TRUE);
        set("disabled", Boolean.FALSE);

        VertexStressDriver.disableAfterFailure(minecraft,
            new IllegalStateException("injected failure"));

        assertEquals(12, minecraft.u.c);
        assertFalse(((Boolean)get("renderDistanceSaved")).booleanValue());
        assertTrue(((Boolean)get("disabled")).booleanValue());

        minecraft.u.c = 8;
        VertexStressDriver.disableAfterFailure(minecraft,
            new IllegalStateException("second failure"));
        assertEquals("idempotent cleanup must not overwrite a later setting", 8,
            minecraft.u.c);
    }

    private static Object get(String name) throws Exception
    {
        Field field = VertexStressDriver.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void set(String name, Object value) throws Exception
    {
        Field field = VertexStressDriver.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
