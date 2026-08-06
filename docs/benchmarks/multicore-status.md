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
