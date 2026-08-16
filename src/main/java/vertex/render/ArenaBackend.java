package vertex.render;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;
import vertex.api.MeshHost;

/**
 * Stage-3 shared-arena backend (`renderer=arena`, docs/RENDERER.md section 5): sections
 * own (buffer, offset, count) ranges inside arena blocks shared per (1024-region, pass),
 * with the section transform baked into the vertices at staging time, so a whole batch
 * draws with one bind, one pointer setup, one region translate and one multi-draw.
 *
 * Lifecycle, all client thread:
 *  - upload = allocate-new, glBufferSubData, flip the slot, free-old - never an in-place
 *    overwrite of a range this frame may still draw;
 *  - growth = add 16 MB blocks per arena, never resize-and-copy live meshes;
 *  - compaction = when a multi-block arena's occupancy halves, its emptiest block stops
 *    accepting allocations and its resident sections re-mark dirty through the normal
 *    rebuild path (drained via the orchestrator's clientTick); the block deletes when
 *    its last range frees. A draining block keeps drawing until then - no visual gap;
 *  - reset() (world change, render-distance change, disable) deletes every block and
 *    invalidates all slots by generation.
 *
 * Submission uses glMultiDrawArrays where GL 1.4 is present and a per-range loop
 * otherwise (same binds and pointer setup either way); translucency stays exactly
 * vanilla-ordered via the planner's run batching.
 */
public final class ArenaBackend implements RenderBackend
{
    private static final float SECTION_SCALE = 1.000001F;
    /** Default arena block: holds hundreds of typical section meshes. */
    private static final int BLOCK_BYTES = 16 * 1024 * 1024;
    private static final int QUANTUM = 1024;

    static final class ArenaBlock
    {
        final int buffer;
        final int capacity;
        final ArenaAllocator allocator;
        boolean draining;
        /** Live reservations, keyed by the owning slot pass; values are the renderers. */
        final IdentityHashMap<Object, Object> residents = new IdentityHashMap<Object, Object>();

        ArenaBlock(int buffer, int capacity)
        {
            this.buffer = buffer;
            this.capacity = capacity;
            this.allocator = new ArenaAllocator(capacity, QUANTUM);
        }
    }

    static final class RegionArena
    {
        final int minusX;
        final int minusZ;
        final List<ArenaBlock> blocks = new ArrayList<ArenaBlock>();

        RegionArena(int minusX, int minusZ)
        {
            this.minusX = minusX;
            this.minusZ = minusZ;
        }
    }

    private int generation = 0;
    @SuppressWarnings("unchecked")
    private final Map<Long, RegionArena>[] arenas = new HashMap[] {
        new HashMap<Long, RegionArena>(), new HashMap<Long, RegionArena>()};
    private final ArenaBatchPlan plan = new ArenaBatchPlan();
    private final List<Object> deferredRemarks = new ArrayList<Object>();
    private final boolean multiDraw;
    private IntBuffer multiFirsts = BufferUtils.createIntBuffer(256);
    private IntBuffer multiCounts = BufferUtils.createIntBuffer(256);

    private long capacityBytes = 0L;
    private long uploads = 0L;
    private long uploadedBytes = 0L;
    private long uploadNanos = 0L;
    private long sectionsDrawn = 0L;
    private long drawCalls = 0L;
    private long drawNanos = 0L;
    private long batchesIssued = 0L;
    private long blocksDrained = 0L;

    public ArenaBackend()
    {
        if (!GLContext.getCapabilities().OpenGL15)
        {
            throw new IllegalStateException("OpenGL 1.5 buffer objects are unavailable");
        }

        this.multiDraw = GLContext.getCapabilities().OpenGL14;
    }

    @Override
    public String name()
    {
        return "arena";
    }

    /** Floor to the 1024 grid - the region base vanilla's RenderList batching keys on. */
    static int regionMinus(int coordinate)
    {
        return coordinate - (coordinate & 1023);
    }

