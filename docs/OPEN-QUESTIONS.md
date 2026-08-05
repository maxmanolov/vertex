# Open questions

## The icon dispatch fires inconsistently between runs (blocking)

`VertexIcons.adjust` is installed at every return site of RenderBlocks' world-aware
`getBlockIcon(Block, IBlockAccess, x, y, z, side)` (`blm.a(Laji;Lahl;IIII)Lrf;`), which
static analysis shows has 37 internal call sites - apparently the in-world face-sprite
choke point.

Observed, all with the class confirmed patched (`Patching RenderBlocks (blm)`, no
`Failed to patch`), the world joined, and ~450 chunk rebuilds/min under harness churn:

| run | iconHits/min | iconSideHits/min | better grass armed |
|---|---|---|---|
| instrumented probe | 164,418 | 94,378 | yes |
| instrumented probe (2nd window) | 146,161 | 83,235 | - |
| natural-textures soak (window 1) | 0 | 0 | no |
| natural-textures soak (window 2) | 0 | 0 | no |

The zero runs still rendered: 2.7M GL state calls/min and 465 chunk rebuilds/min.

Candidate explanations, none yet tested:
1. A resource pack was enabled in the zero runs (the natural-textures verification pack).
   A resource reload may route block rendering through a different path or re-resolve
   sprites elsewhere.
2. Vanilla may reach face sprites through `Block.getIcon(IBlockAccess, x, y, z, side)`
   (`aji.e`) directly in some render paths, bypassing the RenderBlocks wrapper.
3. Render-type dependence: the sampled terrain may differ enough between runs to change
   which render methods execute.

**Consequences:** better grass (PR #23) was merged as *matched* on a single arming
observation and has been corrected down to *in progress*. CTM, natural textures and
emissive were all designed to dispatch from this hook; none should be built on it until
the invocation is deterministic and understood.

**Next step:** counter both candidate sites simultaneously (`blm.a(...)` and `aji.e(...)`)
in one build, then soak twice - once with a resource pack enabled and once without - and
compare. That isolates hypotheses 1 and 2 in a single pass.
