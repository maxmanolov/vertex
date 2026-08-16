# Vertex architecture

## Pipeline

```
official launcher profile (1.7.10-Vertex)
  └─ net.minecraft.launchwrapper.Launch --tweakClass vertex.VertexTweaker
       └─ VertexTweaker registers vertex.transform.VertexTransformer
            └─ per-class ASM patches, dispatched by obfuscated (notch) name
                 ├─ WorldRendererPatch (blo): +vertex$immediate, +ImmediateMarker bridges
                 ├─ RenderGlobalPatch (bma): markBlockForUpdate / updateRenderers hooks
                 └─ SkipMethodPatch (bma/blt/bpz/bjf): config-gated head-skip of render passes
                      └─ runtime logic lives in vertex.hooks.* (plain Java, no GL)
```

Class version note: 1.7.10 classes are version 50, so writers use `COMPUTE_MAXS` only.
Requesting frame computation would resolve types through the wrong class loader.

## The class-loader split

`Launch` adds each tweaker's package to LaunchClassLoader's *class-loader* exclusions, so
everything under `vertex.` is defined by the app class loader while the game's classes live
in LaunchClassLoader. Two consequences, learned the hard way:

1. Hook code must never `Class.forName` an obfuscated name - it would load a second,
   untransformed copy. All reflective handles are resolved from live instances passed into
   the hooks (see `VertexHooks.ready`).
2. Injected interfaces (`vertex.api.*`) resolve to the app-loader copy from both sides,
   which is exactly what makes `instanceof ImmediateMarker` work across the split.

## Failure policy

A performance mod must never cost the user their game session:

- `VertexTransformer` catches per-class patch failures and returns vanilla bytes.
- Every runtime hook self-disables after its first failure and logs once.
- Config parsing falls back to defaults on any error.

## Configuration

`vertex.properties` in the game directory (created with defaults on first run). Values are
polled at most once per second by the hooks, so edits apply in-game within a second - no
GUI, no restart. Keys are documented in the generated file and in README.

## Clean-room policy

Vertex reimplements functionality popularized by OptiFine without access to its code:

**Allowed sources:** vanilla 1.7.10 bytecode and MCP mappings; Mojang's LaunchWrapper;
public community documentation of resource-pack formats and observable behavior; profiling
of the vanilla client; original design work.

**Forbidden sources:** OptiFine binaries' decompiled output, any code derived from them,
and any third-party source of OptiFine lineage. If a contributor has consulted such
material for a given feature area, someone else implements that area.

File-format compatibility (e.g. CTM `.properties` conventions) is treated as an
interoperability surface: the formats are reimplemented from public documentation and
sample packs, never from OptiFine's parser.

## Verifying a hook is actually reached

A neutrality soak proves a hook does no harm - it cannot prove the hook runs. An inert
hook and a correct hook produce identical soaks. Any dispatch point that features will be
built on therefore ships with a hit counter in the diagnostics line first (see
VertexIcons.hits/sideHits), and the counter is read in-world before anything is built on
top of it. This was learned the expensive way: better grass silently never armed on its
first soak, and only an instrumented run showed the icon dispatch was live all along.

## Testing and benchmarks

- `./gradlew test`: the transformer layer is unit-tested against synthetic classes built
  with ASM in the tests themselves - patched bytecode is loaded into an isolated class
  loader and executed, asserting hook delivery, body preservation, config gating, and the
  Tessellator redirect's class-initialization guarantee (a regression test for a real bug
  caught during boot verification).

- Transformers are exercised end-to-end against the real Mojang 1.7.10 jar. The built-in
  test harness makes this fully autonomous - no human in the loop:

      java -Dvertex.test.autoJoin=WORLDNAME -Dvertex.test.churn=8 ... (normal launch)

  autoJoin enters the named singleplayer world from the title screen; churn force-promotes
  ~N sections per second near the player, generating sustained rebuild load through the
  real promotion/consumption pipeline. A soak passes when the process outlives the window
  with zero hook self-disables and the per-minute diagnostics counters show balanced
  promotions and rebuilds. First recorded run: 3+ minutes, ~460 promotions and ~460
  rebuilds per minute, zero disables, against the official client.

  Companion flags: `-Dvertex.test.chatSpam=N` prints a fresh chat line every second
  in-world (keeps chat visible for HUD capture comparisons), `-Dvertex.test.showHud=true`
  keeps the GUI drawn during frame captures, `-Dvertex.test.markAudit=true` attributes
  every section re-mark and every direct worldRenderersToUpdate addition to its caller
  (10-second counters per mark entry point plus sampled stacks; the instrument that
  root-caused #118), `-Dvertex.profileRender=true` brackets the five client-thread
  renderer phases (frustum clip, sortAndRender walk, glCallLists submission, rebuild
  pass) and logs a 10-second `[VertexProf]` split plus vanilla's per-frame section
  counters (the baseline instrument for the render-backend work), and
  `-Dvertex.test.guiProbe=true` (with
  `-Dvertex.test.shotDir`) runs an autonomous GUI check from the title screen: it opens
  the Chat Settings screen, screenshots the injected Vertex buttons, clicks Chat
  Background through the real patched actionPerformed path, screenshots the flipped
  label, and exits - placement and the toggle/save round trip verified in one run.
- Every feature lands with either a regression check or a documented manual verification,
  plus a benchmark when it claims a measurable win. Claims without numbers stay out of
  FEATURES.md status upgrades.

- Release audit procedure (run before any tagged build): clone main into a fresh
  directory, `./gradlew clean build test`, then launch the produced jar against the
  official client with a brand-new game directory (only a world save copied in as a test
  fixture). Pass criteria: all classes patch, the config file generates itself, the world
  joins, zero unexpected self-disables. Every release attaches a `SHA256SUMS` file
  computed over the published assets (`shasum -a 256 *.jar > SHA256SUMS`) so downloads
  are verifiable. Verified for 0.2.0 on 2026-08-05: reproducible
  build, 63/63 tests, 11/11 classes patched, no stale or uncommitted state required.
