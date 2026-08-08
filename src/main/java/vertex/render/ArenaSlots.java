package vertex.render;

/**
 * Per-section state the arena backend parks in the injected MeshHost slot: one
 * {@link Pass} per render pass holding the section's (buffer, offset, count) reservation
 * plus everything the draw planner needs without reflection or map lookups. Plain data;
 * all mutation happens on the client thread inside the backend.
 */
final class ArenaSlots
{
    static final class Pass
    {
        /** Owning arena bookkeeping (null while the pass is empty). */
        Object arena;
        Object block;
        ArenaAllocator.Range range;
        /** GL buffer id of the block, copied here so planning never chases references. */
        int buffer;
        /** First vertex index within the buffer (byte offset / stride). */
        int first;
        int count;
        int drawMode;
        int formatBits;
        /** The 1024-region base this pass's vertices were baked against. */
        int minusX;
        int minusZ;

        void clear()
        {
            this.arena = null;
            this.block = null;
            this.range = null;
            this.buffer = 0;
            this.first = 0;
            this.count = 0;
        }
    }

    final int generation;
    final Pass[] passes = {new Pass(), new Pass()};

    ArenaSlots(int generation)
    {
        this.generation = generation;
    }
}
