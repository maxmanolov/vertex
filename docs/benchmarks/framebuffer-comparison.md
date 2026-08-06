# Automated framebuffer comparison (status: experimental, not yet a gate)

## What it is

`-Dvertex.test.shotDir=path` captures three fixed-angle front-buffer frames of a pinned
scene (world-spawn anchor, pinned time on client AND integrated server, weather strengths
zeroed, peaceful difficulty, GUI hidden, screens closed, render distance pinned) as PNG +
raw RGB, for exact pixel comparison between runs that differ only in `-Dvertex.multicore`.
The driver script copies a golden world before every run so no run's saves contaminate the
next, and an off-vs-off control pair establishes the method's noise floor before any
off-vs-on comparison is read.

## What it has demonstrated

- Cross-run geometry is identical where measured: the spatial diff mask of a control pair
  showed every terrain silhouette edge aligned to the pixel, with divergence confined to
  full-surface brightness (lightmap inputs), later isolated to server weather state and
  chunk-streaming completion.
- After time + weather pinning, one angle converged to a 5.8% pixel difference on an
  off-vs-off control (remaining delta: streaming completion and an animated toast).

## Why it is not yet a pass/fail gate

The control noise floor is not yet below the threshold where an off-vs-on difference
would be attributable to the renderer. Known remaining work, in order:

1. Gate captures on a fully drained build queue counting live (non-null) entries -
   vanilla's queue retains nulled slots, so raw size never reaches zero.
2. Pin render distance BEFORE world join (a mid-run pin forces loadRenderers and
   invalidates the settled state it was meant to protect).
3. Exclude or despawn passive mobs in frame; suppress the achievement toast.

## Consequence for release verification

Multi-core visual sign-off remains on the MANUAL test checklist (fly-through with
`-Dvertex.multicore=true`). The machine evidence gathered so far - silhouette-exact
control masks and ~10k worker-built sections replaying without geometry faults in stress
runs - is strong but is not claimed as a substitute for eyes on the screen.