    static long regionKey(int minusX, int minusZ)
    {
        return ((long)(minusX >> 10) << 32) ^ ((minusZ >> 10) & 0xFFFFFFFFL);
    }

    @Override
    public void upload(Object renderer, int pass, MeshData mesh, int originX, int originY, int originZ, int glListBase)
    {
        long start = System.nanoTime();
        MeshHost host = (MeshHost)renderer;
        Object parked = host.vertex$mesh();
        ArenaSlots slots = parked instanceof ArenaSlots ? (ArenaSlots)parked : null;

        if (slots == null || slots.generation != this.generation)
        {
            slots = new ArenaSlots(this.generation);
            host.vertex$setMesh(slots);
        }

        ArenaSlots.Pass slot = slots.passes[pass];
        RegionArena previousArena = (RegionArena)slot.arena;
        ArenaBlock previousBlock = (ArenaBlock)slot.block;
        ArenaAllocator.Range previousRange = slot.range;

        if (mesh.isEmpty())
        {
            slot.clear();
        }
        else
        {
            RegionArena arena = arenaFor(pass, originX, originZ);
            ArenaBlock block = allocateBlock(arena, mesh.byteSize());
            ArenaAllocator.Range range = block.allocator.allocate(mesh.byteSize());

            try
            {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, block.buffer);
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, range.offset,
                    Staging.loadBaked(mesh, originX & 1023, originY, originZ & 1023, SECTION_SCALE));
            }
            finally
            {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            }

