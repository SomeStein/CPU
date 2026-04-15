# Architecture

## Top-Level Shape

The repository is split into five layers.

1. Launch and task entrypoints
2. Python orchestration backend
3. Language-specific benchmark workers
4. Java Swing controller UI
5. Tracked run history and saved profiles

## Launch Layer

Primary launch files:

- [`scripts/launcher.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/launcher.py)
- [`scripts/run-task.sh`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.sh)
- [`scripts/run-task.cmd`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.cmd)
- [`.vscode/tasks.json`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/.vscode/tasks.json)

Responsibilities:

- resolve host entrypoints
- build Java classes and host-native C benchmark binaries
- start headless runs
- launch the Swing controller

## Python Backend

The Python layer is the orchestration and analysis boundary.

Key modules:

- [`scripts/benchkit/common.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/common.py)
- [`scripts/benchkit/build.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/build.py)
- [`scripts/benchkit/runtimes.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/runtimes.py)
- [`scripts/benchkit/suite.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/suite.py)
- [`scripts/benchkit/history.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/history.py)
- [`scripts/controller_api.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/controller_api.py)

Responsibilities:

- host detection
- runtime resolution
- profile loading
- case expansion
- worker execution
- result normalization
- run artifact persistence
- legacy history import
- UI backend commands

## Worker Layer

Workers are intentionally thin and silent during measurement.

Python:

- [`scripts/python_sloppy.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/python_sloppy.py)
- [`scripts/python_optimized.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/python_optimized.py)

Node:

- [`scripts/node_benchmark_common.mjs`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/node_benchmark_common.mjs)
- [`scripts/node_sloppy.mjs`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/node_sloppy.mjs)
- [`scripts/node_optimized.mjs`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/node_optimized.mjs)

Java:

- [`java-src/bench/BenchCommon.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/java-src/bench/BenchCommon.java)
- [`java-src/bench/JavaSloppy.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/java-src/bench/JavaSloppy.java)
- [`java-src/bench/JavaOptimized.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/java-src/bench/JavaOptimized.java)

C:

- [`tsc_benchmark.c`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/tsc_benchmark.c)

Contract:

- input comes from `--case-file`
- output is one final JSON result
- no progress output from the timed section

## Controller UI

The controller is now modular.

Main classes:

- [`controller/src/cpubench/ControllerApp.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/ControllerApp.java)
- [`controller/src/cpubench/api/BackendClient.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/api/BackendClient.java)
- [`controller/src/cpubench/model/TableData.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/model/TableData.java)
- [`controller/src/cpubench/ui/ControllerFrame.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/ui/ControllerFrame.java)
- [`controller/src/cpubench/ui/DarkTheme.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/ui/DarkTheme.java)
- [`controller/src/cpubench/ui/IconFactory.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/ui/IconFactory.java)
- [`controller/src/cpubench/ui/MetricLineChartPanel.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/ui/MetricLineChartPanel.java)
- [`controller/src/cpubench/ui/ImplementationBarChartPanel.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/ui/ImplementationBarChartPanel.java)

Responsibilities:

- present the run deck
- expose saved profiles
- stream live launch/finish events
- render graphs from stored results or live controller-side event summaries
- inspect raw logs and manifest data
- expose repository docs inside the app

## Data Flow

1. A profile is selected in the UI or via CLI.
2. The Python suite expands that profile into case files.
3. Each worker is started as a separate process.
4. The worker returns exactly one JSON payload.
5. The suite writes result rows and raw logs after the process exits.
6. The suite emits controller-side events before launch and after completion.
7. The UI reads runs, results, events, manifests, and logs through `controller_api.py`.

## Persisted Formats

Profile files:

- [`testruns/*.testrun.json`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns>)

Per-run files:

- `manifest.json`
- `results.csv`
- `events.ndjson`
- `cases/*.case`
- `raw/*.log`

Global index:

- [`runs/index.csv`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/runs/index.csv)

