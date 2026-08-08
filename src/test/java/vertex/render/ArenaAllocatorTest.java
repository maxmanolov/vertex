package vertex.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ArenaAllocatorTest
{
    @Test
    public void allocatesFirstFitByAddressWithQuantumRounding()
    {
        ArenaAllocator arena = new ArenaAllocator(16384, 1024);
        ArenaAllocator.Range first = arena.allocate(100);
        assertEquals(0, first.offset);
        assertEquals("100 bytes must round to one quantum", 1024, first.length);
        ArenaAllocator.Range second = arena.allocate(1025);
        assertEquals(1024, second.offset);
        assertEquals("1025 bytes must round to two quanta", 2048, second.length);
        assertEquals(3072, arena.liveBytes());
    }

    @Test
    public void freeCoalescesBothNeighborsBackToOneBlock()
    {
        ArenaAllocator arena = new ArenaAllocator(8192, 1024);
        ArenaAllocator.Range a = arena.allocate(1024);
        ArenaAllocator.Range b = arena.allocate(1024);
        ArenaAllocator.Range c = arena.allocate(1024);
        assertNotNull(c);
        arena.free(a);
        arena.free(c);
        // a is an isolated hole; c coalesced rightward into the untouched tail block,
        // so live b separates exactly two holes.
        assertEquals(2, arena.freeBlockCount());
        arena.free(b);
        // Freeing b bridges everything: one hole spanning the whole arena.
        assertEquals(1, arena.freeBlockCount());
        assertEquals(0, arena.liveBytes());
        assertEquals(8192, arena.largestFreeBlock());
    }

    @Test
    public void firstFitPrefersTheLowerHole()
    {
        ArenaAllocator arena = new ArenaAllocator(8192, 1024);
        ArenaAllocator.Range a = arena.allocate(2048);
        ArenaAllocator.Range b = arena.allocate(2048);
        arena.allocate(2048);
        arena.free(a);
        arena.free(b);
        // One coalesced 4096 hole at 0 plus the 2048 tail; a small request lands low.
        ArenaAllocator.Range small = arena.allocate(1024);
        assertEquals(0, small.offset);
    }

    @Test
    public void exhaustionReturnsNullAndRecoversAfterFree()
    {
        ArenaAllocator arena = new ArenaAllocator(4096, 1024);
        ArenaAllocator.Range a = arena.allocate(4096);
        assertNull("full arena must refuse, not grow", arena.allocate(1));
        arena.free(a);
        assertNotNull(arena.allocate(4096));
    }

    @Test
    public void fragmentedArenaRefusesAnAllocationNoSingleHoleFits()
    {
        ArenaAllocator arena = new ArenaAllocator(6144, 1024);
        ArenaAllocator.Range a = arena.allocate(2048);
        ArenaAllocator.Range b = arena.allocate(1024);
        ArenaAllocator.Range c = arena.allocate(2048);
        arena.allocate(1024);
        arena.free(a);
        arena.free(c);
        assertEquals(b.offset, 2048);
        // 4096 bytes are free but the largest hole is 2048.
        assertEquals(4096, arena.capacity() - arena.liveBytes());
        assertEquals(2048, arena.largestFreeBlock());
        assertNull(arena.allocate(4096));
        assertNotNull(arena.allocate(2048));
    }

    @Test
    public void doubleFreeAndForeignRangesFailFast()
    {
        ArenaAllocator arena = new ArenaAllocator(4096, 1024);
        ArenaAllocator.Range a = arena.allocate(1024);
        arena.free(a);

        try
        {
            arena.free(a);
            fail("double free must throw");
        }
        catch (IllegalStateException expected)
        {
        }

        try
        {
            arena.free(new ArenaAllocator.Range(2048, 1024));
            fail("foreign range must throw");
        }
        catch (IllegalStateException expected)
        {
        }
    }

    @Test
    public void rejectsInvalidConstructionAndSizes()
    {
        try
        {
            new ArenaAllocator(1000, 1024);
            fail("capacity must be a multiple of the quantum");
        }
        catch (IllegalArgumentException expected)
        {
        }

        ArenaAllocator arena = new ArenaAllocator(4096, 1024);

        try
        {
            arena.allocate(0);
            fail("zero-byte allocation must throw");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    /**
     * Seeded random exercise against a shadow occupancy map: no overlap, no leak, exact
     * accounting, and full coalescing once everything frees. This is the fragmentation
     * scenario the arena will actually live through (steady rebuild churn).
     */
    @Test
    public void randomizedChurnKeepsEveryInvariant()
    {
        int capacity = 1 << 20;
        int quantum = 1024;
        ArenaAllocator arena = new ArenaAllocator(capacity, quantum);
        boolean[] occupied = new boolean[capacity];
        List<ArenaAllocator.Range> live = new ArrayList<ArenaAllocator.Range>();
        Random random = new Random(20260807L);
        long expectedLive = 0L;

        for (int op = 0; op < 8000; ++op)
        {
            boolean tryAllocate = live.isEmpty() || random.nextInt(100) < 55;

            if (tryAllocate)
            {
                int bytes = 1 + random.nextInt(16 * 1024);
                ArenaAllocator.Range range = arena.allocate(bytes);

                if (range == null)
                {
                    assertTrue("refusal is only legal when no hole fits",
                        arena.largestFreeBlock() < ((bytes + quantum - 1) / quantum) * quantum);
                    continue;
                }

                assertTrue(range.length >= bytes);
                assertEquals(0, range.offset % quantum);
                assertEquals(0, range.length % quantum);

                for (int i = range.offset; i < range.offset + range.length; ++i)
                {
                    if (occupied[i])
                    {
                        fail("overlapping allocation at byte " + i);
                    }

                    occupied[i] = true;
                }

                live.add(range);
                expectedLive += range.length;
            }
            else
            {
                ArenaAllocator.Range range = live.remove(random.nextInt(live.size()));
                arena.free(range);

                for (int i = range.offset; i < range.offset + range.length; ++i)
                {
                    occupied[i] = false;
                }

                expectedLive -= range.length;
            }

            assertEquals(expectedLive, arena.liveBytes());
            assertEquals(live.size(), arena.allocationCount());
        }

        for (ArenaAllocator.Range range : live)
        {
            arena.free(range);
        }

        assertEquals(0, arena.liveBytes());
        assertEquals("all frees must coalesce back to a single block", 1, arena.freeBlockCount());
        assertEquals(capacity, arena.largestFreeBlock());
    }
}
