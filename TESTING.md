# Manual test checklist - Vertex 0.4.0

Automated coverage (unit tests, autonomous soaks, stress cycles) runs via the harness
flags documented in docs/ARCHITECTURE.md. The items below are the ones that need eyes.

The standalone cross-client benchmark has a separate test task:

```text
./gradlew benchmarkTest
```

See `bench/README.md` for local profile comparisons.

## Multi-core visual sign-off - GATE CLOSED (2026-08-06)

Multicore is default-on since 0.3.2. The gate did its job on the first attempt: the
initial fly-through crashed in seconds on an in-motion race no automated soak had
surfaced (#92, client resort vs worker vertexState write), the race was fixed at root
and A/B-verified, and the follow-up fly-through on the fixed pipeline passed clean.
The checklist below is retained for regression sign-off on future renderer changes:

1. Launch the 1.7.10-Vertex profile (multicore is on by default).
2. Join a world; fly fast in one direction for ~60s - include water - then return.
3. Look for: missing/stale sections, holes at section boundaries, flicker during
   rebuilds, lighting seams. Break and place blocks near chunk borders.
4. Exit to title, rejoin, repeat briefly.

## Renderer backend spot check (opt-in, 0.4.0)

Set `renderer=displaylist`, `vbo`, or `arena` in vertex.properties (restart to apply)
and run the same four-step fly-through above - the checklist is identical because the
backends must be visually indistinguishable from legacy. With `diagnostics=true` the
once-per-minute summary reports backend health (uploads, draw batches, arena occupancy);
any failure self-disables back to the vanilla path and logs once.

## Feature spot checks (5 minutes total)

- `vertex.properties`: toggle sky/clouds/weather/fog false while in-world; each pass
  disappears within ~1s; re-enable and confirm restore.
- Dynamic lights: hold a torch at night; light follows you; drop to off via config and
  confirm the glow clears after the rebuild interval.
- With a pack: grass/foliage colormap tint applies; natural.properties mirrors show
  varied tiling; a mob with numbered texture variants shows stable per-mob skins;
  skyN.properties layers render and fade with the day clock (check the 3x2 UV layout
  looks correct - known-unconfirmed item).
- Better grass: enable, confirm hillside grass sides render as grass.
- Fullbright: toggle the Video Settings button (or `fullbright=true`); the world renders
  at max brightness within a second and dark areas are fully visible; toggle off and
  confirm normal lighting returns. The state must keep following the toggle even after
  a renderer-reload failure (reload loss is logged, brightness keeps working).
- HUD backgrounds: on a server (or via /scoreboard in a world with cheats) display a
  sidebar objective, toggle Scoreboard BG in Chat Settings; the sidebar's translucent
  backdrop disappears while its text and score numbers stay. Chat Background is covered
  by the automated capture diff; a quick eyeball that chat text stays readable is enough.
- Installer: `java -jar vertex-<v>.jar install` into a fresh .minecraft with vanilla
  1.7.10 present; profile appears and launches.
