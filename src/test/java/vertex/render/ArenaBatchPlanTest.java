package vertex.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The batching planner's two regimes: opaque merges across the walk into any matching
 * batch (fewest submissions), ordered merges only consecutive runs so the translucent
 * back-to-front walk order survives multi-draw exactly.
 */
public class ArenaBatchPlanTest
{
    private static ArenaSlots.Pass item(int buffer, int first, int count, int minusX, int minusZ)
    {
        ArenaSlots.Pass pass = new ArenaSlots.Pass();
        pass.buffer = buffer;
        pass.first = first;
        pass.count = count;
        pass.drawMode = 7;
        pass.formatBits = 7;
        pass.minusX = minusX;
        pass.minusZ = minusZ;
        return pass;
    }

    @Test
    public void opaqueMergesInterleavedRegionsIntoOneBatchEach()
    {
        ArenaBatchPlan plan = new ArenaBatchPlan();
        // Walk order alternates between two regions' buffers: A B A B A.
        plan.add(false, item(1, 0, 24, 0, 0));
        plan.add(false, item(2, 0, 36, 1024, 0));
        plan.add(false, item(1, 32, 12, 0, 0));
        plan.add(false, item(2, 64, 6, 1024, 0));
        plan.add(false, item(1, 96, 18, 0, 0));

        assertEquals("one batch per (buffer, region)", 2, plan.batchCount());
        assertEquals(5, plan.itemCount());
        ArenaBatchPlan.Batch first = plan.batch(0);
        assertEquals(1, first.buffer);
        assertEquals(3, first.size);
        assertEquals(0, first.firsts[0]);
        assertEquals(32, first.firsts[1]);
        assertEquals(96, first.firsts[2]);
        assertEquals(2, plan.batch(1).buffer);
        assertEquals(2, plan.batch(1).size);
    }

    @Test
    public void orderedSplitsOnEveryRegionFlipAndPreservesWalkOrder()
    {
        ArenaBatchPlan plan = new ArenaBatchPlan();
        // Same walk as above, but translucent: A B A B A must stay five ordered runs
        // of one - merging the As would draw them out of global order.
        plan.add(true, item(1, 0, 24, 0, 0));
        plan.add(true, item(2, 0, 36, 1024, 0));
        plan.add(true, item(1, 32, 12, 0, 0));
        plan.add(true, item(2, 64, 6, 1024, 0));
        plan.add(true, item(1, 96, 18, 0, 0));

        assertEquals(5, plan.batchCount());
        assertEquals(1, plan.batch(0).buffer);
        assertEquals(2, plan.batch(1).buffer);
        assertEquals(1, plan.batch(2).buffer);
        assertEquals(2, plan.batch(3).buffer);
        assertEquals(1, plan.batch(4).buffer);
    }

    @Test
    public void orderedMergesConsecutiveRunsInRangeOrder()
    {
        ArenaBatchPlan plan = new ArenaBatchPlan();
        plan.add(true, item(1, 320, 24, 0, 0));
        plan.add(true, item(1, 0, 36, 0, 0));
        plan.add(true, item(1, 128, 12, 0, 0));
        plan.add(true, item(2, 0, 6, 1024, 0));

        assertEquals(2, plan.batchCount());
        ArenaBatchPlan.Batch run = plan.batch(0);
        assertEquals(3, run.size);
        // Multi-draw executes ranges in array order == the walk order, NOT ascending
        // offsets - that is exactly what keeps back-to-front correct.
        assertEquals(320, run.firsts[0]);
        assertEquals(0, run.firsts[1]);
        assertEquals(128, run.firsts[2]);
    }

    @Test
    public void formatOrModeChangesSplitBatchesInBothRegimes()
    {
        ArenaBatchPlan plan = new ArenaBatchPlan();
        ArenaSlots.Pass normals = item(1, 0, 24, 0, 0);
        normals.formatBits = 15;
        plan.add(false, item(1, 32, 12, 0, 0));
        plan.add(false, normals);
        plan.add(false, item(1, 64, 12, 0, 0));
        assertEquals("format change forces its own batch", 2, plan.batchCount());
        assertEquals(2, plan.batch(0).size);
    }

    @Test
    public void poolsGrowPastTheirInitialCapacities()
    {
        ArenaBatchPlan plan = new ArenaBatchPlan();

        // More ranges than one batch's initial array (64) in a single run.
        for (int i = 0; i < 200; ++i)
        {
            plan.add(false, item(1, i * 32, 8, 0, 0));
        }

        // More batches than the initial pool (16), via distinct buffers, twice over to
        // prove reset() recycles cleanly.
        for (int i = 0; i < 40; ++i)
        {
            plan.add(false, item(100 + i, 0, 8, 0, 0));
        }

        assertEquals(41, plan.batchCount());
        assertEquals(200, plan.batch(0).size);
        plan.reset();
        assertEquals(0, plan.batchCount());
        plan.add(false, item(5, 0, 8, 0, 0));
        assertEquals(1, plan.batchCount());
        assertEquals(1, plan.batch(0).size);
    }
}
