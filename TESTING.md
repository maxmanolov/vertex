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

## Renderer backend spot check (arena default for new profiles)

The arena backend is the declared default for new or missing-key configurations;
existing files retain an explicit value, including the `renderer=legacy` line generated
before promotion. `renderer=vbo`, `displaylist` or `legacy` in vertex.properties select
the other rungs (restart to apply). Run the same four-step
fly-through above on any rung - the checklist is identical because the backends must be
visually indistinguishable from legacy. With `diagnostics=true` the once-per-minute
summary reports backend health (uploads, draw batches, arena occupancy); any failure
self-disables down the ladder to the vanilla path and logs once.

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
- Freelook: hold Left Alt while walking forward - the camera detaches into a third-person
  orbit while the character keeps walking in the original direction; spin a full circle
  (yaw must not stop at 360), pitch stops at straight up/down; release - view snaps back
  to first person and the original heading instantly, and the crosshair still points
  where it did before the hold. Rebind the "Freelook" entry in Controls (Miscellaneous),
  restart, confirm the new key persisted. Held while a chest/chat is open: nothing
  happens; releasing mid-GUI still snaps back on close.
- HUD backgrounds: on a server (or via /scoreboard in a world with cheats) display a
  sidebar objective, toggle Scoreboard BG in Chat Settings; the sidebar's translucent
  backdrop disappears while its text and score numbers stay. Chat Background is covered
  by the automated capture diff; a quick eyeball that chat text stays readable is enough.
- Toggle sprint: set `toggleSprint=true` (hot-reloads); walking forward sprints with no
  key held, and sprint resumes by itself after stopping, jumping, and bumping into a
  block. Tap Left Ctrl once - sprint pauses (walk speed); tap again - resumes. Open chat
  and type a Ctrl shortcut: the latch must not flip. Hunger below 3 shanks must still
  stop the sprint (vanilla gate stays in charge).
- Video Settings menu: open Options > Video Settings; six pages (Video, Details,
  Animations, Quality, Performance, Other) in the OptiFine layout. Toggle a vanilla
  option, a Vertex option (Fullbright, Dynamic Lights), drag a slider, navigate into a
  sub-page and back via Done and via Esc, change GUI Scale on the main page (screen
  re-centers), and confirm values survive a restart. Greyed rows are reference slots
  with no honest 1.7.10 backing and must do nothing. Automated placement evidence:
  `-Dvertex.test.guiProbe=videoPages` screenshots all six pages.
- Detail settings: toggle Sun & Moon off at day and Stars off at night (each element
  vanishes alone, the rest of the sky stays); cycle Cloud Height and watch the cloud
  plane lift; cycle Fog Start with fog visible; Depth Fog off brightens the fog color
  at bedrock depths. Toggling Connected Textures, Better Grass, Custom Colors, Natural
  Textures or Swamp Colors rebuilds the world within seconds (the settings re-mark).
- Animations page: freeze water/lava/fire/portal individually (only that family stops);
  Items Animated off freezes the compass/clock; smoke/flame/portal/potion particle
  toggles stop their families while crits and block-break particles always spawn. All
  OFF then All ON round-trips every wired key.
- Grass quality: with Graphics Fast, set Grass to Fancy - hillside grass sides gain
  the biome-tinted overlay within a rebuild (log: "Grass override active"); Fast under
  fancy Graphics removes it. Antialiasing: set 4x, restart, and confirm the log line
  "Antialiasing active ... GL_SAMPLES=4"; block edges at a distance smooth visibly.
  Smooth Biomes: stand on a biome border - OFF snaps the grass/foliage/water color at
  the border line, ON restores the blended band; the world re-marks on each flip.
- Better snow: in a snowy biome set `betterSnow=true`, place a fence and a torch on
  ground beside snow layers; a thin snow layer renders beneath both, and breaking the
  neighboring snow clears it after the rebuild. Off restores bare ground.
- Chunk-save paths (#195): run with `-Dvertex.test.blockChurn=20 -Dvertex.test.saveEvery=30`
  and confirm `[Vertex] Test save #N complete` lines plus a fresh mtime on
  `saves/<world>/region/*.mca`. The fixture's SIGTERM shutdown cannot save (log4j closes
  its appenders first, so the vanilla stop path throws before `saveAllWorlds`), so this
  is the only way to exercise chunk serialization, the scheduled-tick index and
  `getPendingBlockUpdates` in-game. NOTE: `blockChurn` writes real blocks (a toggling
  8x8 column at y=200) - it mutates the world, so never combine it with capture gates;
  use a scratch copy of the fixture world.
- Clear Water: stand over deep water and flip Clear Water ON - the bottom becomes
  visible within a tick (log: "Clear water armed (2 sprites)"); OFF restores vanilla
  opacity bit-exact. Flip once with Water Animated OFF to confirm the immediate
  re-upload covers frozen animations too.
- Fullscreen Mode: cycle to a smaller mode (e.g. 1280x720), press F11 - the log shows
  "Fullscreen mode 1280x720 applied" and the fullscreen resolution matches; Default
  restores the vanilla desktop-mode path (no log line). A value from a disconnected
  monitor falls back to desktop with a one-time log note.
- Custom Fonts: enable a resource pack shipping mcpatcher/font/ascii.png - all text
  switches to the pack font (log: "Custom font loaded from mcpatcher/font/ascii.png")
  with widths derived from the pack image; toggling Custom Fonts OFF restores the
  vanilla font immediately, ON brings the pack font back without a reload.
- Trees/Dropped Items: force Fast and Fancy against the opposite Graphics setting and
  confirm leaves/item entities follow the override, Default follows Graphics again.
- Smooth Lighting Level: cycle 100% -> OFF -> 50% in a dark interior; corner shading
  visibly weakens then partially returns; the world re-marks on each step.
- Other page: Show FPS draws the counter top-left (hides nothing else), Lagometer draws
  the frame-time graph under it, Time: Night darkens the sky at noon without touching
  daylight sensors or mob spawning (gameplay stays day), Autosave: 3min spaces the
  "Saving chunks" pauses out, Debug Profiler OFF makes F3+Shift show no pie chart.
- Performance page: Chunk Updates 1 vs 5 visibly changes world-load fill rate;
  Dynamic Updates on + standing still finishes loading faster; Fast Math and Fast
  Render read back correctly after the restart they announce.
- Installer: `java -jar vertex-<v>.jar install` into a fresh .minecraft with vanilla
  1.7.10 present; profile appears and launches.
