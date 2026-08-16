package vertex.server;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import vertex.api.ScheduledTickPosition;

/**
 * Set of scheduled block ticks indexed by the chunk containing each tick.
 *
 * The game still owns its time-ordered TreeSet. This structure replaces only the
 * membership set, so ordinary add/contains/remove operations remain set operations while
 * chunk-save queries can visit nine local buckets instead of every scheduled tick in the
 * world. Chunk keys stay primitive to avoid allocating a coordinate object per lookup.
 */
public final class IndexedTickSet extends AbstractSet<Object>
{
    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.60F;

    private long[] keys = new long[INITIAL_CAPACITY];
    private Set<Object>[] buckets = buckets(INITIAL_CAPACITY);
    /** 0 = empty, 1 = occupied, 2 = deleted. */
    private byte[] states = new byte[INITIAL_CAPACITY];
    private int size;
    private int occupied;

    public IndexedTickSet(Set<?> source)
    {
        if (source != null)
        {
            addAll(source);
        }
    }

    @Override
    public int size()
    {
        return size;
    }

    @Override
    public boolean contains(Object entry)
    {
        ScheduledTickPosition position = position(entry);

        if (position == null)
        {
            return false;
        }

        int slot = findExisting(key(position.vertex$x() >> 4, position.vertex$z() >> 4));
        return slot >= 0 && buckets[slot].contains(entry);
    }

    @Override
    public boolean add(Object entry)
    {
        ScheduledTickPosition position = position(entry);

        if (position == null)
        {
            throw new IllegalArgumentException("Scheduled tick does not expose coordinates: " + entry);
        }

        long key = key(position.vertex$x() >> 4, position.vertex$z() >> 4);
        int slot = findInsertion(key);

        if (states[slot] != 1)
        {
            if (occupied + 1 > (int)(keys.length * LOAD_FACTOR))
            {
                resize(keys.length << 1);
                slot = findInsertion(key);
            }

            if (states[slot] == 0)
            {
                ++occupied;
            }

            states[slot] = 1;
            keys[slot] = key;
            buckets[slot] = new HashSet<Object>();
        }

        boolean added = buckets[slot].add(entry);

        if (added)
        {
            ++size;
        }

        return added;
    }

    @Override
    public boolean remove(Object entry)
    {
        ScheduledTickPosition position = position(entry);

        if (position == null)
        {
            return false;
        }

        int slot = findExisting(key(position.vertex$x() >> 4, position.vertex$z() >> 4));

        if (slot < 0 || !buckets[slot].remove(entry))
        {
            return false;
        }

        --size;
        retireIfEmpty(slot);
        return true;
    }

    @Override
    public void clear()
    {
        keys = new long[INITIAL_CAPACITY];
        buckets = buckets(INITIAL_CAPACITY);
        states = new byte[INITIAL_CAPACITY];
        size = 0;
        occupied = 0;
    }

    @Override
    public Iterator<Object> iterator()
    {
        return new BucketIterator();
    }

    /** Returns a sorted snapshot of ticks in the target chunk and its eight neighbors. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Iterator<Object> nearby(int chunkX, int chunkZ, Comparator comparator)
    {
        List<Object> result = new ArrayList<Object>();

        for (int dx = -1; dx <= 1; ++dx)
        {
            for (int dz = -1; dz <= 1; ++dz)
            {
                int slot = findExisting(key(chunkX + dx, chunkZ + dz));

                if (slot >= 0)
                {
                    result.addAll(buckets[slot]);
                }
            }
        }

        if (comparator == null)
        {
            Collections.sort((List)result);
        }
        else
        {
            Collections.sort(result, comparator);
        }

        return result.iterator();
    }

    private int findExisting(long key)
    {
        int mask = keys.length - 1;
        int slot = mix(key) & mask;

        while (states[slot] != 0)
        {
            if (states[slot] == 1 && keys[slot] == key)
            {
                return slot;
            }

            slot = (slot + 1) & mask;
        }

        return -1;
    }

    private int findInsertion(long key)
    {
        int mask = keys.length - 1;
        int slot = mix(key) & mask;
        int deleted = -1;

        while (states[slot] != 0)
        {
            if (states[slot] == 1 && keys[slot] == key)
            {
                return slot;
            }

            if (states[slot] == 2 && deleted < 0)
            {
                deleted = slot;
            }

            slot = (slot + 1) & mask;
        }

        return deleted >= 0 ? deleted : slot;
    }

    private void resize(int capacity)
    {
        long[] oldKeys = keys;
        Set<Object>[] oldBuckets = buckets;
        byte[] oldStates = states;
        keys = new long[capacity];
        buckets = buckets(capacity);
        states = new byte[capacity];
        occupied = 0;

        for (int i = 0; i < oldKeys.length; ++i)
        {
            if (oldStates[i] == 1)
            {
                int slot = findInsertion(oldKeys[i]);
                states[slot] = 1;
                keys[slot] = oldKeys[i];
                buckets[slot] = oldBuckets[i];
                ++occupied;
            }
        }
    }

    private void retireIfEmpty(int slot)
    {
        if (buckets[slot].isEmpty())
        {
            buckets[slot] = null;
            states[slot] = 2;
        }
    }

    private static ScheduledTickPosition position(Object entry)
    {
        return entry instanceof ScheduledTickPosition ? (ScheduledTickPosition)entry : null;
    }

    private static long key(int chunkX, int chunkZ)
    {
        return ((long)chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static int mix(long value)
    {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return (int)value;
    }

    @SuppressWarnings("unchecked")
    private static Set<Object>[] buckets(int size)
    {
        return (Set<Object>[])new Set<?>[size];
    }

    private final class BucketIterator implements Iterator<Object>
    {
        private int bucket = -1;
        private int currentBucket = -1;
        private Iterator<Object> entries = Collections.<Object>emptyList().iterator();
        private boolean removable;

        @Override
        public boolean hasNext()
        {
            while (!entries.hasNext())
            {
                do
                {
                    ++bucket;
                }
                while (bucket < states.length && states[bucket] != 1);

                if (bucket >= states.length)
                {
                    return false;
                }

                entries = buckets[bucket].iterator();
            }

            return true;
        }

        @Override
        public Object next()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }

            Object result = entries.next();
            currentBucket = bucket;
            removable = true;
            return result;
        }

        @Override
        public void remove()
        {
            if (!removable)
            {
                throw new IllegalStateException();
            }

            entries.remove();
            --size;
            retireIfEmpty(currentBucket);
            removable = false;
        }
    }
}
