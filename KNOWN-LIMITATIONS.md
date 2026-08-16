# Known limitations - Vertex 0.5.x

- **Multi-core chunk building is now DEFAULT-ON** (`multicore=false` in
  vertex.properties opts out; restart to apply). The promotion gate closed 2026-08-06
  after the full chain: structural verification, the 0.3.1 lifecycle hardening
  (#73-#76), world-change tessellation recovery (#91), and - decisively - a human
  fly-through that surfaced the last in-motion race (client translucent resort vs
  worker vertexState write, #92), fixed at root and A/B-verified before a clean
  fly-through sign-off on the fixed pipeline.
- **The arena renderer is now the declared default for new profiles and configurations
  with no `renderer` value** (`vbo`, `displaylist` and `legacy` remain selectable,
  restart to apply). Existing configurations retain explicit values, so profiles whose
  generated file already says `renderer=legacy` remain on legacy until that line changes.
  Promotion mirrored the multicore gate:
  structural parity plus zero-disable soak/churn/stress gauntlets at 0.4.0
  (docs/benchmarks/renderer-backends.md), then real-session miles and maintainer
  sign-off (2026-08-08). Any runtime failure self-disables down the ladder to the
  vanilla path. GPU buffer memory is explicit and reported in diagnostics under the
  buffer backends (~239MB at RD16 under `vbo`; arena blocks peaked at 336MB with 241MB
  live in the RD16 soak) - the legacy display-list equivalent existed but was
  driver-hidden.
- **Workers still execute against the live WorldRenderer**: reposition races are
  caught by build stamps, the resort race by the in-flight guard, and transient
  `skipRenderPass` writes remain observable mid-build (worst case a one-frame flicker
  of a section mid-rebuild, never a crash). The immutable-snapshot design that removes
  the whole class remains open (docs/ROADMAP.md #1).
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
- **Connected textures ship rule-driven** (`connectedTextures`, default on; the original
  exclusion determination in docs/benchmarks/ctm-determination.md predates the
  block-indexed rule engine that lifted it). Tiles stitch even when the key is off so
  the menu toggle works live in both directions; the cost is a few atlas cells for
  packs that ship CTM rules.
- **A handful of video-menu slots are deliberately inert**: Redstone Animated, Vignette,
  Translucent Blocks, Load Far and Preloaded Chunks have no 1.7.10 behavior behind them;
  Water/Grass fancy, Clear Water, Custom Fonts, Antialiasing, Better Snow, Smooth
  Biomes, Fullscreen Mode and the integrated-server pacing pair (Smooth World, Lazy
  Chunk Loading) are real OptiFine features tracked as enhancement issues. Every inert
  slot renders disabled and is annotated with its reason in the layout source.
- **Vanilla-profile only**: under Forge or deobfuscated environments Vertex deliberately
  no-ops (names don't match); this is by design, not a defect.
