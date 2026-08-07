package vertex.hooks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vertex.colors.ColorMap;
import vertex.colors.ColorProperties;
import vertex.sky.SkyLayer;
import vertex.variants.NaturalProperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VertexPackLoaderTest
{
    public static final class FakeLocation
    {
        final String path;

        public FakeLocation(String path)
        {
            this.path = path;
        }
    }

    public static final class FakeResource
    {
        final byte[] content;

        FakeResource(String content)
        {
            this.content = content.getBytes(StandardCharsets.ISO_8859_1);
        }

        public InputStream b()
        {
            return new ByteArrayInputStream(this.content);
        }
    }

    public static final class FakeManager
    {
        final Map<String, FakeResource> resources = new HashMap<String, FakeResource>();

        public FakeResource a(FakeLocation location)
        {
            FakeResource resource = this.resources.get(location.path);

            if (resource == null)
            {
                throw new IllegalArgumentException("missing " + location.path);
            }

            return resource;
        }
    }

    @Before
    public void setUp() throws Exception
    {
        set("locationCtor", FakeLocation.class.getConstructor(String.class));
        set("getResource", FakeManager.class.getMethod("a", FakeLocation.class));
        Properties oldColors = new Properties();
        oldColors.setProperty("fog.nether", "010203");
        int[] pixels = new int[ColorMap.SIZE * ColorMap.SIZE];
        Properties oldNatural = new Properties();
        oldNatural.setProperty("stone", "2F");
        set("state", new VertexPackLoader.PackState(
            new ColorProperties(oldColors), new ColorMap(pixels), new ColorMap(pixels),
            new NaturalProperties(oldNatural), java.util.Collections.singletonList(
                SkyLayer.parse(new Properties(), "old-sky.png"))));
    }

    @After
    public void tearDown() throws Exception
    {
        set("state", new VertexPackLoader.PackState(null, null, null, null,
            java.util.Collections.<SkyLayer>emptyList()));
        set("locationCtor", null);
        set("getResource", null);
        set("lastManager", null);
    }

    @Test
    public void malformedLateResourceCannotLeaveMixedPackState()
    {
        assertNotNull(VertexPackLoader.colorProperties());
        assertNotNull(VertexPackLoader.naturalProperties());
        assertEquals(1, VertexPackLoader.skyLayers().size());
        FakeManager manager = new FakeManager();
        manager.resources.put("mcpatcher/color.properties", new FakeResource("fog.nether=112233\n"));
        manager.resources.put("mcpatcher/natural.properties", new FakeResource("stone=\\uZZZZ\n"));
        VertexPackLoader.reload(manager);

        assertNull("new colors must not publish before the full reload succeeds", VertexPackLoader.colorProperties());
        assertNull(VertexPackLoader.grassMap());
        assertNull(VertexPackLoader.foliageMap());
        assertNull("old natural rules must not survive the failed reload", VertexPackLoader.naturalProperties());
        assertTrue(VertexPackLoader.skyLayers().isEmpty());
    }

    @Test
    public void successfulReloadPublishesOneCompleteState()
    {
        FakeManager manager = new FakeManager();
        manager.resources.put("mcpatcher/color.properties", new FakeResource("fog.nether=112233\n"));
        manager.resources.put("mcpatcher/natural.properties", new FakeResource("stone=2F\n"));
        manager.resources.put("mcpatcher/sky/world0/sky1.properties", new FakeResource("source=new-sky.png\n"));
        VertexPackLoader.reload(manager);

        assertEquals(0x112233, VertexPackLoader.colorProperties().get("fog.nether", 0));
        assertNull(VertexPackLoader.grassMap());
        assertNull(VertexPackLoader.foliageMap());
        assertNotNull(VertexPackLoader.naturalProperties().spec("stone"));
        assertEquals(1, VertexPackLoader.skyLayers().size());
        assertEquals("new-sky.png", VertexPackLoader.skyLayers().get(0).source);
    }

    private static void set(String name, Object value) throws Exception
    {
        Field field = VertexPackLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
