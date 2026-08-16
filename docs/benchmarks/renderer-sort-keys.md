# Renderer-array distance-key benchmark

## Question

The 1.7.10 client re-sorts the complete section array when the renderer grid loads and
after the camera crosses its movement threshold. Its comparison callback derives both
sections' squared camera distances every time. Does deriving each key once materially
reduce a render-distance-16 sort?

## Method

`bench/RendererSortBench.java` builds the exact RD16 grid size (33 x 16 x 33 = 17,424
sections) and tests deterministic shuffled and nearly-sorted inputs. It warms both
implementations for 20 runs and reports the median of 31 runs. Both legs use
`Arrays.sort`; the only difference is distance derivation inside each comparison versus
one preparation pass and primitive-key comparisons. Array cloning is outside the timed
region.

Run with:

```
javac bench/RendererSortBench.java
java -cp bench RendererSortBench
```

## Result

Zulu JDK 8 on Apple Silicon:

| input | comparator derivation | cached derivation | sort speedup | distance evaluations |
|---|---:|---:|---:|---:|
| shuffled | 2.441 ms | 1.718 ms | 1.42x | 448,858 -> 17,424 (25.8x fewer) |
| nearly sorted | 0.226 ms | 0.204 ms | 1.11x | 40,674 -> 17,424 (2.3x fewer) |

The exact distance-evaluation count is independent of timer noise and makes the
algorithmic reduction explicit.
