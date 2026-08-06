# Multicore status correction (rc1 era)

## What the counters said, and why they were wrong

Every multicore soak reported healthy numbers: replays flowing, zero self-disables,
queues draining under churn. All of it was real telemetry of hollow work. The submit
interception returns true, vanilla's loop then clears needsUpdate on the client, and the
worker's vanilla body - whose first line checks that flag - no-oped. Workers "built"
nothing; replay swapped never-compiled display lists; the rebuild counter counted the
swaps. No mechanical gate distinguished an empty build from a full one.

It was caught by the first instrument that looked at OUTPUT instead of activity: a
framebuffer capture of a multicore scene showing empty sky where a grass field belonged.

## Current state after the flag fix

- Worker bodies execute (dirty flag restored at worker entry; the body re-clears it).
- The quiet-world perpetual rebuild loop is gone; capture drain gates open with workers on.
- New defect, visible and reproducible: replayed sections render FRAGMENTS of their
  geometry (multicore-replay-fragments.png) - underground features without covering
  surface - while vanilla-built distant sections render correctly. The capture/replay
  path loses quads; cause under investigation, starting from per-pass capture accounting
  and build-time world-state completeness.

## Lesson institutionalized

Activity counters cannot gate a renderer. The capture harness - even without a converged
byte-comparison floor - is now the mandatory evidence for any multicore status claim:
a picture of terrain, or no claim.

## Investigation log: fragment defect

Per-section build audit (multicore on): near-surface sections split into two failure
populations - 34/60 built EMPTY (zero bytes, skip0=true) and one drew 7.4MB in a single
replay, roughly a hundred sections' geometry at one section's translation. The picture's
floating fragments are that mega-list.

Tessellator lifecycle audit (identity-tracked borrow/bind/recycle): **pool exonerated** -
293 borrows, 288 recycles, zero double-borrows, every borrow paired with exactly one
pass-capture. Ownership is exclusive, so the mega-buffer was filled through its own
build's body.

Remaining suspects, in investigation order:
1. Body re-execution per build (one Build delivered/executed repeatedly, appending into
   the same held tessellators) - check BuildQueue delivery and the vertex$rebuild path.
2. interceptPostRender slot accounting vs the vanilla body's actual pre/post cadence -
   read blo.a(Lsv;)V bytecode and verify one pre and one post per active pass, no
   mid-pass draws.
3. Zero-byte population: worker-side ChunkCache built with incomplete neighbors
   (extendedLevelsInChunkCache short-circuit) without a heal re-mark surviving the
   replay pipeline.

Diagnostics for all of this are now permanent behind -Dvertex.test.buildAudit.
