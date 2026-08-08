package vertex.render;

/**
 * Pure batching planner for arena submission (no GL, unit-tested directly): turns the
 * pass's visible sections - already in vanilla's walk order - into the fewest safe draw
 * batches, where one batch is one buffer bind + one pointer setup + one (multi-)draw.
 *
 * Two regimes, chosen per pass:
 *  - OPAQUE (pass 0): order within the pass is free under the depth test, so sections
 *    merge into any batch sharing (buffer, format, mode, region base) regardless of walk
 *    position - at steady state that is a handful of batches per visible 1024-region.
 *  - ORDERED (pass 1): translucency requires vanilla's back-to-front order, so only
 *    CONSECUTIVE sections sharing a batch key merge; ranges inside one multi-draw
 *    execute in array order and runs preserve the global walk order exactly. This is
 *    the deliberately less aggressive path the design calls for.
 *
 * Batch objects and their arrays are pooled and grow-only; steady state allocates
 * nothing per frame.
 */
final class ArenaBatchPlan
{
    static final class Batch
    {
        int buffer;
        int formatBits;
        int drawMode;
        int minusX;
        int minusZ;
        int size;
        int[] firsts = new int[64];
        int[] counts = new int[64];

        private boolean accepts(ArenaSlots.Pass item)
        {
            return this.buffer == item.buffer && this.formatBits == item.formatBits
                && this.drawMode == item.drawMode
                && this.minusX == item.minusX && this.minusZ == item.minusZ;
        }

        private void begin(ArenaSlots.Pass item)
        {
            this.buffer = item.buffer;
            this.formatBits = item.formatBits;
            this.drawMode = item.drawMode;
            this.minusX = item.minusX;
            this.minusZ = item.minusZ;
            this.size = 0;
        }

        private void add(ArenaSlots.Pass item)
        {
            if (this.size == this.firsts.length)
            {
                int[] growFirsts = new int[this.size * 2];
                int[] growCounts = new int[this.size * 2];
                System.arraycopy(this.firsts, 0, growFirsts, 0, this.size);
                System.arraycopy(this.counts, 0, growCounts, 0, this.size);
                this.firsts = growFirsts;
                this.counts = growCounts;
            }

            this.firsts[this.size] = item.first;
            this.counts[this.size] = item.count;
            ++this.size;
        }
    }

    private Batch[] batches = new Batch[16];
    private int batchCount = 0;
    private int itemCount = 0;

    ArenaBatchPlan()
    {
        for (int i = 0; i < this.batches.length; ++i)
        {
            this.batches[i] = new Batch();
        }
    }

    void reset()
    {
        this.batchCount = 0;
        this.itemCount = 0;
    }

    /** Add one visible section's pass entry, in walk order. */
    void add(boolean ordered, ArenaSlots.Pass item)
    {
        ++this.itemCount;

        if (ordered)
        {
            // Translucent: merge only into the current tail run.
            if (this.batchCount > 0 && this.batches[this.batchCount - 1].accepts(item))
            {
                this.batches[this.batchCount - 1].add(item);
                return;
            }
        }
        else
        {
            // Opaque: merge into any existing batch with the same key.
            for (int i = 0; i < this.batchCount; ++i)
            {
                if (this.batches[i].accepts(item))
                {
                    this.batches[i].add(item);
                    return;
                }
            }
        }

        if (this.batchCount == this.batches.length)
        {
            Batch[] grown = new Batch[this.batchCount * 2];
            System.arraycopy(this.batches, 0, grown, 0, this.batchCount);

            for (int i = this.batchCount; i < grown.length; ++i)
            {
                grown[i] = new Batch();
            }

            this.batches = grown;
        }

        Batch batch = this.batches[this.batchCount++];
        batch.begin(item);
        batch.add(item);
    }

    int batchCount()
    {
        return this.batchCount;
    }

    Batch batch(int index)
    {
        return this.batches[index];
    }

    int itemCount()
    {
        return this.itemCount;
    }
}
