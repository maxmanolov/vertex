# CTM determination (decision: stays excluded for this release)

The release criteria permit CTM only if a clean-room implementation would deliver a
substantial, measurable performance improvement - not cosmetic compatibility at real cost.

## Cost side, measured

- Runtime tile selection would ride the world-aware icon dispatch, measured live at
  142,000-164,000 resolutions/min at steady state and 2.4M/min during world load; every
  resolution would add at minimum a rule-index lookup, and CTM-matched faces a 12-neighbor
  connectivity probe (the tested engine's requirement).
- Atlas integration is blocked outright by vanilla's own API (`registerIcon` rejects the
  slash-containing tile paths the pack format requires - recorded with the exception in
  docs/OPEN-QUESTIONS.md). Both workarounds carry exactly the costs this project exists
  to avoid: brittle deep reflection into the stitcher, or per-face texture binds that
  destroy batching.

## Benefit side

- CTM is strictly additive work in this renderer: it selects *different* sprites; it
  never reduces faces, draw calls, atlas binds, or state changes. There is no mechanism
  by which it improves rendering performance in the 1.7.10 display-list pipeline, and
  therefore no benefit to demonstrate against the criteria.
- Comparative benchmarking of OptiFine's own binary CTM path was considered and is
  recorded as impractical to automate here: OptiFine has no headless instrumentation
  channel, and this project's harness cannot drive its profile. A manual side-by-side
  remains possible for the curious; it cannot change the sign of the cost/benefit above.

## Decision

Excluded for 0.3.0: a cosmetic feature with measured positive cost, zero performance
upside, and a vanilla API constraint at its foundation. The tested engine, parser and
scanner remain merged should a future compatibility-driven (not performance-driven)
revisit be justified.
