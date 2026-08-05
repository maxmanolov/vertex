# Open questions

## RESOLVED: the icon dispatch is reliable; the pack was innocent

`VertexIcons.adjust` sits at every return site of RenderBlocks' world-aware
`getBlockIcon` (`blm.a(Laji;Lahl;IIII)Lrf;`). One soak measured ~164k invocations/min and
armed better grass; a later soak measured zero. Because those two runs differed in *two*
ways - a code change and an enabled resource pack - neither could be blamed.

A controlled A/B settled it: the same merged jar, same world, same churn, toggling only
the resource pack.

| leg | iconHits/min | better grass armed | rebuilds/min |
|---|---|---|---|
| pack disabled | 172,098 | yes | 463 |
| pack enabled | 155,957 | yes | 463 |

**Conclusion:** the dispatch fires reliably and is pack-independent. The zero-hit run was
specific to the then-unmerged natural-textures build, which is therefore the suspect and
must be re-tested in isolation before it is merged. Better grass's status is restored to
matched on this stronger evidence (two independent arming observations), and CTM is
unblocked.

**Method note kept deliberately:** the original single-observation merge of better grass
was overconfident, and correcting it cost one cycle. Two independent observations under a
controlled variable is now the bar for promoting a feature's status in FEATURES.md.

## Open: does the natural-textures build suppress the dispatch?

The unmerged mirror-variant implementation coincided with the zero-hit run. Hypotheses:
its added branch throws an `Error` (not `Exception`, so the local catch misses it) on
first invocation, or a class-load failure of `vertex.natural.NaturalVariants` /
`VertexNaturalIcons` inside the dispatch. Next step: restore it, soak it alone, and if the
zero reproduces, catch `Throwable` at the dispatch boundary and log the cause.

## RESOLVED (as a blocker): vanilla refuses slash-containing sprite names

CTM tiles live in packs as `mcpatcher/ctm/<name>/0.png ... 46.png`. Registering them into
the block atlas is the only way they gain atlas coordinates and therefore the only way an
`IIcon` exists to hand back from the icon dispatch.

Registration was implemented against `TextureMap.loadTextureAtlas` (hooked before
stitching, the correct point) and failed with vanilla's own validation:

    java.lang.IllegalArgumentException: Name cannot contain slashes!
        at bpz.a(SourceFile:303)   // TextureMap.registerIcon

So the supported API cannot express the documented pack layout. Remaining routes:

1. **Synthetic sprite injection** - construct TextureAtlasSprite instances, load their
   images manually, and insert them into TextureMap's private registry so the stitcher
   places them. Feasible, but reaches into stitcher internals and is brittle.
2. **Standalone textures** - bind each tile outside the atlas. Correct, and disqualifying:
   one texture bind per face destroys batching in a mod whose purpose is performance.

CTM is therefore excluded for the 0.2.x line with this cause. Everything below the atlas
problem - connectivity math, parsing, rule indexing, pack scanning, tile ordering - is merged
and tested, so a revisit resumes rather than restarts.
