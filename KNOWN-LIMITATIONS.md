# Known limitations - Vertex 0.3.0-rc2

- **Multi-core chunk building ships default-OFF** (`-Dvertex.multicore=true` to enable).
  Its geometry is now structurally verified: the per-section build audit matches the
  vanilla path exactly (identical zero-sets, no divergent sections) and captures show
  correct terrain - see docs/benchmarks/multicore-status.md for the full defect history,
  which included claims retracted mid-release-cycle when the first output-level evidence
  contradicted every healthy activity counter. Real-build stress: 5,000-17,700
  rebuilds/min, clean cycles and shutdowns. Remaining before default-on: the human
  fly-through (TESTING.md).
- **Intermittent world-exit crash under stress (1 in 5 runs)**: "Already tesselating!"
  at rejoin, a known vanilla 1.7.10 teardown fragility signature; attribution unresolved
  (three targeted repro attempts passed, on and off). Tracked with full detail in the
  issue tracker; the stress suite is the reproduction harness.
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