            slot.arena = arena;
            slot.block = block;
            slot.range = range;
            slot.buffer = block.buffer;
            slot.first = range.offset / MeshData.STRIDE;
            slot.count = mesh.vertexCount;
            slot.drawMode = mesh.drawMode;
            slot.formatBits = formatBits(mesh);
            slot.minusX = arena.minusX;
            slot.minusZ = arena.minusZ;
            block.residents.put(slot, renderer);
        }

        // Free AFTER the flip so no window exists where the section points at freed
        // space; the old range may have been drawn earlier this frame.
        if (previousRange != null)
        {
            previousBlock.allocator.free(previousRange);

            if (previousBlock != slot.block)
            {
                previousBlock.residents.remove(slot);
            }

            reclaim(previousArena, previousBlock);
            maybeStartDrain(previousArena);
        }

        ++this.uploads;
        this.uploadedBytes += mesh.byteSize();
        this.uploadNanos += System.nanoTime() - start;
    }

    @Override
    public boolean ownsSubmission()
    {
        return true;
    }

    @Override
    public void drawVisible(List<?> sections, int pass, double camX, double camY, double camZ)
    {
        long start = System.nanoTime();
        this.plan.reset();
        boolean ordered = pass == 1;

        for (int i = 0; i < sections.size(); ++i)
        {
            Object renderer = sections.get(i);

            if (!(renderer instanceof MeshHost))
            {
                continue;
            }

            Object parked = ((MeshHost)renderer).vertex$mesh();

            if (!(parked instanceof ArenaSlots))
            {
                continue;
            }

            ArenaSlots slots = (ArenaSlots)parked;

            if (slots.generation != this.generation)
            {
                continue;
            }

            ArenaSlots.Pass slot = slots.passes[pass];

            if (slot.count > 0)
            {
                this.plan.add(ordered, slot);
            }
        }

        if (!ordered)
        {
            this.plan.sortOpaqueBatches();
        }

        int currentFormat = 0;
        int boundBuffer = 0;

        try
        {
            for (int i = 0; i < this.plan.batchCount(); ++i)
            {
                ArenaBatchPlan.Batch batch = this.plan.batch(i);

                if (batch.buffer != boundBuffer)
                {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, batch.buffer);
                    boundBuffer = batch.buffer;
                }

                // Pointers capture the binding, so they re-issue per batch; the enables
                // are global and only toggle when the format actually changes.
                GL11.glVertexPointer(3, GL11.GL_FLOAT, MeshData.STRIDE, 0L);

                if ((batch.formatBits & 1) != 0)
                {
                    GL11.glTexCoordPointer(2, GL11.GL_FLOAT, MeshData.STRIDE, 12L);
                }

                if ((batch.formatBits & 2) != 0)
                {
                    Staging.clientActiveTexture(Staging.lightmapUnit());
                    GL11.glTexCoordPointer(2, GL11.GL_SHORT, MeshData.STRIDE, 28L);
                    Staging.clientActiveTexture(Staging.defaultUnit());
                }

                if ((batch.formatBits & 4) != 0)
                {
                    GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, MeshData.STRIDE, 20L);
                }

                if ((batch.formatBits & 8) != 0)
                {
                    GL11.glNormalPointer(GL11.GL_BYTE, MeshData.STRIDE, 24L);
                }

                currentFormat = applyClientState(currentFormat, batch.formatBits | 16);
                GL11.glPushMatrix();
                GL11.glTranslatef(
                    (float)((double)batch.minusX - camX),
                    (float)(0.0D - camY),
                    (float)((double)batch.minusZ - camZ));

                if (this.multiDraw && batch.size > 1)
                {
                    ensureMultiCapacity(batch.size);
                    // Buffer casts: Java 8 has no covariant IntBuffer.clear/flip, and a
                    // newer-JDK build must not emit descriptors the game's runtime lacks
                    // (see Staging's class comment).
                    ((java.nio.Buffer)this.multiFirsts).clear();
                    this.multiFirsts.put(batch.firsts, 0, batch.size);
                    ((java.nio.Buffer)this.multiFirsts).flip();
                    ((java.nio.Buffer)this.multiCounts).clear();
                    this.multiCounts.put(batch.counts, 0, batch.size);
                    ((java.nio.Buffer)this.multiCounts).flip();
                    GL14.glMultiDrawArrays(batch.drawMode, this.multiFirsts, this.multiCounts);
                    ++this.drawCalls;
                }
                else
                {
                    for (int range = 0; range < batch.size; ++range)
                    {
                        GL11.glDrawArrays(batch.drawMode, batch.firsts[range], batch.counts[range]);
                        ++this.drawCalls;
                    }
                }

                GL11.glPopMatrix();
            }
        }
        finally
        {
            applyClientState(currentFormat, 0);

            if (boundBuffer != 0)
            {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            }
        }

        this.sectionsDrawn += this.plan.itemCount();
        this.batchesIssued += this.plan.batchCount();
        this.drawNanos += System.nanoTime() - start;
    }

    @Override
    public void reset()
    {
        for (int pass = 0; pass < 2; ++pass)
        {
            for (RegionArena arena : this.arenas[pass].values())
            {
                for (int i = 0; i < arena.blocks.size(); ++i)
                {
                    GL15.glDeleteBuffers(arena.blocks.get(i).buffer);
                }
            }

            this.arenas[pass].clear();
        }

        this.capacityBytes = 0L;
        this.deferredRemarks.clear();
        ++this.generation;
    }

    @Override
    public long bufferBytes()
    {
        return this.capacityBytes;
    }

    @Override
    public long[] drainCounters()
    {
        long[] out = {this.uploads, this.uploadedBytes, this.uploadNanos,
            this.sectionsDrawn, this.drawCalls, this.drawNanos};
        this.uploads = 0L;
        this.uploadedBytes = 0L;
        this.uploadNanos = 0L;
        this.sectionsDrawn = 0L;
        this.drawCalls = 0L;
        this.drawNanos = 0L;
        return out;
    }

    @Override
    public String extraReport()
    {
        int blocks = 0;
        long live = 0L;
        int freeBlocks = 0;
        int largestFree = 0;

        for (int pass = 0; pass < 2; ++pass)
        {
            for (RegionArena arena : this.arenas[pass].values())
            {
                blocks += arena.blocks.size();

                for (int i = 0; i < arena.blocks.size(); ++i)
                {
                    ArenaAllocator allocator = arena.blocks.get(i).allocator;
                    live += allocator.liveBytes();
                    freeBlocks += allocator.freeBlockCount();

                    if (allocator.largestFreeBlock() > largestFree)
                    {
                        largestFree = allocator.largestFreeBlock();
                    }
                }
            }
        }

        // freePct is headroom (unreserved capacity), not fragmentation - the previous
        // fragPct label conflated the two. Fragmentation shows as freeBlocks growing
        // while largestFreeKB shrinks: many small holes instead of a few big ones.
        long freePct = this.capacityBytes > 0L
            ? (this.capacityBytes - live) * 100L / this.capacityBytes : 0L;
        String report = " arena[blocks=" + blocks
            + " capMB=" + this.capacityBytes / (1024L * 1024L)
            + " liveMB=" + live / (1024L * 1024L)
            + " freePct=" + freePct
            + " freeBlocks=" + freeBlocks
            + " largestFreeKB=" + largestFree / 1024
            + " batches=" + this.batchesIssued
            + " drained=" + this.blocksDrained
            + " multiDraw=" + this.multiDraw + "]";
        this.batchesIssued = 0L;
        this.blocksDrained = 0L;
        return report;
    }

    @Override
    public List<Object> drainDeferredRemarks()
    {
        if (this.deferredRemarks.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        List<Object> out = new ArrayList<Object>(this.deferredRemarks);
        this.deferredRemarks.clear();
        return out;
    }

    private RegionArena arenaFor(int pass, int originX, int originZ)
    {
        int minusX = regionMinus(originX);
        int minusZ = regionMinus(originZ);
        Long key = Long.valueOf(regionKey(minusX, minusZ));
        RegionArena arena = this.arenas[pass].get(key);

        if (arena == null)
        {
            arena = new RegionArena(minusX, minusZ);
            this.arenas[pass].put(key, arena);
        }

        return arena;
    }

    /** First non-draining block that fits, else a fresh block (GL storage allocated). */
    private ArenaBlock allocateBlock(RegionArena arena, int bytes)
    {
        for (int i = 0; i < arena.blocks.size(); ++i)
        {
            ArenaBlock block = arena.blocks.get(i);

            if (!block.draining && block.allocator.largestFreeBlock() >= roundUp(bytes))
            {
                return block;
            }
        }

        int capacity = Math.max(BLOCK_BYTES, roundUp(bytes));
        int buffer = GL15.glGenBuffers();

        try
        {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacity, GL15.GL_STATIC_DRAW);
        }
        finally
        {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        ArenaBlock block = new ArenaBlock(buffer, capacity);
        arena.blocks.add(block);
        this.capacityBytes += capacity;
        return block;
    }

    /** Delete a block whose drain completed (or that emptied while draining). */
    private void reclaim(RegionArena arena, ArenaBlock block)
    {
        if (block.draining && block.allocator.liveBytes() == 0)
        {
            GL15.glDeleteBuffers(block.buffer);
            arena.blocks.remove(block);
            this.capacityBytes -= block.capacity;
        }
    }

    /**
     * Compaction by rebuild: once a multi-block arena is under half occupancy, retire
     * its emptiest block - stop allocating into it and re-mark its resident sections
     * dirty so the normal rebuild path migrates them; the last free deletes the block.
     */
    private void maybeStartDrain(RegionArena arena)
    {
        ArenaBlock candidate = pickDrainCandidate(arena.blocks);

        if (candidate == null)
        {
            return;
        }

        if (candidate.allocator.liveBytes() == 0)
        {
            // Nothing resident: reclaim immediately instead of waiting for a rebuild.
            GL15.glDeleteBuffers(candidate.buffer);
            arena.blocks.remove(candidate);
            this.capacityBytes -= candidate.capacity;
            ++this.blocksDrained;
            return;
        }

        candidate.draining = true;
        ++this.blocksDrained;
        this.deferredRemarks.addAll(candidate.residents.values());
    }

    /**
     * Pure drain decision (unit-tested): only multi-block arenas, only when total
     * occupancy is under 50%, never while another block is already draining. The LAST
     * block - the allocation frontier, where new and migrated meshes land - is never a
     * candidate, and a candidate must itself be under 25% full: without both guards,
     * creating a block dips arena occupancy below the threshold and the fresh, emptiest
     * block immediately drains, whose migration creates the next block - a measured
     * 27,535 drains and 3.2 GB of re-uploads per minute on the first arena soak.
     */
    static ArenaBlock pickDrainCandidate(List<ArenaBlock> blocks)
    {
        if (blocks.size() < 2)
        {
            return null;
        }

        long capacity = 0L;
        long live = 0L;

        for (int i = 0; i < blocks.size(); ++i)
        {
            if (blocks.get(i).draining)
            {
                return null;
            }

            capacity += blocks.get(i).capacity;
            live += blocks.get(i).allocator.liveBytes();
        }

        if (live * 2L >= capacity)
        {
            return null;
        }

        ArenaBlock emptiest = null;

        for (int i = 0; i < blocks.size() - 1; ++i)
        {
            ArenaBlock block = blocks.get(i);

            if (block.allocator.liveBytes() * 4L < (long)block.capacity
                && (emptiest == null || block.allocator.liveBytes() < emptiest.allocator.liveBytes()))
            {
                emptiest = block;
            }
        }

        return emptiest;
    }

    private static int formatBits(MeshData mesh)
    {
        return (mesh.hasTexture ? 1 : 0) | (mesh.hasBrightness ? 2 : 0)
            | (mesh.hasColor ? 4 : 0) | (mesh.hasNormals ? 8 : 0);
    }

    /** Toggle client states to match the wanted bits (16 = vertex array); returns wanted. */
    private static int applyClientState(int current, int wanted)
    {
        if (((current ^ wanted) & 16) != 0)
        {
            setClientState(GL11.GL_VERTEX_ARRAY, (wanted & 16) != 0);
        }

        if (((current ^ wanted) & 1) != 0)
        {
            setClientState(GL11.GL_TEXTURE_COORD_ARRAY, (wanted & 1) != 0);
        }

        if (((current ^ wanted) & 2) != 0)
        {
            Staging.clientActiveTexture(Staging.lightmapUnit());
            setClientState(GL11.GL_TEXTURE_COORD_ARRAY, (wanted & 2) != 0);
            Staging.clientActiveTexture(Staging.defaultUnit());
        }

        if (((current ^ wanted) & 4) != 0)
        {
            setClientState(GL11.GL_COLOR_ARRAY, (wanted & 4) != 0);
        }

        if (((current ^ wanted) & 8) != 0)
        {
            setClientState(GL11.GL_NORMAL_ARRAY, (wanted & 8) != 0);
        }

        return wanted;
    }

    private static void setClientState(int state, boolean on)
    {
        if (on)
        {
            GL11.glEnableClientState(state);
        }
        else
        {
            GL11.glDisableClientState(state);
        }
    }

    private void ensureMultiCapacity(int size)
    {
        if (this.multiFirsts.capacity() < size)
        {
            int capacity = Integer.highestOneBit(size - 1) * 2;
            this.multiFirsts = BufferUtils.createIntBuffer(capacity);
            this.multiCounts = BufferUtils.createIntBuffer(capacity);
        }
    }

    private static int roundUp(int bytes)
    {
        int quanta = (bytes + QUANTUM - 1) / QUANTUM;
        return quanta * QUANTUM;
    }
}
