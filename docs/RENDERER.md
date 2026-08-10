# The Vertex renderer program

Goal: leave Vertex with a renderer architecture where
`vanilla display lists -> per-section VBOs -> shared VBO arenas with batched submission`
is an incremental evolution - each stage swaps one component behind a stable seam, none
is a rewrite, and nothing along the way is throwaway. The baseline measurement that
justifies the program is docs/benchmarks/renderer-baseline.md: at RD16 the client thread
spends 76% of wall time executing one display list per visible section (~6.7 us each,
linear), while traversal, visibility and rebuild together stay under 4%.

The `renderer` config key selects the stage; since the 2026-08-08 promotion the shared
arena is the declared default for new and missing-key configurations, and
`renderer=legacy` still weaves nothing (byte-identical vanilla). Existing configurations
retain explicit values, including legacy defaults generated before the promotion.
Every stage stayed opt-in until it had visual parity, performance evidence and
real-session miles - the arena promotion closed that gate through the same shape as
multicore's (automated bars, then maintainer sign-off in real sessions).

## 1. The vanilla pipeline being replaced (bytecode-verified)

Per section (`WorldRenderer`), vanilla owns three display lists: `base+0` opaque,
`base+1` translucent, `base+2` occlusion box. A rebuild (`updateRenderer`) tessellates
each pass in world coordinates with the tessellator translation set to `-origin` (so the
buffer content is section-local), then `preRenderBlocks`/`postRenderBlocks` compile it:
`glNewList(base+pass) / push / glTranslatef(clip) / the 1.000001 anti-crack scale about
the section center / dereferenced vertex arrays / pop / glEndList`, where
`clip = (x & 1023, y, z & 1023)`. Submission (`renderSortedRenderers`) walks the pass's
renderers in sorted order (reversed for pass 1), collects visible non-empty sections into
`glRenderLists`, groups them into at most four `RenderList`s keyed by the exact
`posXMinus/YMinus/ZMinus` 1024-block region, and `renderAllRenderLists` draws each group
with one `glTranslatef(base - camera)` plus one `glCallLists(IntBuffer)`, bracketed by
the lightmap enable/disable. Translucent resorts (`updateRendererSort`) restore the saved
`TesselatorVertexState`, whose `getVertexState` sorts the raw buffer back-to-front **in
place** before returning the state copy, and recompile the pass-1 list.

Two facts carry the whole design:

- **Vertex data is already section-local and backend-neutral** the moment tessellation
  ends; only the last step (list compile) welds it to a GPU representation.
- **The 1024-region key vanilla batches by is exactly the arena's natural partition**:
  bake `clip` into the vertices and every section in a region shares one camera
  translation, which is what makes multi-draw submission legal.

## 2. The seam: section meshes, one install path, one backend

With `renderer=displaylist|...` the managed pipeline takes over geometry transport;
`renderer=legacy` leaves every shipped path byte-identical (the weave is gated on the
mode, resolved once at class load).

- `vertex.render.MeshData` - immutable CPU geometry for one (section, pass): the exact
  32-byte-stride interleaved stream, section-local, plus format flags. Produced by
  **workers** (multicore capture extracts it right after the pass finishes and pools the
  tessellator worker-side) and by the **client capture** (synchronous rebuilds,
  translucent resorts - the same pre/post seam, same-thread), never by GL code.
- `vertex.hooks.VertexRenderer` - the orchestrator: owns the mode, the client capture,
  the one `install()` path (backend upload + `bytesDrawn`/`vertexState` bookkeeping the
  vanilla renderer expects), submission interception, grid resets, diagnostics, and the
  failure fallback.
- `vertex.render.RenderBackend` - the GPU representation strategy. All calls client
  thread. Ownership rules are in its javadoc; the short form: one live mesh per
  (section, pass), upload replaces atomically, staleness is gated before install (the
  multicore stamp/generation checks), `reset()` is the single release point, failures
  disable the pipeline rather than retry.
- `vertex.api.MeshHost` - one injected `Object` slot on WorldRenderer so a backend
  reaches its per-section state without reflection or map lookups on the draw path.

Stale builds cannot install: worker results pass the existing reposition-stamp,
XYZ and generation gates first, and a build captured under one pipeline never installs
under the other (managed-vs-legacy mismatch discards and re-queues).

## 3. Stage ladder

**Stage 1 - managed display lists (`renderer=displaylist`, this stage).** Upload
compiles the same list ids with the same interior GL as vanilla; submission stays with
vanilla's RenderList batching. Visual parity is checkable pixel-for-pixel and performance
must be neutral (same submission path). This proves the transport: workers produce
MeshData, every GL byte flows through the backend.

