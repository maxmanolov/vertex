package vertex.hooks;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Comparator;
import org.junit.Test;
import vertex.api.ActiveSection;

public final class VertexActiveSectionsTest
{
    @Test
    public void publishesOnlyBuiltSectionsInCameraOrderAndRetiresRepositions()
    {
        RenderGlobal owner = new RenderGlobal();
        VertexActiveSections.reset(owner);
        assertNull(VertexActiveSections.snapshot(owner));

        Section[] built = new Section[27];

        for (int i = built.length - 1; i >= 0; --i)
        {
            built[i] = new Section(true, i * 16 + 8, 8, 8);
            VertexActiveSections.built(built[i]);
        }

        Section empty = new Section(false, 24, 8, 8);
        VertexActiveSections.built(empty);
        assertEquals(Section[].class, VertexActiveSections.snapshot(owner).getClass());
        assertArrayEquals(built, VertexActiveSections.snapshot(owner));

        // Same-position setPosition calls are no-ops; a real toroidal move retires it.
        VertexActiveSections.beforeReposition(built[0], 0, 0, 0);
        assertArrayEquals(built, VertexActiveSections.snapshot(owner));
        VertexActiveSections.beforeReposition(built[0], 16, 0, 0);
        assertNull(VertexActiveSections.snapshot(owner));

        VertexActiveSections.built(built[0]);
        assertArrayEquals(built, VertexActiveSections.snapshot(owner));

        // A vanilla movement sort becomes the registry order used by the next snapshot.
        VertexActiveSections.sort(VertexActiveSections.snapshot(owner), new Comparator<Object>()
        {
            @Override
            public int compare(Object left, Object right)
            {
                return 0;
            }
        });
        built[0].hasMesh = false;
        VertexActiveSections.built(built[0]);
        assertNull(VertexActiveSections.snapshot(owner));
    }

    private static final class RenderGlobal
    {
        private Section[] u = new Section[64];
        private Minecraft A = new Minecraft();
    }

    private static final class Minecraft
    {
        private Entity i = new Entity();
    }

    private static final class Entity
    {
        private double s;
        private double t;
        private double u;
    }

    private static final class Section implements ActiveSection
    {
        private boolean hasMesh;
        private final int x;
        private final int y;
        private final int z;

        Section(boolean hasMesh, int x, int y, int z)
        {
            this.hasMesh = hasMesh;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean vertex$hasMesh()
        {
            return hasMesh;
        }

        @Override
        public int vertex$centerX()
        {
            return x;
        }

        @Override
        public int vertex$centerY()
        {
            return y;
        }

        @Override
        public int vertex$centerZ()
        {
            return z;
        }
    }
}
