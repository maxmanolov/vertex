# Scheduled-tick chunk-query benchmark

Issue #189 replaces the integrated server's scheduled-tick membership set with a
chunk-keyed index. The existing time-ordered tree remains authoritative; only the
candidate iterator used by a chunk save/unload query is narrowed to the target chunk and
its eight neighbors.

## Method

`bench/ScheduledTickIndexBench.java` creates 120,000 scheduled ticks across a 40 x 40
loaded-chunk area. It compares the mapped 1.7.10 query shape (scan the full time-ordered
set and test the expanded chunk bounds) with the indexed nine-bucket query. It separately
measures scheduling into both the membership set and the unchanged time-order tree, so a
faster query cannot hide excessive insertion overhead.

Run after compiling the main classes:

```text
./gradlew classes
javac -cp build/classes/java/main bench/ScheduledTickIndexBench.java
java -cp build/classes/java/main:bench ScheduledTickIndexBench
```

Environment: Zulu OpenJDK 8u502, macOS arm64, Apple Silicon, 2026-08-16. Three fresh JVM
runs produced query speedups of 40.7x, 37.2x, and 38.6x. The representative final run was:

```text
ticks=120000 queryMatches=93 rawQueryMs=0.7125 indexedQueryMs=0.0185 querySpeedup=38.6x
rawScheduleMs=10.811 indexedScheduleMs=11.448 scheduleRatio=1.06x
```

The result is deliberately a data-structure microbenchmark, not an FPS claim. It isolates
the exact work changed by the patch: full-world candidate discovery during chunk
save/unload, plus the steady-state cost of maintaining the replacement membership set.
