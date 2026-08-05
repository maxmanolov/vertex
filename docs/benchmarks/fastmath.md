# Fast Math benchmark (decision: excluded)

**Question:** OptiFine popularized replacing vanilla's 65536-entry sine table with a
4096-entry table ("Fast Math") for cache friendliness. Is that worth porting?

**Method:** `bench/FastMathBench.java` - both tables implemented with vanilla 1.7.10's
indexing scheme, 20M lookups per pattern, coherent (game-like sweeping angles) and random
access, plus worst-case absolute error against Math.sin. JDK 8 (Zulu), Apple Silicon.

**Results:**

| pattern | 64k table | 4k table |
|---|---|---|
| coherent | 0.75 ns/op | 16.5 ns/op |
| random | 1.04 ns/op | 16.7 ns/op |
| worst-case error | 0.000096 | 0.001533 |

**Decision:** excluded. Vanilla is already table-based and effectively free (~1 ns/op;
the 256 KB table fits modern L2 caches outright). The small-table variant measured
dramatically *slower* under the JIT in this harness and is 16x less accurate. There is
no headroom worth chasing on modern hardware; revisit only if profiling ever shows
MathHelper in a real frame profile.
