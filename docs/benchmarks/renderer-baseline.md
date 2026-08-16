# Renderer phase baseline (pre-backend work)

Where does the client thread spend its time in the stock (display-list) renderer? This
is the go/no-go measurement for the render-backend program: it decides which stage of
`legacy display lists -> per-section VBOs -> shared arenas` is worth building, and it is
the yardstick every later backend is benchmarked against.

## Method

`-Dvertex.profileRender=true` (see ARCHITECTURE.md) brackets the five client-thread
renderer phases with nanosecond timers woven at class load:

- **clip** - `RenderGlobal.clipRenderersByFrustum`: per-frame frustum visibility.
- **sort** - `RenderGlobal.sortAndRender`: the renderer walk, RenderList grouping, and
  (nested inside it) submission. **traversal** is derived as sort minus submit.
- **submit** - `RenderGlobal.renderAllRenderLists`: the actual `glCallLists` batches.
- **update** - `RenderGlobal.updateRenderers`: the client-thread rebuild/upload pass
  (includes the multicore drain and immediate rebuilds).

Scenario: standing at spawn in the soak world, peaceful, no motion, multicore on,
capture fixture off, frame rate uncapped, 4-5 minutes per run with the first ~90 s
(initial build drain) discarded. Machine: Apple-silicon Mac, macOS OpenGL. Absolute
per-list costs are driver-specific (Windows drivers execute display lists faster); the
*shape* of the split is what transfers, and it matches the 500 fps RD16 ceiling
observed on a Windows desktop.

## Results (steady state, means over the last 8 x 10 s windows)

| metric                    | RD 8            | RD 16           |
|---------------------------|-----------------|-----------------|
| frames per second         | ~248            | ~92             |
| frame time p50 / p99      | 3.7 / ~7 ms     | ~10 / ~18 ms    |
| sections drawn (pass 0)   | 398             | 1,245           |
| sections loaded           | 4,624           | 17,424          |
| translucent sections      | 64              | 172             |
| **submit per frame**      | **2.72 ms (67% of wall)** | **8.26 ms (76% of wall)** |
| traversal per frame       | 0.04 ms         | 0.27 ms         |
| frustum clip per frame    | 0.04 ms         | 0.30 ms         |
| update per frame          | ~0.001 ms       | ~0.006 ms       |
| **per-section submit**    | **6.8 us**      | **6.6 us**      |

## Reading

1. **The renderer is submission-bound.** Two thirds to three quarters of all wall time
   goes into `glCallLists` executing one display list per visible section. Everything
   else the goal's decomposition names - traversal, visibility, sorting, rebuild - sums
   to under 4% of the frame even at RD 16.
2. **Cost is linear in visible sections at a constant ~6.7 us per display list** on
   this driver. The fps ceiling is `1 / (visibleSections x perListCost)`; nothing short
   of reducing per-section submission cost moves it.
3. **Rebuild/upload is a non-factor in steady state** (worker tessellation is off-thread
   and the drain is budgeted), so backend migration work should not regress it but will
   not win anything there either.

## Consequence for the backend program

- Per-section VBOs replace interpretive display-list execution with
  `bind + pointers + glDrawArrays` per section - a meaningful constant-factor cut, and
  the stepping stone that forces the mesh/upload/ownership machinery into shape.
- The real lever is the shared-arena stage: sections become `(buffer, offset, count)`
  ranges, vertices carry baked 1024-region-local coordinates (the same regions vanilla's
  RenderList batching already keys on), and a pass submits a handful of multi-draw
  calls per region instead of ~1,245 per-section commands. That attacks the 6.7 us x N
  term directly.
- Traversal/visibility optimizations are explicitly *not* worth building at this stage:
  the data caps their best case under 4%.

Raw windows: `[VertexProf]` lines, e.g. RD 16:
`window=10.0s frames=921 clip=285.1ms/921 sort=7831.0ms/1842 submit=7586.8ms/1842
update=2.5ms/921 traversal=244.2ms busyPct=81.1 sections[loaded=17424 rendered=1245
clipped=3121 occluded=0 emptyPass=13058 lastPassLists=172]`
