# Local client benchmark

Use this harness to compare Minecraft 1.7.10 clients on one computer. The harness uses
one external frame collector for all clients. It does not load Vertex code into the
baseline clients.

## Requirements

- Use JDK 17 to build the harness. The Gradle wrapper supports JDK 8 through JDK 23.
  The output runs on JDK 8.
- On Windows, use the [PresentMon console application](https://github.com/GameTechDev/PresentMon/blob/main/README-ConsoleApplication.md).
- Add your account to the Windows `Performance Log Users` group if PresentMon reports
  an access error.
- Install each client before you start the suite. Supply your own OptiFine files. Use
  manual launch mode for Lunar Client.

The harness does not read launcher account files. It writes the effective plan to the
result directory. This plan includes metadata, instructions, and configured paths. Do
not put tokens, passwords, session IDs, account data, or other credentials in a plan.
Review the result files before you share them.

## Build

On Windows:

```text
gradlew.bat benchmarkJar benchmarkTest
```

On macOS or Linux:

```text
./gradlew benchmarkJar benchmarkTest
```

The output file is `build/libs/vertex-benchmark-<version>.jar`.

## Configure

Copy `bench/profiles.example.json` to `bench/profiles.local.json`. Edit these fields:

- Set `collector.executable` to the PresentMon console executable.
- Set `collector.metric` to `presented` or `displayed`. Do not use `auto` in a suite
  plan. The `auto` value is only for the single-file `analyze` command.
- Set the resolution and game settings to the values that you use.
- Add each client `options.txt` path to `settingsFiles` when the path is stable.
- Change `processName` if the render process does not use `javaw.exe`.
- Add the exact client version or build to `metadata`.

Use one plan for a quality-parity test. Use a different plan for a client-optimized
test. Do not combine these test types in one report.

Validate the plan:

```text
java -jar build/libs/vertex-benchmark-0.3.2.jar validate --plan bench/profiles.local.json
```

## Run

Close all other processes that use the configured `processName` before each capture.
Then run:

```text
java -jar build/libs/vertex-benchmark-0.3.2.jar run --plan bench/profiles.local.json
```

The harness shows the next profile in a seeded, position-balanced order. Every run
uses a full client close and a cooldown as a washout. The order does not balance
continuous carryover because no client stays open between runs. For each run:

1. Start the requested client.
2. Load the same world.
3. Set the same camera position, yaw, pitch, time, and weather.
4. Apply the settings in the plan.
5. Return to the harness and press Enter.
6. Enter the game process ID. On Windows, use Task Manager **Details**, or run
   `Get-Process -Name javaw | Select-Object Id,ProcessName` in PowerShell. Change
   `javaw` if the plan uses a different process name.
7. Return focus to the game during the five-second focus-settle countdown.
8. Do not change focus or open a menu during warm-up or capture.
9. Close the client when the harness requests it. The next run cannot start until the
   target process and any command-launched process have stopped.

Use a static camera for the first suite. For a motion test, use the same pre-generated
course or the same external server for every client. Do not use the Vertex churn driver
for a cross-client result. It is not available in the baseline clients.

## Import mode

Set `collector.type` to `import` to use CSV files from a separate capture workflow. The
harness requests one file for each run. The CSV must contain a supported frame-time
column, `ProcessID`, and the entered game process ID. Supported frame-time columns
include `MsBetweenPresents`, `FrameTime`, `MsBetweenAppStart`, `DisplayedTime`, and
`MsBetweenDisplayChange`. The standalone `analyze` command can read a file without
`ProcessID`, but suite imports require process identity.

## Output

The harness creates a new timestamped suite directory. It does not overwrite an existing
run. The directory contains:

- The effective plan.
- The raw CSV and its SHA-256 value for each run.
- One JSON record for each run.
- JSON, CSV, and Markdown summaries.

The report calculates each run before it combines runs. It uses nearest-rank
percentiles. It reports mean FPS, p50, p95, p99, p99.9, maximum frame time, 1% low, and
0.1% low. It does not remove outliers. A selected series with any dropped frame is
invalid and is not included in aggregate performance results.

Do not compare reports from different computers. Keep the power mode, display mode,
driver, Java version, heap, world, settings, and background programs constant. Use at
least three valid repetitions for each profile.
