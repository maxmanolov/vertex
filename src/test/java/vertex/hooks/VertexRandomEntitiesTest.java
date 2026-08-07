package vertex.hooks;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VertexRandomEntitiesTest
{
    public static final class FakeLocation
    {
        final String domain;
        final String path;

        public FakeLocation(String domain, String path)
        {
            this.domain = domain;
            this.path = path;
        }

        public String a()
        {
            return this.path;
        }

        public String b()
        {
            return this.domain;
        }
    }

    public static final class FakeEntity
    {
        final int id;

        FakeEntity(int id)
        {
            this.id = id;
        }

        public int y()
        {
            return this.id;
        }
    }

    public static final class FakeRender
    {
        final FakeLocation base;
        FakeLocation bound;

        FakeRender(FakeLocation base)
        {
            this.base = base;
        }

        protected FakeLocation a(FakeEntity entity)
        {
            return this.base;
        }

        protected void a(FakeLocation location)
        {
            this.bound = location;
        }
    }

    public static final class FakeManager
    {
        final Set<String> resources = new HashSet<String>();

        public Object a(FakeLocation location)
        {
            if (!this.resources.contains(location.domain + ":" + location.path))
            {
                throw new IllegalArgumentException("missing resource");
            }

            return new Object();
        }
    }

    @Before
    public void setUp() throws Exception
    {
        resetRandomEntities();

        FakeManager manager = new FakeManager();
        manager.resources.add("alpha:textures/entity/cow2.png");
        manager.resources.add("beta:textures/entity/cow2.png");
        set(VertexPackLoader.class, "getResource", FakeManager.class.getMethod("a", FakeLocation.class));
        set(VertexPackLoader.class, "lastManager", manager);
    }

    @After
    public void tearDown() throws Exception
    {
        resetRandomEntities();
        set(VertexPackLoader.class, "getResource", null);
        set(VertexPackLoader.class, "lastManager", null);
    }

    @Test
    public void variantPoolsAreSeparateForEqualPathsInDifferentDomains()
    {
        FakeEntity entity = new FakeEntity(1);
        FakeRender alpha = new FakeRender(new FakeLocation("alpha", "textures/entity/cow.png"));
        VertexRandomEntities.interceptBind(alpha, entity);

        FakeRender beta = new FakeRender(new FakeLocation("beta", "textures/entity/cow.png"));
        assertTrue(VertexRandomEntities.interceptBind(beta, entity));
        assertEquals("beta", beta.bound.domain);
    }

    private static void resetRandomEntities() throws Exception
    {
        variants().clear();
        set(VertexRandomEntities.class, "disabled", Boolean.FALSE);
        set(VertexRandomEntities.class, "getEntityTexture", null);
        set(VertexRandomEntities.class, "bindTexture", null);
        set(VertexRandomEntities.class, "getResourcePath", null);
        set(VertexRandomEntities.class, "getResourceDomain", null);
        set(VertexRandomEntities.class, "getEntityId", null);
        set(VertexRandomEntities.class, "locationCtor", null);
        VertexRandomEntities.applied = 0L;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object[]> variants() throws Exception
    {
        Field field = VertexRandomEntities.class.getDeclaredField("variants");
        field.setAccessible(true);
        return (Map<String, Object[]>)field.get(null);
    }

    private static void set(Class<?> owner, String name, Object value) throws Exception
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
