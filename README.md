# CPU Lab

This repository is a self-hosted benchmark lab for `windows-x64` and `macos-arm64`.

It is organized around one main application:

- the Swing launch deck opens the GUI
- the GUI prepares assets, runs profiles, and analyzes stored history
- the Python backend owns orchestration, validation, persistence, and legacy import

## Supported Targets

- `macos-arm64`
- `windows-x64`

Explicitly out of scope:

- `macos-x64`
- Windows ARM
- Linux

## Source Layout

The repo is now split into explicit source domains for maintainability:

- workspace tooling and orchestration: [`scripts/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/scripts>)
- backend orchestration modules: [`scripts/benchkit/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/scripts/benchkit>)
- Swing GUI sources: [`gui/src/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/gui/src>)
- benchmark sources by language: [`benchmarks/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks>)
- saved built-in profiles: [`testruns/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns>)
- saved custom profiles: [`testruns/custom/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns/custom>)
- tracked run history: [`runs/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/runs>)
- bundled runtimes and native binaries: [`tools/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools>)

## Benchmark Suite

The suite now exposes one canonical implementation per language:

- `c`
- `cpp`
- `rust`
- `go`
- `java`
- `node`
- `python`
- `ruby`
- `perl`

Built-in profiles include:

- `smoke`
- `balanced`
- `stress`

Custom profiles are saved as `.testrun.json` files under [`testruns/custom/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns/custom>).

## Main Workflow

VS Code tasks are wired through [`scripts/run-task.sh`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.sh) and [`scripts/run-task.cmd`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.cmd).

Primary entrypoints:

```bash
python3 scripts/controller_api.py prepare-host --download-missing
python3 scripts/controller_api.py readiness
python3 scripts/controller_api.py self-check
python3 scripts/controller_api.py package-portable
python3 scripts/launcher.py analyze
python3 scripts/launcher.py summary
python3 scripts/analyze_runs.py
```

`build` and `run` are intentionally no longer supported as top-level workflow commands. The launch deck owns asset preparation and run execution now.

## GUI Overview

The Swing app is now a VSCode-style dark shell with:

- an activity rail that swaps side-panel modes
- a hideable side panel for runs, filters, monitor context, artifacts, profiles, and docs
- single-row workspace tabs in the center
- interactive metric charts with hover details, pan, and zoom
- colored language icons across tables, builders, legends, and tooltips
- raw log and manifest inspection backed by stored artifacts only

Main GUI entrypoint:

- [`gui/src/cpubench/ControllerApp.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/gui/src/cpubench/ControllerApp.java)

Custom run builder capabilities:

- create a new draft from scratch
- load built-in or custom profiles into the editor
- duplicate built-in profiles into editable custom drafts
- edit implementations, defaults, case matrix rows, and per-implementation overrides
- validate drafts through the Python backend
- save custom profiles to `testruns/custom`
- run temporary drafts without changing the tracked built-in profiles

## Timing Safety

Timing isolation is the main invariant of the repository.

- measured loops do not print progress
- measured loops do not update GUI state
- measured loops do not write files
- the controller only reacts before launch and after each case completes
- live monitoring is built from phase-boundary events and persisted results
- cycle or counter details are only surfaced when workers or legacy imports provide them

Read the detailed contract in [`docs/TIMING_ISOLATION.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/TIMING_ISOLATION.md).

## Run Artifacts

Every modern run stores:

- `manifest.json`
- `results.csv`
- `events.ndjson`
- `cases/*.case`
- `raw/*.log`

Legacy runs are still supported and imported into the same analysis flow.

Global tracked history starts in:

- [`runs/index.csv`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/runs/index.csv)

## Runtime Bundles

Pinned runtime and toolchain manifests now live under [`tools/manifests/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/manifests>). `prepare-host` installs repo-local dependencies from those manifests before packaging or launch validation.

Bundle layout:

- runtimes: [`tools/runtime/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/runtime>)
- toolchains: [`tools/toolchains/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/toolchains>)
- native binaries: [`tools/bin/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/bin>)

The compiled language pack is designed around checked-in host binaries, but the backend can also compile host-native assets when a local toolchain is available.

## Documentation Map

Start here:

- [`docs/ARCHITECTURE.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/ARCHITECTURE.md)
- [`docs/UI_UX.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/UI_UX.md)
- [`docs/TIMING_ISOLATION.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/TIMING_ISOLATION.md)
- [`AGENTS.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/AGENTS.md)
- [`AGNETS.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/AGNETS.md)
