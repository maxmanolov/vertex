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

## Why this is plausible rather than surprising

Apple's GL implementation sits behind a translation layer that already coalesces
redundant state changes before anything reaches the GPU. A redundant `glEnable` costs a
function call and a compare on the driver side; the work Vertex hoped to avoid was
already not being done. The measurement says the deduplication is happening somewhere
below us either way.

## Consequences

- The skipping logic was **removed**, not merely disabled. Dead caching around GL state
  is a correctness liability (every path that changes state behind the tracker must
  remember to invalidate) with, on this platform, no upside to justify it.
- The **counters stayed**. They cost a increment per call, they are what priced this
  question, and they are what will price it on another platform without rebuilding the
  tracker.
- Visual gating was not the deciding factor. The capture comparison for this change was
  inconclusive against the fixture's noise floor (see #176), so the decision rests on the
  frame-time axis alone - where the answer is unambiguous.

## If someone revisits this on Windows or Linux

Do not restore the skipping first. Run the counting build, read `glCalls` and
`glRedundant` off the diagnostics line, and multiply the redundant count by a measured
per-call cost on that driver. Only if that product is a meaningful fraction of frame
time is the skip worth reintroducing - and then it needs the tracker's invalidation
paths re-audited, because they are the part that can produce wrong pixels rather than
merely slow ones.
