# Vertex benchmark report

To run a local cross-client comparison, use [bench/README.md](bench/README.md). This
harness measures each client through the same external frame collector. Do not use the
Vertex-only diagnostics line as the primary result for a cross-client comparison.

Sections are stamped with the release they were measured at; the environment is the
same throughout.

All numbers measured on the official Mojang 1.7.10 client, Apple M3 (GL 2.1 on Metal),
Zulu JDK 8, via the built-in diagnostics (`diagnostics=true`) and autonomous harness.
Reproduction flags for every scenario are in docs/ARCHITECTURE.md. Pacing, not average
FPS, is the optimization target; percentiles come from the allocation-free frame
histogram (0.5ms buckets).

## Renderer backends (measured at 0.4.0; arena is the new-profile default)

Steady state at spawn in the fixture world, uncapped, multicore on. `displaylist`
(stage 1) is measured frame-time-identical to legacy by design and omitted below. Full
protocol, parity evidence and per-stage detail: docs/benchmarks/renderer-backends.md.

| steady state | legacy (vanilla path) | vbo | arena (default) |
|---|---|---|---|
| RD8 fps / submit per frame | ~248 / 2.72 ms | ~730 / 0.42 ms | ~1,080 / 0.07 ms |
| RD16 fps / submit per frame | ~92 / 8.26 ms | ~306 / 1.98 ms | ~562 / 0.30 ms |
| RD16 draw commands per frame | ~1,700 (one per section) | ~1,700 (one per section) | ~51 (region batches) |

The default applies when `renderer` is absent or blank. Existing configurations retain
their explicit value, including the `renderer=legacy` line generated before promotion.

Arena frame pacing at RD16: ftP50 1.7 ms / ftP99 3.2 ms. Buffer memory is explicit
under these backends (RD16: ~239 MB vbo; arena 241 MB live against a 336 MB block
peak) where display-list memory was driver-hidden.

## Frame pacing (multicore program, 0.3.0-0.3.2)

| Scenario | ftP50 | ftP99 | ftMax | frames/min | notes |
|---|---|---|---|---|---|
| Steady state, churn load, multicore off | 16.7ms | 23.2ms | 51.9ms | 3,689 | vsync-locked baseline |
| Under full stress cycle, multicore ON | 13.2-15.2ms | 19.7-20.2ms | see note | ~3,900 | teleports, RD flips, mass updates |

Note: multi-second ftMax spikes during stress windows are vanilla's blocking world
save/load on exit/rejoin transitions, annotated in KNOWN-LIMITATIONS.md; steady-state
windows show the pacing above.

## Chunk-build throughput (multicore program, 0.3.0-0.3.2)

| Path | world-load flood | steady state |
|---|---|---|
| Vanilla budgeted path (multicore off) | budget-limited trickle | ~460 rebuilds/min under churn |
| Worker pool (multicore on, 6 workers, REAL builds) | **5,000-17,700 rebuilds/min** across stress windows (~300 sections/s during teleport storms) | churn parity, queue drained |

The earlier ~10,400/min figure was retracted (it measured empty builds silenced by a flag
race; docs/benchmarks/multicore-status.md). The numbers above are from the post-fix
stress suite with structurally verified geometry: measured across a full 6-minute cycle
run (26 teleports, 6 render-distance flips, 3 resource reloads, 3 world exit/rejoins,
clean shutdown, pacing p50 16.2ms / p99 18.7ms at steady state, heap 343-454MB with no
growth trend), with repeat runs passing under identical load. One intermittent teardown
crash in five stress runs is tracked openly (see KNOWN-LIMITATIONS.md).

## Memory and allocation

- Steady state: 124MB used heap, 1 minor GC (17ms) per minute
- 8-minute stress with 4 world exit/rejoin cycles: heap oscillated 175-314MB with no
  growth trend; GC contained within transition windows

## Feature-cost measurements backing exclusions

- GL state redundancy (Fast Render): 45-49% of ~2M calls/min redundant when keyed
  correctly per (unit, target) - but only ~263 calls/frame, ~26 microseconds against a
  16ms frame (docs/benchmarks/fastrender.md). Excluded.
- Fast Math: vanilla's 64k trig table ~1ns/op; the 4k variant slower and 16x less
  accurate on this hardware (docs/benchmarks/fastmath.md). Excluded.
- CTM: measured dispatch cost side (142k-164k icon resolutions/min steady, 2.4M during
  load), zero performance benefit mechanism (docs/benchmarks/ctm-determination.md). Excluded.

## Active-feature overhead (measured at the counters)

- Icon dispatch (better grass / natural textures): one interface call on 142k-164k
  resolutions/min; inert branches are a boolean check
- Brightness hook (dynamic lights): one volatile read per lookup with no sources
- Render-pass skips: one static call per pass per frame; config polls at most 1/s
- Diagnostics off: one nanoTime + histogram increment per frame, counters reset per interval
