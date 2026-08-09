# Vertex

Standalone performance optimization mod for Minecraft **1.7.10**. No Forge required: Vertex is a [LaunchWrapper](https://github.com/Mojang/LegacyLauncher) tweak (`--tweakClass vertex.VertexTweaker`) whose `IClassTransformer` patches the vanilla client's rendering classes in memory with [ObjectWeb ASM](https://asm.ow2.io/) as they load. Nothing on disk is modified except the launcher profile the installer creates.

## Install

1. Run vanilla **1.7.10** once from the official launcher (so its jar and json exist).
2. Build or download `vertex-<version>.jar`, then:

   ```
   java -jar vertex-<version>.jar install
   ```

   (Use `--mcdir /path/to/.minecraft` for a non-standard location.)
3. Pick the **1.7.10-Vertex** profile in the launcher.

The installer clones the vanilla version json into `versions/1.7.10-Vertex/`, retargets it at `net.minecraft.launchwrapper.Launch`, appends the tweak class argument, and registers this jar plus LaunchWrapper as libraries. Uninstalling is deleting that profile directory.

## Optimizations

See [FEATURES.md](FEATURES.md) for the living comparison matrix against OptiFine 1.7.10 —
every feature is tracked as missing, in progress, matched, surpassed, or excluded with
justification. Architecture and the clean-room policy live in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Configuration (active)

`vertex.properties` is created in the game directory on first run. Every key defaults to
vanilla behavior; set a key to `false` to skip that work for performance. Edits hot-reload
within a second — no restart, except the two pipeline keys (`multicore`, `renderer`),
which apply at the next launch. The generated file documents every key with a comment; the
authoritative list lives there and in [FEATURES.md](FEATURES.md) (20 keys as of 0.4.0,
covering render passes, pack visual features, dynamic lights, the chunk-build and
renderer pipelines, fullbright, HUD backgrounds and diagnostics).

### Render-pass controls (active)

Config-gated head-skips of whole vanilla render passes: the sky pass (sky color, stars,
sun and moon), clouds, rain/snow rendering plus rain splash particles, void fog particles,
and per-frame animated-texture uploads. Each costs a single static call per frame when
enabled and removes the pass entirely when disabled.

### Interactive render priority (ported, active)

Vanilla marks a changed block's 3x3x3 chunk-section neighborhood dirty and leaves the rebuild to a distance-sorted, per-frame-budgeted queue. When that queue is busy (chunk loading while moving), the section containing a block you just broke or placed can wait many frames, so the world visibly lags your click.

Vertex promotes the section containing an *interactive* change - one within 8 blocks of the view entity - to an immediate rebuild that runs ahead of the vanilla budget, capped at 4 sections per frame. Blocks on a section boundary also promote the face-adjacent section (never diagonals), so no stale face or hole lingers at the seam. Server-driven changes (pistons, fluids, redstone, other players) stay on the vanilla throttled path and cannot bypass the budget.

### Fullbright and HUD backgrounds (active)

`fullbright` (default off, with a button in Video Settings) renders the world at maximum
brightness and skips light-triggered chunk rebuilds while active. `chatBackground` and
`scoreboardBackground` remove the translucent backdrops behind chat lines and the
scoreboard sidebar, with ON/OFF buttons in the vanilla Chat Settings screen. All three
persist to vertex.properties like every other key.

### Multi-core chunk building (default-on)

A pool of CPU workers tessellates chunk geometry into per-build Tessellator instances while the client thread compiles the results into display lists. The global `Tessellator.instance` read is redirected to a per-thread instance across all rendering classes at load time. Worker output is structurally verified against the vanilla path (identical per-section build audits, matching frame captures), the lifecycle is hardened end to end, and the promotion gate closed with a human fly-through on the fixed pipeline — see docs/benchmarks/multicore-status.md for the full verification history.

On by default since 0.3.2; set `multicore=false` in vertex.properties to opt out (restart to apply). Self-disables cleanly on any failure without costing the session.

### Renderer backends (arena default-on)

Phase profiling showed the stock renderer is submission-bound: at render distance 16,
three quarters of frame time goes into executing one display list per visible section
(docs/benchmarks/renderer-baseline.md). Vertex ships a staged replacement, and the
shared-arena backend is now the **default**: sections live as ranges inside per-region
vertex buffers with their transforms baked in at build time, so a pass submits a
handful of glMultiDrawArrays batches instead of one draw per section — measured at
render distance 16: 8.26 ms → 0.30 ms submission per frame, ~51 draw commands instead
of ~1,700, ~92 → ~562 fps. The `renderer` key still selects the other rungs: `vbo`
(per-section vertex buffers), `displaylist` (the managed section-mesh pipeline at
vanilla visuals and performance), or `legacy` (the untouched vanilla path, which weaves
nothing). Restart to apply. Promotion followed the multicore playbook: structural-parity
audits and zero-disable soak/churn/stress gauntlets at 0.4.0, then real-session miles
and maintainer sign-off. The design, stage ladder, and lifecycle rules live in
[docs/RENDERER.md](docs/RENDERER.md); measured results per stage in
docs/benchmarks/renderer-backends.md. Any runtime failure self-disables down the ladder
to the vanilla path without costing the session.

## How it works

| Piece | Role |
|---|---|
| `vertex.VertexTweaker` | ITweaker: registers the transformer, relaunches the vanilla main |
| `vertex.transform.VertexTransformer` | Dispatches per-class patches by obfuscated (notch) name |
| `vertex.transform.WorldRendererPatch` | Adds the immediate flag + `ImmediateMarker` interface bridges to `blo` |
| `vertex.transform.RenderGlobalPatch` | Hooks `markBlockForUpdate` and `updateRenderers` in `bma` |
| `vertex.hooks.VertexHooks` | The actual logic: reach gate, boundary promotion, capped consumption |
| `vertex.Mappings` | 1.7.10 notch names (from MCP srg + stable-12 CSVs), documented per member |
| `vertex.installer.VertexInstaller` | Writes the launcher profile (`Main-Class` of the jar) |

Vertex targets the obfuscated vanilla 1.7.10 client only. In any other environment (deobfuscated dev workspace, other versions, Forge) the class names don't match and every class passes through untouched; if a hook fails at runtime it logs once and disables itself rather than crashing the game.

## Build

Use JDK 17 and the Gradle wrapper. The wrapper supports JDK 8 through JDK 23. The
output runs on JDK 8.

```
./gradlew build
```

Output: `build/libs/vertex-<version>.jar` - both the mod library and the installer executable.

## Compare clients locally

Use the [local client benchmark](bench/README.md) to compare vanilla 1.7.10, Vertex,
OptiFine, Lunar Client, or another profile. The standalone harness uses the same external
frame collector for each client. It keeps raw CSV data and writes JSON, CSV, and Markdown
reports.

## License

MIT. Vertex contains only original code (tweaker, transformers, hooks, installer); it ships no Minecraft code and modifies the client only in memory at class-load time.
