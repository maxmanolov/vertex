# Known limitations - Vertex 0.3.0-rc1

- **Multi-core chunk building ships default-OFF** (`-Dvertex.multicore=true` to enable).
  It has passed every mechanical gate: ~10k worker-built sections/min replayed cleanly,
  8-minute stress cycles with world reloads and clean shutdown, zero self-disables, no
  heap growth. What remains is human visual sign-off (see TESTING.md); the automated
  framebuffer gate is experimental (docs/benchmarks/framebuffer-comparison.md).
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
