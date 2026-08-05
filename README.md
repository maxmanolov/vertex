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
within a second — no restart. Current keys: `sky`, `clouds`, `weather`, `voidParticles`,
`textureAnimations`, `interactiveRenderPriority`.

### Render-pass controls (active)

Config-gated head-skips of whole vanilla render passes: the sky pass (sky color, stars,
sun and moon), clouds, rain/snow rendering plus rain splash particles, void fog particles,
and per-frame animated-texture uploads. Each costs a single static call per frame when
enabled and removes the pass entirely when disabled.

### Interactive render priority (ported, active)

Vanilla marks a changed block's 3x3x3 chunk-section neighborhood dirty and leaves the rebuild to a distance-sorted, per-frame-budgeted queue. When that queue is busy (chunk loading while moving), the section containing a block you just broke or placed can wait many frames, so the world visibly lags your click.

Vertex promotes the section containing an *interactive* change - one within 8 blocks of the view entity - to an immediate rebuild that runs ahead of the vanilla budget, capped at 4 sections per frame. Blocks on a section boundary also promote the face-adjacent section (never diagonals), so no stale face or hole lingers at the seam. Server-driven changes (pistons, fluids, redstone, other players) stay on the vanilla throttled path and cannot bypass the budget.

### Multi-core chunk building (planned)

Porting the worker-pool chunk tessellation (CPU workers build geometry, the client thread only compiles display lists) requires redirecting every read of the global `Tessellator.instance` to a per-thread instance, which touches most rendering classes at load time. The transformer pipeline is built to support it; it lands in a later release.

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

JDK 8+ and the Gradle wrapper:

```
./gradlew build
```

Output: `build/libs/vertex-<version>.jar` - both the mod library and the installer executable.

## License

MIT. Vertex contains only original code (tweaker, transformers, hooks, installer); it ships no Minecraft code and modifies the client only in memory at class-load time.
