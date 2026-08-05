# Vertex feature matrix

Living comparison against OptiFine 1.7.10 (HD Ultra line), maintained as features land.
Status values: **missing** / **in progress** / **matched** / **surpassed** / **excluded** (with justification).
All Vertex implementations are clean-room: designed from vanilla sources and mappings, public
format documentation, and observable behavior only (see docs/ARCHITECTURE.md for the policy).

## Rendering and chunk pipeline

| Feature | Purpose | Vertex status | Notes |
|---|---|---|---|
| Interactive render priority | Rebuild the chunk section you just edited immediately instead of waiting on the throttled queue | **surpassed** | Not an OptiFine feature at all. Reach-gated, boundary-aware, capped 4/frame. Active since 0.1.0. |
| Multi-core chunk building | Tessellate chunk geometry on worker threads | **in progress** | Phase 1 landed (0.2.0): the transformer-wide `Tessellator.instance` redirect is live and verified against the official client (78 call sites, identity semantics, class-init side effect preserved). Phase 2 adds the worker pool proven out-of-tree - no shared GL context, unlike OptiFine's single Pbuffer thread. |
| Smooth (time-sliced) chunk loading | Spread chunk rebuild cost across frames to reduce stutter | missing | Planned after multi-core; the per-frame budget machinery is shared. |
| Lazy chunk loading | Defer client chunk attach work | missing | Low priority; measure first - vanilla 1.7.10's cost profile here is modest. |
| Fast Render (GL state batching) | Reduce redundant GL state changes per frame | missing | Medium impact, high regression risk on the fixed-function pipeline; needs its own profiling pass. |
| Render distance extension (>16) | Far render distance | missing | Client-side value is easy; useful only in singleplayer (servers cap view distance). Low. |
| Sky / stars / sun & moon toggles | Skip sky-pass draws for fps | **matched** (0.2.0) | `sky=false` in vertex.properties skips the whole sky pass. Per-layer split planned. |
| Cloud rendering control | Skip or simplify clouds | **matched** (0.2.0) | `clouds=false` skips the cloud pass entirely (vanilla only offers fast/fancy via graphics). |
| Weather rendering control | Skip rain/snow rendering + splash particles | **matched** (0.2.0) | `weather=false` skips renderRainSnow and rain particle spawning. Server weather state is untouched. |
| Void fog / depth particles control | Remove void fog particle churn | **matched** (0.2.0) | `voidParticles=false` skips doVoidFogParticles. |
| Texture animation control | Stop per-frame texture re-uploads (water/lava/fire/portal) | **matched** (0.2.0) | `textureAnimations=false` skips TextureMap.updateAnimations - one switch today; per-animation granularity planned. |
| Fog control (off/fast/fancy) | Reduce fog fill cost / visibility preference | **matched** (0.2.0) | `fog=false` disables distance fog via a GL-mode-aware tail hook on setupFog; lava/water/blindness density fog is preserved. Fast/fancy hint control judged not worth it on modern GPUs. |
| Particles fine control | Finer than vanilla's all/decreased/minimal | missing | Low; vanilla covers the bulk. |
| Fast Math | Cheaper trig via smaller lookup table | **excluded** | Benchmarked (docs/benchmarks/fastmath.md): vanilla's 64k table costs ~1 ns/op on modern hardware; the 4k variant measured slower and 16x less accurate. Nothing to gain. |
| Chunk updates per frame | Configurable rebuild budget | missing | Lands with the multi-core port. |
| Antialiasing (FSAA) | Smoother edges | missing | Requires pixel-format selection at display creation; restart semantics. Low demand. |
| Anisotropic filtering | Sharper angled textures | **matched by vanilla** | Vanilla 1.7.10 already ships AF in video settings; nothing to add unless bugs surface. |
| Mipmaps | Reduce distant texture shimmer | **matched by vanilla** | Vanilla 1.7.10 already ships mipmap levels; OptiFine-era fixes largely merged upstream. |

## Resource-pack visual features

These parse community-documented resource-pack formats (MCPatcher/OptiFine-convention `.properties`
files). File-format compatibility is an interoperability surface; implementations are original.

| Feature | Purpose | Vertex status | Notes |
|---|---|---|---|
| Connected textures (CTM) | Seamless glass/bookshelves etc. | missing | Largest resource-pack feature; format is publicly documented by the pack community. High demand, large effort. |
| Custom sky | Pack-defined sky boxes | missing | Medium effort, format documented. |
| Custom colors | Pack-defined colormaps | missing | Medium. |
| Emissive textures | Glow overlays | missing | Medium-small. |
| Random entities | Per-mob texture variants | missing | Medium. |
| Natural textures | Rotate/flip tiling variants | missing | Small. |
| Better grass / better snow | Side-grass and snow-under-fence rendering | missing | Small-medium, block-renderer surgery. |
| HD fonts / HD textures | Pre-1.6 McPatcher features | **excluded** | Vanilla's 1.6+ resource pack system already supports arbitrary resolutions and fonts; nothing left to implement for 1.7.10. |

## Platform and infrastructure

| Feature | Purpose | Vertex status | Notes |
|---|---|---|---|
| Standalone tweaker install (no Forge) | Single-mod loader via LaunchWrapper | **matched** | `--tweakClass vertex.VertexTweaker` + profile installer since 0.1.0. |
| Configuration | Persistent user settings | **matched** (0.2.0) | `vertex.properties` in the game dir, hot-reloaded (~1s); no GUI yet (see excluded). |
| In-game options GUI | Video-settings screens | **excluded (for now)** | GUI class injection into obfuscated screens is high-maintenance for zero fps value; config file + hot reload covers function. Revisit if demand appears. |
| Shaders support | Programmable-pipeline visual overhaul | **excluded (for now)** | Requires replacing the fixed-function display-list renderer with a deferred, shader-driven pipeline - effectively a new renderer, not a patch. Out of scope until the multi-core/VBO groundwork exists. Documented as the boundary of the project's ambition for 1.7.10. |
| Show FPS / lagometer / debug diagnostics | Performance visibility | **in progress** (0.2.0) | Periodic activity counters land with `diagnostics=true` (promotions, rebuilds, active skips, once/minute). HUD and frame-time metrics later. |
| Crash-resilient hooks | A broken patch must never take the game down | **surpassed** | Every transformer falls back to vanilla bytes; every hook self-disables on first failure and logs once. OptiFine historically hard-crashes on patch mismatch. |
| Forge compatibility | Coexist with Forge | **excluded** | Vertex targets the obfuscated vanilla client by design; under Forge, names differ and Vertex deliberately no-ops. Forge users have the mod ecosystem for these features. |
| Cape support | Cosmetics | **excluded** | Not a performance feature; out of scope. |

## Priority queue (by impact / effort)

1. Multi-core chunk building (flagship; design already proven)
2. Fog control
3. Diagnostics counters (cheap, guides everything else)
4. Connected textures (biggest visual-feature demand)
5. Better grass/snow, natural textures (small block-render features)
6. Custom colors / custom sky / emissive / random entities
7. Fast Render investigation (profile first)
8. Fast Math benchmark (adopt only if measurable)
