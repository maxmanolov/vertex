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

## Final rc1 attempt

After the live-entry drain gate and a pre-launch render-distance pin (the mid-run pin
was forcing the reload it guarded against), the off-vs-off control still measured 66-74%
on this scene. Eleven iterations eliminated confounds one at a time (dead-player fixture
state, cross-run world mutation, server-physics deaths, pause menu, height race, server
time sync, weather strength, respawn scatter) and once reached 5.8% on a single angle,
proving convergence is possible - but at least one nondeterminism source remains
unidentified, and the time-box for rc1 is spent. The tool stays in the tree with this
record so the next attempt starts from the full confound list instead of rediscovering it.

## Consequence for release verification

Multi-core visual sign-off remains on the MANUAL test checklist (fly-through with
`-Dvertex.multicore=true`). The machine evidence gathered so far - silhouette-exact
control masks and ~10k worker-built sections replaying without geometry faults in stress
runs - is strong but is not claimed as a substitute for eyes on the screen.

## Motion-burst mode (experimental, added post-rc2)

`-Dvertex.test.motion=true` flies the camera smoothly along +Z while capturing a
sequential frame burst, for consecutive-frame temporal analysis (section flicker and
pop-in produce delta spikes steady parallax cannot). Current limitation, measured: the
combination of per-frame repositioning, fresh terrain generation, and synchronous
front-buffer readback collapses the client to under 1 fps on GL-over-Metal, yielding
3-frame series (~95% deltas - fully changed scenes) that cannot support temporal claims.
Making this a usable gate needs asynchronous PBO readback and a pre-generated flight
corridor in the fixture world. Until then, in-motion verification remains a manual item.
