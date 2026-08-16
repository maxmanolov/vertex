package vertex.hooks;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.launchwrapper.LogWrapper;
import vertex.api.DistanceKeyHost;

/**
 * Full renderer-array ordering with one distance derivation per section. The hook is
 * also wired across an unrelated RenderList sort in the same vanilla method, so arrays
 * whose elements do not implement DistanceKeyHost delegate directly to Arrays.sort.
 */
public final class VertexRenderOrder
{
    private static final Comparator<Object> BY_KEY = new Comparator<Object>()
    {
        public int compare(Object left, Object right)
        {
            return Double.compare(((DistanceKeyHost)left).vertex$sortKey(),
                ((DistanceKeyHost)right).vertex$sortKey());
        }
    };

    private static Class<?> sorterClass;
    private static Field offsetX;
    private static Field offsetY;
    private static Field offsetZ;
    private static boolean disabled;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void sort(Object[] values, Comparator comparator)
    {
        if (disabled || values.length == 0 || !(values[0] instanceof DistanceKeyHost))
        {
            Arrays.sort(values, comparator);
            return;
        }

        try
        {
            ready(comparator);
            double xOffset = offsetX.getDouble(comparator);
            double yOffset = offsetY.getDouble(comparator);
            double zOffset = offsetZ.getDouble(comparator);

            for (int i = 0; i < values.length; ++i)
            {
                DistanceKeyHost section = (DistanceKeyHost)values[i];
                double dx = (double)section.vertex$centerX() + xOffset;
                double dy = (double)section.vertex$centerY() + yOffset;
                double dz = (double)section.vertex$centerZ() + zOffset;
                section.vertex$setSortKey(dx * dx + dy * dy + dz * dz);
            }

            Arrays.sort(values, BY_KEY);
        }
        catch (Exception e)
        {
            disabled = true;
            LogWrapper.warning("[Vertex] Cached renderer ordering disabled; using the vanilla comparator: " + e);
            Arrays.sort(values, comparator);
        }
    }

    private static synchronized void ready(Comparator<?> comparator) throws Exception
    {
        Class<?> type = comparator.getClass();

        if (type == sorterClass)
        {
            return;
        }

        Field x = type.getDeclaredField("a");
        Field y = type.getDeclaredField("b");
        Field z = type.getDeclaredField("c");
        x.setAccessible(true);
        y.setAccessible(true);
        z.setAccessible(true);

        if (x.getType() != Double.TYPE || y.getType() != Double.TYPE || z.getType() != Double.TYPE)
        {
            throw new IllegalStateException("sorter coordinate fields are not doubles");
        }

        offsetX = x;
        offsetY = y;
        offsetZ = z;
        sorterClass = type;
    }

    private VertexRenderOrder()
    {
    }
}
