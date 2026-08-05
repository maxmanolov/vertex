# Remaining feature program

Every feature below is **in progress by design commitment**: the design is fixed here, the
merge gate is explicit, and implementation proceeds top-down. Nothing in this file may be
merged without meeting its gate. (Everything else in FEATURES.md is already implemented,
matched, surpassed, or excluded with justification.)

## 1. Multi-core chunk building - phase 2 (flagship)

**Design (proven out-of-tree in a private 1.7.10 client, reimplemented here against
vanilla):** a pool of `min(cores-2, 6)` CPU-only workers tessellates chunk sections into
per-build Tessellator instances; the client thread submits builds, compiles finished
tessellations into display lists, and swaps them live. No shared or offscreen GL context.
Phase 1 (merged: the total `Tessellator.instance` redirect) makes the per-thread lookup
possible: workers bind their own instance, every other thread sees the vanilla one.

Vanilla-specific work: `WorldRenderer.updateRenderer` builds display lists inline, so the
transformer must split it - geometry loop redirected to a build-capture hook, GL
compilation replayed on the client thread. Stale results are discarded by position stamp;
a mark during build survives as a re-mark (needsUpdate cleared before the chunk snapshot).
Smooth (time-sliced) and lazy chunk loading fold into this pipeline as budget policies on
the same queue, not separate features.

**Merge gate:** boots and renders identically with workers=0 (identity mode); with workers
on, survives 15+ minutes of in-world flight with zero hook self-disables; diagnostics
counters show worker builds; no visual holes at section boundaries.

## 2. Connected textures (CTM)

**Design:** parse the community-documented `mcpatcher/ctm` resource-pack format
(`.properties`: method/tiles/connect/faces) into an immutable rule set at resource-load;
at tessellation time a pure function `(block, side, neighbors) -> sprite` consults a
precomputed lookup keyed by the 8-neighbor bitmask - no allocation on the render path.
Implemented from public format documentation and sample packs only.

**Status:** core merged - vertex.ctm.CtmProperties (parser: method aliases, tile ranges,
faces, connect defaults) and BlobConnectivity (256-mask -> 47-class canonicalization,
property-tested: exactly 47 classes emerge from the corner-relevance rule alone). The
class-to-atlas-position table is deliberately a calibration seam: it will be filled against
a reference pack when the sprite hook lands, because a guessed order would be
self-consistent-but-wrong in exactly the way unit tests cannot catch.

**Merge gate (unchanged):** a reference CTM pack (glass borders + bookshelves) renders
correctly on all six faces and at chunk-section boundaries; disabled state is bit-identical
to vanilla; no measurable frame-time regression with CTM-less packs.

## 3. Custom sky / random entities (custom colors, natural textures done; emissive excluded)

Same shape as CTM: parse documented pack formats into immutable data, hook one narrow
vanilla decision point each (biome colorizer, sky render pass, sprite emission overlay,
entity texture resolution, sprite variant selection). Ordered after CTM because they share
the pack-parsing infrastructure it introduces.

**Merge gate per feature:** reference pack renders correctly; absence of pack files leaves
vanilla behavior bit-identical.

## 4. Better grass / better snow

Block-renderer decision hooks (side-grass texture substitution; snow-layer continuation
under fences). Small, but touches the hottest render loop - lands only with before/after
frame-time numbers from the diagnostics counters.

## 5. Fast Render investigation

**Resolved: excluded with data.** The call-count diagnostic landed and measured 38-41%
redundancy at ~140 redundant calls/frame - microseconds of driver time against the 5%
frame-time threshold. See docs/benchmarks/fastrender.md.

## 6. Dynamic lights

**Design:** each frame, collect dynamic sources (held or dropped light-emitting items,
burning entities) into a small array of (pos, level) entries. Hook the renderer-side
brightness sampling (the mixed-brightness lookup used during tessellation) to max() in a
distance-attenuated contribution from nearby sources - pure function, no world mutation,
no lighting engine involvement, so multiplayer-safe by construction. As sources move
across section boundaries, re-mark affected sections through the existing bounded
interactive-priority path (at most a handful of sections per move, capped per frame).

**Merge gate:** holding a torch lights caves within ~8 blocks with smooth falloff; frame
time regression under 2% with diagnostics on; sections never stale-light behind a moving
source by more than one rebuild interval; disabled state bit-identical to vanilla.
