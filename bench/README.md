# Local client benchmark

Use the quick benchmark to compare Vanilla 1.7.10 with Vertex or a supported
OptiFine 1.7.10 JAR on one Windows computer. You do not need a plan file or a
process ID.

## Quick start

Requirements:

- Install Minecraft Java Edition 1.7.10 with the official launcher.
- Start Vanilla 1.7.10 one time, and then close the game and launcher.
- Supply the Vertex or OptiFine JAR that you want to test.

Build the Windows package:

```text
gradlew.bat benchmarkDist benchmarkTest
```

Extract `build/distributions/vertex-benchmark-<version>-windows.zip`. Then use
one of these methods:

1. Drag one or more supported client JAR files onto `benchmark.cmd`.
2. Start `benchmark.cmd`, and select the JAR files in the file window.

Do not use the computer while the benchmark runs. The result opens when the
test is complete.

Quick mode records raw game-loop intervals through the neutral controller. It
does not need a separate frame collector, administrator access, or a process
ID. It does not measure display-present events or dropped display frames.

## Test scenarios

The quick benchmark uses the same neutral scenario controller in each standard
client. It creates a new offline world with a fixed seed for each run. It tests:

- Static world rendering with a fixed camera.
- Chunk travel and loading at 24 blocks per second.
- 1,920 block and lighting updates per second.
- 160 moving pigs with AI, pathfinding, and collision.

Each workload runs for five untimed seconds after its setup and the preceding
scenario's cleanup before frame intervals are recorded.

The report shows each scenario separately. It also shows an equal-weight
combined index. The combined index is not an FPS value.

The standard preset runs three repetitions for each client. The fast preset
runs one repetition and is only a smoke comparison:

```text
java -jar vertex-benchmark.jar quick client.jar --preset fast
```

## Safety and isolation

The quick benchmark uses the installed Vanilla 1.7.10 libraries, assets, and
legacy Java runtime. A client JAR does not contain these files.

The tool does not read launcher account files. It uses an offline benchmark
identity. It creates a separate game directory, world, native directory, log,
and capture for each run under `%LOCALAPPDATA%\VertexBenchmark`. It does not
write to the installed Minecraft directory.

The tool checks JAR structure before it runs a file. It does not run an unknown
JAR. This structure check is not a security scan. Use JAR files from a source
that you trust.

## Supported clients

- Vanilla 1.7.10 is the automatic baseline.
- Vertex JARs use the automatic standard-client adapter.
- Supported OptiFine 1.7.10 JARs use the automatic standard-client adapter.
- Lunar Client does not use a standalone client JAR. Use the advanced manual
  workflow for an installed Lunar Client. Do not compare its manual result with
  the automatic scenario index as if both test methods are the same.

## Output

The tool opens `summary.html`. The quick report includes mean FPS, 1% low FPS,
0.1% low FPS, game-loop interval percentiles, and the change from Vanilla. It
keeps the raw CSV and one JSON record for each capture.

Do not compare reports from different computers. Keep the power mode, display
mode, graphics driver, and background programs constant.

## Advanced manual workflow

The JSON workflow remains available for clients that the quick adapter cannot
start. Copy `bench/profiles.example.json`, edit it, and validate it:

```text
java -jar vertex-benchmark.jar validate --plan bench/profiles.local.json
```

Run the plan:

```text
java -jar vertex-benchmark.jar run --plan bench/profiles.local.json
```

Manual mode asks you to start the client, load a repeatable scene, enter the
render process ID, and keep the client focused during capture. Use the same
external frame collector and the same scene for all clients in one manual
report. On Windows, use the [PresentMon console application](https://github.com/GameTechDev/PresentMon/blob/main/README-ConsoleApplication.md).
Do not use the Vertex-only churn driver for a cross-client result.

The harness writes configured paths, metadata, and instructions to the manual
result. Do not put passwords, tokens, session IDs, or account data in a plan.

To analyze one existing capture:

```text
java -jar vertex-benchmark.jar analyze --csv frames.csv --metric presented
```
