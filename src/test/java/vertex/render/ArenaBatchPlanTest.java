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
    public void opaqueSortOrdersBatchesByStableIdentityRegardlessOfArrival()
    {
        // The same three batches arriving in two different (worker-completion) orders
        // must submit identically after the sort: region first, buffer id last.
        ArenaBatchPlan early = new ArenaBatchPlan();
        early.add(false, item(9, 0, 6, 1024, 0));
        early.add(false, item(2, 0, 6, 0, 0));
        early.add(false, item(5, 0, 6, 0, 1024));
        early.sortOpaqueBatches();

        ArenaBatchPlan late = new ArenaBatchPlan();
        late.add(false, item(5, 0, 6, 0, 1024));
        late.add(false, item(9, 0, 6, 1024, 0));
        late.add(false, item(2, 0, 6, 0, 0));
        late.sortOpaqueBatches();

        assertEquals(3, early.batchCount());

        for (int i = 0; i < 3; ++i)
        {
            assertEquals("buffer order run-independent",
                early.batch(i).buffer, late.batch(i).buffer);
            assertEquals(early.batch(i).minusX, late.batch(i).minusX);
            assertEquals(early.batch(i).minusZ, late.batch(i).minusZ);
        }

        assertEquals("region (0,0) sorts first", 2, early.batch(0).buffer);
        assertEquals("region (0,1024) second", 5, early.batch(1).buffer);
        assertEquals("region (1024,0) third", 9, early.batch(2).buffer);
    }

    @Test
    public void opaqueSortKeepsRangeContentAndWalkOrderInsideEachBatch()
    {
        ArenaBatchPlan plan = new ArenaBatchPlan();
        plan.add(false, item(3, 0, 24, 1024, 0));
        plan.add(false, item(1, 0, 12, 0, 0));
        plan.add(false, item(1, 64, 18, 0, 0));
        plan.sortOpaqueBatches();

        assertEquals(1, plan.batch(0).buffer);
        assertEquals(2, plan.batch(0).size);
        assertEquals("walk order inside the batch survives the sort",
            0, plan.batch(0).firsts[0]);
        assertEquals(64, plan.batch(0).firsts[1]);
        assertEquals(3, plan.batch(1).buffer);
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
