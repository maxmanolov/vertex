package vertex.render;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Byte-range allocator for one shared vertex-buffer arena (docs/RENDERER.md section 5):
 * first-fit by address over a free list with address-ordered coalescing, allocations
 * rounded up to a fixed quantum to bound fragmentation bookkeeping. Pure Java, no GL -
 * the arena backend maps ranges onto a GL buffer, this class only does the arithmetic,
 * so every invariant is unit-testable (ArenaAllocatorTest).
 *
 * Outstanding allocations are tracked so a double free or a foreign range fails fast
 * with an exception instead of silently corrupting the free list - inside a shared GPU
 * buffer that corruption would surface as another section's geometry, far from the bug.
 *
 * Single-threaded by contract (the client thread owns all arena state), like every
 * GL-adjacent structure in Vertex.
 */
public final class ArenaAllocator
{
    /** One reserved byte range. length is the rounded reservation, not the payload size. */
    public static final class Range
    {
        public final int offset;
        public final int length;

        Range(int offset, int length)
        {
            this.offset = offset;
            this.length = length;
        }
    }

    private final int capacity;
    private final int quantum;
    /** Free blocks, offset -> length; keys are unique and blocks never touch (coalesced). */
    private final TreeMap<Integer, Integer> free = new TreeMap<Integer, Integer>();
    /** Live reservations, offset -> length, for fail-fast free validation. */
    private final Map<Integer, Integer> allocated = new HashMap<Integer, Integer>();
    private int liveBytes = 0;

    public ArenaAllocator(int capacity, int quantum)
    {
        if (capacity <= 0 || quantum <= 0 || capacity % quantum != 0)
        {
            throw new IllegalArgumentException("capacity must be a positive multiple of the quantum");
        }

        this.capacity = capacity;
        this.quantum = quantum;
        this.free.put(Integer.valueOf(0), Integer.valueOf(capacity));
    }

    /**
     * Reserve at least {@code bytes} (rounded up to the quantum), first fit by lowest
     * address. Returns null when no free block fits - the arena backend then opens a
     * new arena block rather than growing (and copying) this one.
     */
    public Range allocate(int bytes)
    {
        if (bytes <= 0)
        {
            throw new IllegalArgumentException("allocation size must be positive: " + bytes);
        }

        int needed = roundUp(bytes);

        for (Map.Entry<Integer, Integer> candidate : this.free.entrySet())
        {
            int offset = candidate.getKey().intValue();
            int length = candidate.getValue().intValue();

            if (length < needed)
            {
                continue;
            }

            this.free.remove(candidate.getKey());

            if (length > needed)
            {
                this.free.put(Integer.valueOf(offset + needed), Integer.valueOf(length - needed));
            }

            this.allocated.put(Integer.valueOf(offset), Integer.valueOf(needed));
            this.liveBytes += needed;
            return new Range(offset, needed);
        }

        return null;
    }

    /** Release a reservation, coalescing with free neighbors on both sides. */
    public void free(Range range)
    {
        Integer key = Integer.valueOf(range.offset);
        Integer reserved = this.allocated.remove(key);

        if (reserved == null || reserved.intValue() != range.length)
        {
            throw new IllegalStateException("free of unknown or mismatched range at offset "
                + range.offset + " length " + range.length);
        }

        this.liveBytes -= range.length;
        int offset = range.offset;
        int length = range.length;

        Map.Entry<Integer, Integer> before = this.free.floorEntry(Integer.valueOf(offset - 1));

        if (before != null && before.getKey().intValue() + before.getValue().intValue() == offset)
        {
            offset = before.getKey().intValue();
            length += before.getValue().intValue();
            this.free.remove(before.getKey());
        }

        Integer afterKey = Integer.valueOf(range.offset + range.length);
        Integer afterLength = this.free.get(afterKey);

        if (afterLength != null)
        {
            length += afterLength.intValue();
            this.free.remove(afterKey);
        }

        this.free.put(Integer.valueOf(offset), Integer.valueOf(length));
    }

    public int capacity()
    {
        return this.capacity;
    }

    /** Bytes currently reserved (rounded); capacity minus this is free. */
    public int liveBytes()
    {
        return this.liveBytes;
    }

    /** The largest single allocation that could currently succeed. */
    public int largestFreeBlock()
    {
        int largest = 0;

        for (Integer length : this.free.values())
        {
            if (length.intValue() > largest)
            {
                largest = length.intValue();
            }
        }

        return largest;
    }

    /** Number of disjoint free blocks - the direct fragmentation gauge. */
    public int freeBlockCount()
    {
        return this.free.size();
    }

    /** Number of live reservations. */
    public int allocationCount()
    {
        return this.allocated.size();
    }

    private int roundUp(int bytes)
    {
        int quanta = (bytes + this.quantum - 1) / this.quantum;
        return quanta * this.quantum;
    }
}
