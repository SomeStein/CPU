# Architecture

## Top-Level Shape

The repository is split into six layers.

1. Launch entrypoints
2. Python orchestration backend
3. Saved profile store
4. Language-specific benchmark workers
5. Swing launch deck
6. Tracked run history

## Launch Layer

Primary entrypoints:

- [`scripts/launcher.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/launcher.py)
- [`scripts/run-task.sh`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.sh)
- [`scripts/run-task.cmd`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.cmd)
- [`.vscode/tasks.json`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/.vscode/tasks.json)

Responsibilities:

- resolve host entrypoints
- build Java classes for the UI and Java benchmarks
- compile host-native benchmark binaries when local toolchains are available
- prefer repo-bundled runtimes and binaries
- launch the Swing controller

## Python Backend

The Python layer is the orchestration and analysis boundary.

Key modules:

- [`scripts/benchkit/common.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/common.py)
- [`scripts/benchkit/catalog.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/catalog.py)
- [`scripts/benchkit/profiles.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/profiles.py)
- [`scripts/benchkit/build.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/build.py)
- [`scripts/benchkit/runtimes.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/runtimes.py)
- [`scripts/benchkit/suite.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/suite.py)
- [`scripts/benchkit/history.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/benchkit/history.py)
- [`scripts/controller_api.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/controller_api.py)

Responsibilities:

- host detection and path resolution
- implementation catalog and runtime lookup
- built-in and custom profile discovery
- draft validation and custom profile saving
- case expansion and worker execution
- result normalization and persistence
- legacy history import
- backend commands consumed by the Swing UI

## Profile Store

Profile files use one stable `.testrun.json` schema:

- `schema_version`
- `id`
- `name`
- `implementations`
- `defaults`
- `matrix`

Storage locations:

- built-in profiles: [`testruns/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns>)
- custom profiles: [`testruns/custom/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns/custom>)

Rules:

- built-in profiles are read-only templates
- custom profiles are persisted through the backend
- temporary drafts can be run without being saved

## Worker Layer

Workers are grouped by language under [`benchmarks/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks>).

Implemented source groups:

- C: [`benchmarks/c/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/c>)
- C++: [`benchmarks/cpp/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/cpp>)
- Go: [`benchmarks/go/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/go>)
- Java: [`benchmarks/java/src/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/java/src>)
- Node: [`benchmarks/node/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/node>)
- Perl: [`benchmarks/perl/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/perl>)
- Python: [`benchmarks/python/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/python>)
- Ruby: [`benchmarks/ruby/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/ruby>)
- Rust: [`benchmarks/rust/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks/rust>)

Worker contract:

- input comes from `--case-file <path>`
- output is one final JSON result
- no progress output is allowed inside the measured region
- scheduler notes and platform extras are optional metadata only

## Swing Launch Deck

The GUI now lives under [`gui/src/cpubench/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/gui/src/cpubench>).

Main classes:

- [`gui/src/cpubench/ControllerApp.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/ControllerApp.java)
- [`gui/src/cpubench/api/BackendClient.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/api/BackendClient.java)
- [`gui/src/cpubench/model/ControllerState.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/model/ControllerState.java)
- [`gui/src/cpubench/model/ProfileDraft.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/model/ProfileDraft.java)
- [`gui/src/cpubench/ui/ControllerFrame.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/ui/ControllerFrame.java)
- [`gui/src/cpubench/ui/ProfileBuilderPanel.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/ui/ProfileBuilderPanel.java)
- [`gui/src/cpubench/ui/DarkTheme.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/ui/DarkTheme.java)

Responsibilities:

- present the launch deck and history browser
- expose built-in and custom profiles
- provide the custom run builder
- stream phase-boundary events during active runs
- render run and global charts from persisted metrics
- inspect raw logs, manifests, and documentation

## Data Flow

1. A saved or temporary profile is selected in the GUI.
2. The Python suite validates the profile definition.
3. The suite expands the profile into case files.
4. Each implementation runs as an isolated worker process.
5. The worker emits exactly one final JSON payload.
6. The suite writes raw logs, result rows, and phase-boundary events after process exit.
7. The GUI reloads stored runs, results, events, and manifests through `controller_api.py`.

## Persisted Formats

Per-run files:

- `manifest.json`
- `results.csv`
- `events.ndjson`
- `cases/*.case`
- `raw/*.log`

Global index:

- [`runs/index.csv`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/runs/index.csv)

The history layer must remain compatible with legacy runs that predate the current schema.