**Stage 2 - per-section VBOs (`renderer=vbo`).** Upload becomes
`glBufferData(GL_ARRAY_BUFFER, staged, GL_STATIC_DRAW)` into a per-(section, pass)
buffer; the backend owns submission (`interceptSubmit` head guard on
renderAllRenderLists): inside the lightmap bracket, walk the pass's visible list in
vanilla's order and per section `bind / pointers / glPushMatrix / glTranslatef(origin -
camera, double-subtract then cast) / anti-crack scale / glDrawArrays / glPopMatrix`.
Buffers are slot-bound (allocated lazily per grid slot, content replaced on rebuild,
freed only at reset) so steady state allocates nothing. Validation: vertex format
offsets 0/12/20/24/28, both passes, pass-1 ordering via the in-place-sorted extraction,
lightmap client-texture pointer on unit 1, dynamic-light rebuilds, cleanup at world
change/RD change/shutdown.

**Stage 3 - shared arenas + batched submission (`renderer=arena`, built).** See §5;
results in docs/benchmarks/renderer-backends.md.

## 4. Lifecycle rules (all stages)

| event | what happens |
|---|---|
| rebuild finishes (worker) | stamp/generation/pipeline gates -> `install()` replaces that pass's mesh; discard requeues the section dirty |
| rebuild finishes (client) | same install, same thread, no gates needed |
| translucent resort | flows through the client capture; replaces only pass 1 |
| pass never started in a rebuild | old mesh kept; vanilla's `skipRenderPass` flag gates it out of submission (same as vanilla's stale-list behavior) |
| pass started, zero quads | empty mesh **still installs** (overwrites stale geometry); draw skips it |
| section repositioned | `setPosition -> setDontDraw` masks both passes until the forced rebuild installs fresh meshes; stored origin means a stale mesh could never draw at the new position anyway |
| loadRenderers (world change, RD change, F3+A) | `backend.reset()`: every GL resource freed, every handed-out slot dead by generation; new grid starts empty |
| shutdown | world unload runs loadRenderers' reset; remaining GL dies with the context |
| upload/draw failure | `VertexRenderer.disable()`: pipeline off for the session, backend resources released, every section re-marked dirty (set-backed, not O(n^2)) so the vanilla path rebuilds its display lists; degraded seconds, never a crash |
| multicore in flight during any of the above | unchanged multicore rules; the mesh path adds no new cross-thread state (worker writes land in the build object, published through the existing finished queue) |

Threading: workers tessellate and extract (CPU only, per-thread tessellators, pooled
worker-side); the client thread does every upload, draw, delete and reset. Config and
mode are load-time constants, so there is no runtime toggling surface to race.

## 5. Shared-arena stage design (implemented as `ArenaBackend`; decisions below are load-bearing)

**Partition.** One arena per (1024-block region, pass) actually visible - at RD16 that
is at most 4 regions x 2 passes. Sections enter the arena of the region their origin
falls in; `clip` coordinates are baked into the vertices at staging time (add
`(x&1023, y, z&1023)` plus the anti-crack scale about the section center - float math on
values < 1040, exact enough), so an arena draws with **one** camera translate.

**Allocation.** Free-list allocator per arena, first-fit with address-ordered
coalescing on free; allocations rounded to a 1 KB quantum to bound fragmentation
bookkeeping. Section meshes are 1-200 KB; a 16 MB arena block holds hundreds. When an
arena block is full, add another block (arena = list of GL buffers, not one growing
buffer) - growing never copies live meshes, so there is no resize stall and no
device-side copy dependency (GL 1.5 has no glCopyBufferSubData; 3.1 is not assumed).

**Upload.** `glBufferSubData` into the allocated range from the staging buffer. Replace
= allocate-new, write, flip the section's `(buffer, offset, count)`, free-old - never
in-place overwrite of a range a queued frame may still read; the frame boundary
(uploads and draws share the client thread) makes the flip safe without fences.

**Freeing and fragmentation.** Frees coalesce with neighbors; per-arena occupancy is
tracked (`liveBytes / capacity`). When occupancy of a multi-block arena drops below 50%,
retire its emptiest block: mark it draining (no new allocations), re-mark the sections
still resident there dirty through the normal path, and delete the block when its last
range frees - compaction by rebuild, reusing the one code path that already exists,
instead of a bespoke mesh-moving copier. Worst case cost equals a partial RD change.
Two guards make this stable (learned from a measured 27,535 drains/min thrash on the
first arena soak): the allocation-frontier block (the last one, where new and migrated
meshes land) is never a candidate - a freshly created block is always the emptiest and
its own creation is what dips occupancy - and a candidate must itself be under 25%
full, so a well-packed block is never churned through rebuilds for a marginal reclaim.

**Submission.** Per pass: for each visible region arena, one bind + one pointer setup +
`glMultiDrawArrays(first[], count[])` over the visible resident sections in vanilla's
walk order (LWJGL exposes it; fallback loop of `glDrawArrays` costs one JNI call per
section but no rebinds/pointer churn and stays correct on GL < 1.4). Pass-1 ordering:
the walk is already back-to-front across sections, and ranges within one multi-draw
execute in array order, so translucency ordering is exactly vanilla's.

**Translucent resorts** re-upload a section's pass-1 range (same allocate-write-flip);
sort state stays CPU-side as today (`TesselatorVertexState` on the renderer).

**Batching regimes (as implemented in `ArenaBatchPlan`, unit-tested):** opaque merges
any same-(buffer, format, mode, region) sections regardless of walk position - a handful
of batches per visible region; translucent merges only consecutive runs, so multi-draw
array order reproduces vanilla's back-to-front walk exactly - deliberately less
aggressive, never incorrect. Sections resident in a draining block keep drawing until
their rebuild migrates them, so compaction has no visual gap.

## 6. Instrumentation and gates

- `-Dvertex.profileRender=true` - phase split (clip / traversal / submit / update) and
  section counters, the baseline instrument; works identically under every backend
  because the brackets time the vanilla entry points around whichever path runs.
- Diagnostics line (managed modes): `renderer=<name> meshUploads meshUploadKB
  meshUploadMs` plus, when the backend owns submission, `meshDrawn meshDrawCalls
  meshDrawMs meshBufMB`. Frame-time p50/p99/max, heap and GC were already there.
- Stage gates: stage 1 - pixel-identical captures vs legacy, frame times within noise,
  soak with zero self-disables under churn; stage 2 - same visual gates plus a measured
  submit-time reduction at RD16 and clean teardown across world/RD changes; stage 3 -
  submit time approaching O(regions) and stable memory under fragmentation stress.
