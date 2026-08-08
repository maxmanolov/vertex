# Renderer backend stage results

Companion to renderer-baseline.md (the pre-backend phase split) and docs/RENDERER.md
(the design). Same machine, same scenario protocol: standing at spawn in the soak
world, peaceful, uncapped, multicore on, steady-state windows after the build drain.
Apple-silicon macOS GL - absolute numbers are driver-specific, the shape transfers.

## Stage 1: managed display lists (`renderer=displaylist`)

Purpose: prove the section-mesh transport (workers produce backend-neutral MeshData,
one client-thread install path owns all GL) at guaranteed neutrality - submission stays
vanilla's RenderList batching.

- RD8 soak, 4 min: frame times identical to legacy (ftP50 3.7 ms, ftP99 6.7 vs 6.2-7.7
  legacy), identical scene composition (398 rendered / 3,459 empty / 64 translucent),
  1,890 mesh uploads/min through the backend, zero self-disables, full drain.
- Structural parity (per-section build audits on pinned RD4 captures): 1,292/1,296
  sections byte-identical bytesDrawn + pass flags vs a legacy run. The 4 differing
  sections are the anchor-adjacent water sections, where bytesDrawn accumulates per
  translucent resort - a resort-count artifact, not geometry. The legacy-vs-legacy
  control pair differs in 140 sections (random-tick world drift), so the managed
  pipeline sits far inside the method's own noise floor.

## Stage 2: per-section VBOs (`renderer=vbo`)

Upload = glBufferData into slot-bound per-(section, pass) buffers; the backend owns
submission (bind + pointers + vanilla section transform + glDrawArrays per visible
section, in vanilla's walk order).

| steady state              | legacy (baseline) | vbo          | change |
|---------------------------|-------------------|--------------|--------|
| RD8 fps                   | ~248              | ~730         | 2.9x   |
| RD8 submit per frame      | 2.72 ms           | 0.42 ms      | 6.5x less |
| RD8 ftP50 / ftP99         | 3.7 / ~7 ms       | 1.2 / 3.2 ms |        |
| RD16 fps                  | ~92               | ~306         | 3.3x   |
| RD16 submit per frame     | 8.26 ms           | 1.98 ms      | 4.2x less |
| RD16 ftP50 / ftP99        | ~10 / ~18 ms      | 2.7 / 5.2 ms |        |
| per-section submit        | 6.6-6.8 us        | 1.0-1.3 us   | ~5x less |

Notes: the RD16 vbo run drew more sections than the baseline run (1,485 vs 1,245;
different world snapshot state), so the per-section figures are the fair comparison.
Buffer memory is now first-class and accounted: 70 MB at RD8, 239 MB at RD16
(vanilla's equivalent display-list memory exists but is driver-hidden).

- Churn stress (RD8, ~480 interactive promotions+rebuilds/min, 3 min): queue stays
  drained, ftP50 1.2 ms / ftP99 2.7 ms / ftMax 5.0 ms, both capture paths (worker
  builds and synchronous client rebuilds) live, zero self-disables.
- Structural parity: 1,292/1,296 sections byte-identical vs legacy - the same 4
  resort-accumulator sections and 35x inside the 140-section control noise, identical
  to the stage-1 result.
- Zero self-disables across every vbo run (RD8, RD16, churn, capture).

## Reading, and what stage 3 attacks

VBO submission removed the display-list interpretive overhead (~6.7 us -> ~1.2 us per
section) and tripled steady-state fps at both distances on this driver. What remains
per section is bind + pointer setup + one draw call; at RD16 that is still ~60% of the
(now much shorter) frame. That per-section term is exactly what the shared-arena stage
(docs/RENDERER.md section 5) eliminates: sections in one 1024-region share a buffer and
a camera translate, so a pass becomes a handful of multi-draw submissions. The measured
ceiling math: ~1,500 visible sections x ~1.2 us predicts ~1.8 ms of submit at RD16;
arenas target O(visible regions) ~= 4-8 batches per pass.
