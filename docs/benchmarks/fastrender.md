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
