# Manual test checklist - Vertex 0.3.0

Automated coverage (unit tests, autonomous soaks, stress cycles) runs via the harness
flags documented in docs/ARCHITECTURE.md. The items below are the ones that need eyes.

## Multi-core visual sign-off (promotion gate for default-on)

Multicore ships as an experimental opt-in; this check promotes it to default in a
future release. It does not block using 0.3.0's default configuration.

1. Launch the 1.7.10-Vertex profile with `-Dvertex.multicore=true`.
2. Join a world; fly fast in one direction for ~60s, then turn 180 and return.
3. Look for: missing/stale sections, holes at section boundaries, flicker during
   rebuilds, lighting seams. Break and place blocks near chunk borders.
4. Exit to title, rejoin, repeat briefly. If clean, multicore's default flips on.

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
- HUD backgrounds: on a server (or via /scoreboard in a world with cheats) display a
  sidebar objective, toggle Scoreboard BG in Chat Settings; the sidebar's translucent
  backdrop disappears while its text and score numbers stay. Chat Background is covered
  by the automated capture diff; a quick eyeball that chat text stays readable is enough.
- Installer: `java -jar vertex-<v>.jar install` into a fresh .minecraft with vanilla
  1.7.10 present; profile appears and launches.
