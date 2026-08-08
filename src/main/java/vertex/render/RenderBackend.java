package vertex.render;

import java.util.List;

/**
 * GPU representation strategy for chunk-section meshes. Exactly one backend is live per
 * session (the "renderer" config key, resolved at class-load like the multicore flag);
 * every method here runs on the client thread only - workers produce {@link MeshData}
 * and never see a backend.
 *
 * The contract is written for three implementations so migrating between them is an
 * increment, not a rewrite:
 *  - display lists (compatibility default of the managed path): upload compiles the
 *    vanilla per-section list ids, submission stays with vanilla's RenderList batching;
 *  - per-section VBOs: upload owns buffer objects, submission walks the visible list;
 *  - shared arenas (designed, see docs/RENDERER.md): sections hold (buffer, offset,
 *    count) ranges and submission batches whole 1024-block regions.
 *
 * Ownership rules, uniform across backends:
 *  - A section owns at most one live mesh per pass; upload replaces atomically.
 *  - Stale worker builds never reach upload (the multicore stamp/generation gate runs
 *    first); a backend never validates game state itself.
 *  - reset() releases every GPU resource the backend allocated and invalidates every
 *    per-section slot it handed out; it is the one call for world transitions, render
 *    distance changes (both arrive via loadRenderers) and the disable path. Slots from
 *    before a reset are dead by generation stamp, never reused.
 *  - Failures propagate as exceptions; the orchestrator disables the managed renderer
 *    and falls back to the vanilla path, so a backend never retries internally.
 */
public interface RenderBackend
{
    String name();

    /**
     * Install one pass's geometry for a section, replacing whatever the section held for
     * that pass. An empty mesh must still overwrite (a section can legitimately lose all
     * its geometry in a pass). originX/Y/Z is the section's world origin at build time;
     * glListBase is the section's vanilla display-list base id (display-list backend only).
     */
    void upload(Object renderer, int pass, MeshData mesh, int originX, int originY, int originZ, int glListBase) throws Exception;

    /** True when the backend replaces vanilla's RenderList/glCallLists submission. */
    boolean ownsSubmission();

    /**
     * Draw the given sections (already visibility-filtered and ordered by vanilla's pass
     * walk - back-to-front for the translucent pass) against the interpolated camera.
     * Only called when {@link #ownsSubmission()} is true.
     */
    void drawVisible(List<?> sections, int pass, double camX, double camY, double camZ) throws Exception;

    /** Release every GPU resource and invalidate all handed-out section slots. */
    void reset();

    /** Current GPU buffer memory attributable to this backend, in bytes (0 if none). */
    long bufferBytes();

    /**
     * Drain diagnostics counters accumulated since the last call:
     * [uploads, uploadedBytes, uploadNanos, sectionsDrawn, drawCallsIssued, drawNanos].
     */
    long[] drainCounters();
}
