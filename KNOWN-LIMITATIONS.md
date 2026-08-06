# Known limitations - Vertex 0.3.0

- **Multi-core chunk building is an EXPERIMENTAL opt-in** (`-Dvertex.multicore=true`).
  Everything in the default configuration is fully verified; the experimental flag is
  labeled to exactly its evidence: geometry structurally identical to the vanilla path
  (per-section audit, identical zero-sets), correct terrain in still captures from
  multiple angles, 5,000-17,700 real rebuilds/min through stress cycles with clean
  shutdowns and flat heap. Its promotion to default-on in a future release is gated on
  in-motion verification - a five-minute fly-through (TESTING.md) or the async-readback
  motion gate (docs/benchmarks/framebuffer-comparison.md). Full defect and retraction
  history: docs/benchmarks/multicore-status.md.
- **Intermittent world-exit crash under the stress harness (1 of 7 runs, 5 consecutive
  clean since)**: "Already tesselating!" at rejoin, a known vanilla 1.7.10 teardown
  fragility signature; attribution unresolved, never observed outside synthetic
  churn+teleport+exit storms. Tracked as issue #69 with diagnostics ready for any
  recurrence.
- **World-transition frame stalls are vanilla behavior**: the multi-second ftMax spikes
  in stress telemetry are Minecraft's blocking world save/load, not Vertex overhead;
  steady-state pacing under stress measured ftP50 13-15ms / ftP99 ~20ms.
- **Custom sky's 3x2 face UV layout** is mechanically verified (draws, GL state restored,
  terrain occludes) but visually unconfirmed; on the manual checklist.
- **Dynamic lights** cover held items for all players; dropped items and burning entities
  are documented follow-up scope.
- **Custom colors** consume grass/foliage colormaps; other color.properties keys load but
  await per-key consumers (fog/sky/potions).
- **Connected textures excluded** for this release with cause (docs/benchmarks/ctm-determination.md).
- **Vanilla-profile only**: under Forge or deobfuscated environments Vertex deliberately
  no-ops (names don't match); this is by design, not a defect.
- **Better snow** not implemented (better grass ships, default off).
