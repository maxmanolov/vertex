# Cloud geometry cache benchmark

Issue #193 isolates `RenderGlobal.renderClouds` (`b(F)V` in the supported 1.7.10
client). The profiler bracket includes the complete cloud pass, while the A/B switch
`-Dvertex.cloudCache=false` disables only display-list compilation and replay. This
keeps instrumentation, class transformation, world state, and the renderer backend
identical between runs.

## Live result

Both runs used the same build and saved world, the arena backend, render distance 8,
fancy graphics, fancy clouds, and a stationary camera. The first 10-second window was
discarded for chunk-build warmup. Four subsequent windows produced:

| Cache | Cloud time / calls (ms) | Weighted ms/call |
| --- | --- | ---: |
| off | 973.9/8,073; 1,013.7/8,071; 1,019.4/8,142; 1,022.8/8,123 | 0.12434 |
| on | 738.2/7,964; 746.6/8,087; 739.3/8,023; 742.9/8,046 | 0.09237 |

The cache reduces measured cloud-pass CPU time by 25.7%. This is a phase-local result;
it is not an FPS claim.

## Framebuffer check

The deterministic capture harness gained `-Dvertex.test.pinCloudTickBase=N`. For the
three fixed camera angles it pins the cloud tick at `N`, `N+5`, and `N+10`, then resets
the cache after world settle so every capture remains inside one natural 20-tick
rebuild interval. Two 854x480 runs used the same jar and differed only in
`-Dvertex.cloudCache=false` versus `true`.

The cloud silhouettes and coverage matched at all three ages under visual inspection.
In the top 50 rows (a terrain-free sky/cloud crop), per-channel mean absolute RGB error
was 0.809, 1.348, and 0.451 out of 255. The small residual is confined to blended cloud
color and subpixel polygon boundaries; no missing, stale, or displaced cloud region was
observed.

## Verification

- `CloudCacheStateTest` covers hit, expiry, reverse-tick, owner, world, mode, motion,
  drift, and clear decisions without OpenGL.
- `CloudCachePatchTest` verifies the exact mapped method descriptor, one replay guard,
  and finish hooks on original returns only.
- The full unit and standalone benchmark suites pass, and the complete transformer
  accepts the official 1.7.10 `bma.class` (`38,236` to `39,845` bytes in the checked
  build).
