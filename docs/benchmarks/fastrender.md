# Fast Render investigation (decision: excluded)

**Question:** OptiFine's Fast Render batches GL state changes to eliminate redundant
transitions. Is that worth reimplementing?

**Method:** every game call to glEnable/glDisable/glBindTexture rerouted through counting
wrappers (GLCallCountPatch + VertexGLStats) that classify a call as redundant when it sets
state to its current value - the exact set state batching would eliminate. Measured live
in-world (autonomous soak, churn load, Apple M3, GL-on-Metal).

**Results (per-minute windows):**

| window | state calls | redundant | ratio |
|---|---|---|---|
| 1 | 941,011 | 364,056 | 38% |
| 2 | 1,224,475 | 507,311 | 41% |

**Decision:** excluded. The redundancy *ratio* is high, but the absolute cost is not:
~8,500 redundant calls/second is ~140 per frame, and a redundant enable/bind on a modern
driver costs on the order of 100ns - microseconds per 16ms frame, well under the 5%
frame-time threshold committed in ROADMAP #5. State batching would add real regression
risk to the fixed-function pipeline for a sub-percent win. The counters stay in the
diagnostics line so this can be re-checked on other hardware.

## Corrected measurement (kyrofx #38)

The original counter judged bind redundancy by texture id alone, ignoring that OpenGL
keeps one binding per (texture unit, target) and one GL_TEXTURE_2D enable per unit. The
counter now tracks active-unit changes (GL13 and ARB multitexture paths) and keys state
per (unit, target).

Re-measured under the same soak conditions: 45-49% redundancy at ~2M state calls/minute -
within the range the flawed counter reported, so cross-unit/cross-target false positives
were not a dominant term in this workload. Absolute cost with corrected numbers: ~950k
redundant calls/minute is ~263 per frame at 60 fps, on the order of 26 microseconds of
driver time per 16 ms frame (~0.16%), still far below the 5% exclusion threshold.

**The exclusion stands under corrected measurement.**
