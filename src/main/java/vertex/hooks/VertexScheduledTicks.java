package vertex.hooks;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.launchwrapper.LogWrapper;
import org.apache.logging.log4j.Level;
import vertex.Mappings;
import vertex.server.IndexedTickSet;

/** Spatial index and chunk-local iterator for integrated-server scheduled block ticks. */
public final class VertexScheduledTicks
{
    private static Field pendingSet;
    private static Field pendingTree;
    private static Field chunkX;
    private static Field chunkZ;
    private static boolean initialized;
    private static volatile boolean disabled;

    /** Constructor/initialize tail: replace the plain membership set once it exists. */
    public static void install(Object world)
    {
        if (disabled)
        {
            return;
        }

        try
        {
            if (ready(world, null))
            {
                installCurrent(world);
            }
        }
        catch (Exception e)
        {
            disable("install", e);
        }
    }

    /**
     * Replaces WorldServer's full TreeSet iterator only for the first pass of the chunk
     * query. The returned iterator removes from the original TreeSet when vanilla calls
     * remove(), preserving the two-set invariant.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator candidateIterator(Object world, Object chunk)
    {
        try
        {
            if (!disabled && ready(world, chunk))
            {
                installCurrent(world);
                Object membership = pendingSet.get(world);
                TreeSet tree = (TreeSet)pendingTree.get(world);

                if (membership instanceof IndexedTickSet)
                {
                    int x = chunkX.getInt(chunk);
                    int z = chunkZ.getInt(chunk);
                    Iterator<Object> local = ((IndexedTickSet)membership).nearby(x, z, tree.comparator());
                    return new TreeRemovingIterator(local, tree);
                }

                return tree.iterator();
            }
        }
        catch (Exception e)
        {
            disable("query", e);
        }

        return fallbackTree(world).iterator();
    }

    @SuppressWarnings("unchecked")
    private static void installCurrent(Object world) throws IllegalAccessException
    {
        Set<Object> current = (Set<Object>)pendingSet.get(world);

        if (current != null && !(current instanceof IndexedTickSet))
        {
            pendingSet.set(world, new IndexedTickSet(current));
        }
    }

    private static synchronized boolean ready(Object world, Object chunk) throws NoSuchFieldException
    {
        if (disabled)
        {
            return false;
        }

        if (!initialized)
        {
            pendingSet = field(world.getClass(), Mappings.WS_PENDING_TICK_SET);
            pendingTree = field(world.getClass(), Mappings.WS_PENDING_TICK_TREE);
            initialized = true;
        }

        if (chunk != null && chunkX == null)
        {
            chunkX = field(chunk.getClass(), Mappings.CHUNK_X);
            chunkZ = field(chunk.getClass(), Mappings.CHUNK_Z);
        }

        return true;
    }

    @SuppressWarnings("rawtypes")
    private static TreeSet fallbackTree(Object world)
    {
        try
        {
            Field tree = pendingTree != null ? pendingTree : field(world.getClass(), Mappings.WS_PENDING_TICK_TREE);
            return (TreeSet)tree.get(world);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Cannot restore the scheduled-tick iterator", e);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException
    {
        Class<?> type = owner;

        while (type != null)
        {
            try
            {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            }
            catch (NoSuchFieldException ignored)
            {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static void disable(String phase, Exception error)
    {
        if (!disabled)
        {
            disabled = true;
            LogWrapper.log(Level.WARN, error,
                "[Vertex] Scheduled-tick index disabled during %s; using the vanilla iterator", phase);
        }
    }

    private static final class TreeRemovingIterator implements Iterator<Object>
    {
        private final Iterator<Object> local;
        private final Set<Object> sourceTree;
        private Object current;
        private boolean removable;

        TreeRemovingIterator(Iterator<Object> local, Set<Object> sourceTree)
        {
            this.local = local;
            this.sourceTree = sourceTree;
        }

        @Override
        public boolean hasNext()
        {
            return local.hasNext();
        }

        @Override
        public Object next()
        {
            current = local.next();
            removable = true;
            return current;
        }

        @Override
        public void remove()
        {
            if (!removable)
            {
                throw new IllegalStateException();
            }

            local.remove();
            sourceTree.remove(current);
            removable = false;
        }
    }

    private VertexScheduledTicks()
    {
    }
}
