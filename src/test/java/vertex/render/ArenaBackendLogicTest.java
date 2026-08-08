package vertex.render;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** GL-free arena logic: region math, bake transform, and the drain decision. */
public class ArenaBackendLogicTest
{
    @Test
    public void regionFloorMatchesVanillaClipArithmeticIncludingNegatives()
    {
        assertEquals(0, ArenaBackend.regionMinus(0));
        assertEquals(0, ArenaBackend.regionMinus(1023));
        assertEquals(1024, ArenaBackend.regionMinus(1024));
        assertEquals(2048, ArenaBackend.regionMinus(2560));
        // Java's & on two's complement floors negatives exactly like vanilla's
        // posX - (posX & 1023): -16 & 1023 == 1008, so minus == -1024.
        assertEquals(-1024, ArenaBackend.regionMinus(-16));
        assertEquals(-1024, ArenaBackend.regionMinus(-1024));
        assertEquals(-2048, ArenaBackend.regionMinus(-1025));
    }

    @Test
    public void regionKeysAreDistinctAcrossNeighborsAndSigns()
    {
        long[] keys = {
            ArenaBackend.regionKey(0, 0),
            ArenaBackend.regionKey(1024, 0),
            ArenaBackend.regionKey(0, 1024),
            ArenaBackend.regionKey(-1024, 0),
            ArenaBackend.regionKey(0, -1024),
            ArenaBackend.regionKey(-1024, -1024),
        };

        for (int a = 0; a < keys.length; ++a)
        {
            for (int b = a + 1; b < keys.length; ++b)
            {
                if (keys[a] == keys[b])
                {
                    throw new AssertionError("key collision between region " + a + " and " + b);
                }
            }
        }
    }

    @Test
    public void bakeTransformIsExactAndTouchesOnlyPositions()
    {
        float scale = 1.000001F;
        // Two vertices: positions plus distinctive attribute ints.
        int[] src = new int[16];
        src[0] = Float.floatToRawIntBits(0.0F);
        src[1] = Float.floatToRawIntBits(69.5F);
        src[2] = Float.floatToRawIntBits(16.0F);
        src[3] = 0x11111111;
        src[4] = 0x22222222;
        src[5] = 0x33333333;
        src[6] = 0x44444444;
        src[7] = 0x55555555;
        src[8] = Float.floatToRawIntBits(8.0F);
        src[9] = Float.floatToRawIntBits(0.25F);
        src[10] = Float.floatToRawIntBits(15.75F);
        src[11] = 0x66666666;
        src[12] = 0x77777777;
        src[13] = 0x78787878;
        src[14] = 0x79797979;
        src[15] = 0x7A7A7A7A;

        float addX = 1008.0F;   // a clip coordinate from a negative region
        float addY = 64.0F;
        float addZ = 0.0F;
        int[] dst = new int[16];
        Staging.bakeInto(src, 16, addX, addY, addZ, scale, dst);

        for (int v = 0; v < 16; v += 8)
        {
            for (int axis = 0; axis < 3; ++axis)
            {
                float add = axis == 0 ? addX : axis == 1 ? addY : addZ;
                float expected = Float.intBitsToFloat(src[v + axis]) * scale
                    + (8.0F * (1.0F - scale) + add);
                assertEquals("vertex " + v / 8 + " axis " + axis,
                    Float.floatToRawIntBits(expected), dst[v + axis]);
            }

            for (int attr = 3; attr < 8; ++attr)
            {
                assertEquals("attribute ints must pass through verbatim", src[v + attr], dst[v + attr]);
            }
        }

        // The identity check that makes the anti-crack fold trustworthy: baking with
        // scale about center 8 equals vanilla's T(add) * T(-8) * S * T(8) applied to v.
        float local = 3.0F;
        float vanilla = (local - 8.0F) * scale + 8.0F + addX;
        float baked = local * scale + (8.0F * (1.0F - scale) + addX);
        assertEquals(vanilla, baked, 1.0E-4F);
    }

    @Test
    public void drainPolicyExemptsTheFrontierAndWellFilledBlocks()
    {
        List<ArenaBackend.ArenaBlock> blocks = new ArrayList<ArenaBackend.ArenaBlock>();
        ArenaBackend.ArenaBlock a = new ArenaBackend.ArenaBlock(1, 8192);
        blocks.add(a);
        // Single block: never drained, whatever its occupancy.
        assertNull(ArenaBackend.pickDrainCandidate(blocks));

        // Old block a nearly empty, frontier b holding the working set: occupancy
        // (1024+4096)/16384 = 31% and a is under 25% full -> a drains. This is the
        // genuine-fragmentation case the mechanism exists for.
        ArenaBackend.ArenaBlock b = new ArenaBackend.ArenaBlock(2, 8192);
        blocks.add(b);
        fill(a, 1024);
        fill(b, 4096);
        assertSame(a, ArenaBackend.pickDrainCandidate(blocks));

        // An active drain blocks a second one.
        a.draining = true;
        assertNull(ArenaBackend.pickDrainCandidate(blocks));
        a.draining = false;

        // The mirrored shape - the emptiest block IS the frontier (a fresh block always
        // is) and the old block sits at 50% - must drain NOTHING despite the same 31%
        // occupancy. Without the frontier and under-25% guards this exact state
        // thrashed: every new block immediately drained and its migration created the
        // next one (27,535 drains/min on the first arena soak).
        List<ArenaBackend.ArenaBlock> mirrored = new ArrayList<ArenaBackend.ArenaBlock>();
        ArenaBackend.ArenaBlock old = new ArenaBackend.ArenaBlock(3, 8192);
        ArenaBackend.ArenaBlock frontier = new ArenaBackend.ArenaBlock(4, 8192);
        mirrored.add(old);
        mirrored.add(frontier);
        fill(old, 4096);
        fill(frontier, 1024);
        assertNull(ArenaBackend.pickDrainCandidate(mirrored));

        // Healthy occupancy (>= 50%) blocks draining even with an eligible old block.
        List<ArenaBackend.ArenaBlock> healthy = new ArrayList<ArenaBackend.ArenaBlock>();
        ArenaBackend.ArenaBlock sparse = new ArenaBackend.ArenaBlock(5, 8192);
        ArenaBackend.ArenaBlock dense = new ArenaBackend.ArenaBlock(6, 8192);
        healthy.add(sparse);
        healthy.add(dense);
        fill(sparse, 1024);
        fill(dense, 7168);
        assertNull(ArenaBackend.pickDrainCandidate(healthy));
    }

    private static void fill(ArenaBackend.ArenaBlock block, int bytes)
    {
        if (block.allocator.allocate(bytes) == null)
        {
            throw new AssertionError("test fill failed");
        }
    }
}
