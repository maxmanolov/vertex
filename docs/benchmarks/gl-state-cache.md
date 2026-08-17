# GL state-call skipping: a negative result on macOS

Status: **closed as not-worth-doing on Apple GL.** Recorded so nobody re-derives it,
and so the same question can be priced cheaply on Windows/Linux later.

## Question

Vanilla 1.7.10 sets GL state redundantly: the same `glEnable`, `glBindTexture`,
`glColor4f` and matrix calls are re-issued thousands of times per frame with values the
driver already has. Client mods commonly cache that state and skip the redundant calls.
Does skipping them reduce frame time on this client?

## What was built

`GLStateTracker` shadows the state Vertex touches: per-unit capability flags (including
the texgen quartet `GL_TEXTURE_GEN_S..Q`, which the end-portal effect drives), per-(unit,
target) texture bindings, and sampler state, with `invalidateAll()` / `forgetTexture()`
escape hatches for the paths that mutate GL behind the tracker's back. `VertexGLStats`
counts every call and every call that *would have been* skipped.

An earlier revision actually performed the skipping. That code was removed after the
measurement below; only the counting survives.

## Result

RD16 arena soak, macOS on Apple Silicon, redundant-call skipping on versus off:

| | calls skipped/min | ftP50 | ftP99 |
|---|---:|---:|---:|
| skipping off | 0 | 0.7 ms | 1.7 ms |
| skipping on | ~4,500,000 | 0.7 ms | 1.7 ms |

**Four and a half million skipped calls per minute moved the frame-time distribution by
nothing measurable.** Not "a small win inside noise" - the percentiles were identical.

## What the result establishes

The A/B result does not identify where the redundant calls become cheap; it only shows
that removing them did not change the measured frame-time distribution on this client
and driver. Driver-side coalescing is one possible explanation, but the experiment did
not instrument the driver and therefore cannot establish that mechanism.

## Consequences

- The skipping logic was **removed**, not merely disabled. Dead caching around GL state
  is a correctness liability (every path that changes state behind the tracker must
  remember to invalidate) with, on this platform, no upside to justify it.
- The **counters stayed**. They cost an increment per call, they are what quantified this
  question, and they are what will price it on another platform without rebuilding the
  tracker.
- Visual gating was not the deciding factor. The capture comparison for this change was
  inconclusive against the fixture's noise floor (see #176), so the decision rests on the
  frame-time axis alone - where the answer is unambiguous.

## If someone revisits this on Windows or Linux

Do not infer the result from call counts or an isolated per-call microbenchmark. Repeat
the same controlled frame-time A/B on the target driver, alternating pre-built jars and
holding the scene and machine load fixed. The counters establish that skipping engaged;
the end-to-end frame-time distribution decides whether it helped. If it does, re-audit
the tracker's invalidation paths before restoring the optimization, because those paths
can produce wrong pixels rather than merely slow ones.
