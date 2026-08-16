# Active render-section traversal

The arena backend removes most draw-call overhead, which makes the remaining full-grid
CPU walks visible at high render distance. This benchmark checks whether clipping and
render traversal should visit every allocated renderer or only completed sections that
carry geometry.

## Method

The client ran the `soakworld` fixture at render distance 16 with the arena backend,
multi-core building enabled, an uncapped frame rate, and `-Dvertex.profileRender=true`.
Both variants used the same world, camera position, graphics settings, Zulu Java 8, and
Apple M3 macOS OpenGL environment. Initial windows were discarded until `pendingDirty=0`
and the worker queue was empty.

The isolated Java 8 benchmark in `bench/ActiveSectionWalkBench.java` models the mapped
frustum/traversal classification loop with the live steady-state population: 17,424
allocated renderers versus 4,367 mesh-bearing renderers. It reports the median of nine
samples after warmup; each sample performs 20,000 complete walks.

## Results

The live scene produced the same steady-state render classification in both variants:
1,352 opaque renderers, 3,015 clipped mesh renderers, and 239 translucent render lists.

| client-thread phase | full grid | active registry | reduction |
|---------------------|-----------|-----------------|-----------|
| frustum clipping | 0.134 ms/frame | 0.066 ms/frame | 51.0% |
| traversal excluding submission | 0.106 ms/frame | 0.079 ms/frame | 25.4% |
| combined | 0.239 ms/frame | 0.144 ms/frame | 39.7% |

The isolated walk measured 6,960-7,125 ns for the complete array and 3,163-3,291 ns for
the active array across three process runs, a 2.16-2.20x speedup. Active entries are
distributed across the complete-array fixture, and sample order alternates to avoid a
systematic first-run advantage.

Representative drained active-registry window:

```text
[VertexProf] window=10.0s frames=6069 clip=365.0ms/6069 sort=1675.9ms/12138
submit=1206.4ms/12138 update=5.6ms/6069 traversal=469.5ms busyPct=20.5
sections[loaded=4367 rendered=1352 clipped=3015 occluded=0 emptyPass=0
lastPassLists=239 pendingDirty=0 buildQ=0]
```

## Design consequence

The implementation retains the complete renderer arrays for toroidal repositioning,
dirty scheduling, and startup fallback. A client-thread registry publishes a renderer
only after a completed build reports geometry in at least one pass, and retires it before
an actual section reposition. Rendering and frustum clipping consume a correctly typed
snapshot of that registry. The complete arrays remain in use until at least 27 active
entries exist because the mapped occlusion path unconditionally accesses that prefix.

Worker builds update membership only after client-thread installation. Movement sorts
also synchronize the registry order, preserving the distance order required by the
range/occlusion helpers. Any reflection or registry failure returns the original arrays.
