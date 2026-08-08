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

**Staleness follow-up (post #75):** builds now carry a per-renderer reposition stamp
(bumped by the setPosition head hook, checked at worker entry and drain) on top of the
global generation. The stronger design remains open: workers still execute the vanilla
body against the *live* WorldRenderer, so a reposition mid-body can tear the block
snapshot even though the result is then discarded by stamp. Building against an immutable
section snapshot (copy block/light data on submit, never touch the live renderer off the
client thread) removes that class of race entirely; it requires replacing the wrapped
vanilla body with an independent tessellation loop and belongs to this phase-2 item.

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

**Remaining piece - atlas registration:** CTM tiles live in packs as separate images
(mcpatcher/ctm/<name>/0.png ... 46.png) and vanilla never registers them, so they have no
atlas coordinates and no IIcon exists to return. The dispatch therefore cannot be written
until those sprites are registered during atlas construction, which needs a hook on
TextureMap's stitch/registration path to inject extra sprites and retain their IIcons.
That is the single blocking piece; connectivity, parsing, rule indexing and pack scanning
are all merged and tested.

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

## 5b. Renderer backend program (stages 1-3 merged, opt-in since 0.4.0)

**Design (fixed in docs/RENDERER.md):** staged migration
`legacy display lists -> managed section meshes -> per-section VBOs -> shared arenas
with per-region batched submission`, motivated by the measured baseline
(docs/benchmarks/renderer-baseline.md: 67-76% of wall time in glCallLists, ~6.7 us per
section, linear). Geometry production is separated from GPU representation behind
`MeshData`/`RenderBackend`; workers stay GL-free; one client-thread install path owns
every GL byte; everything is opt-in behind the `renderer` key with `legacy` weaving
nothing.

**Merge gate (stages 1-2, met - PR #122):** zero self-disables across RD8/RD16 soaks and
churn stress; structural build-audit parity with legacy within the method's control
noise; frame times neutral under `displaylist` and improved under `vbo`; full teardown
across world/RD changes; the disable path re-marks and heals through the vanilla
renderer.

**Merge gate (stage 3, met - see docs/benchmarks/renderer-backends.md):** arena
submission approaching O(visible regions) per pass (measured 3.7 batches/frame at RD8,
~51 commands/frame at RD16 vs ~1,700 per-section), stable memory under fragmentation
stress (26-28% steady, 56% under teleport storms settling back, compaction drains only
under genuine churn), and the same structural-parity and zero-disable soak bars as
stage 2 - including the stress-driver gauntlet's render-distance flips, resource
reloads and world rejoins.

**Remaining:** real-world miles on the opt-in backends, then a default-on decision
through the same promotion gate multicore passed (human fly-through sign-off on top of
the automated bars).

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
