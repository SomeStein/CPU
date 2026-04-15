# CPU Benchmark Launch Deck

This repository is a self-hosted benchmark lab for `windows-x64` and `macos-arm64`.

It combines:

- a cross-platform launcher and task flow
- a multi-language benchmark suite
- tracked run artifacts and history
- a dark-mode Swing controller with live monitoring, graphs, raw logs, and history analysis

## Supported Targets

- `macos-arm64`
- `windows-x64`

Explicitly out of scope:

- `macos-x64`
- Windows ARM
- Linux

## What Ships Here

The benchmark suite currently includes:

- `c_native`
- `python_sloppy`
- `python_optimized`
- `node_sloppy`
- `node_optimized`
- `java_sloppy`
- `java_optimized`

Saved run profiles live in [`testruns/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns>) and currently include:

- `smoke`
- `balanced`
- `stress`

## Core Commands

VS Code tasks are wired through [`scripts/run-task.sh`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.sh) and [`scripts/run-task.cmd`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/run-task.cmd).

Direct CLI usage:

```bash
python3 scripts/launcher.py build
python3 scripts/launcher.py run --profile balanced
python3 scripts/launcher.py analyze
python3 scripts/launcher.py summary
python3 scripts/analyze_runs.py
```

## UI Overview

The Swing app is intentionally split into controller-side UX and worker-side execution:

- the launcher window is dark-mode only
- runs are started from a profile selector in the hero bar
- menus expose refresh, run, and in-app documentation actions
- profile blueprints are shown in the left sidebar
- result filters update tables and graphs interactively
- the performance tab renders stored metrics only
- the monitoring tab renders controller-side live events only
- raw logs, manifest data, cycle/counter details when available, and result inspection are exposed in dedicated panes

UI entrypoint:

- [`controller/src/cpubench/ControllerApp.java`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/controller/src/cpubench/ControllerApp.java)

## Timing Safety

Timing isolation is the main invariant of the repository.

- measured loops do not print progress
- measured loops do not update GUI state
- measured loops do not write files
- the controller only reacts before launch and after each case completes
- live monitoring is built from event boundaries and stored result rows
- cycle or counter details are only surfaced from final worker payloads or legacy imported artifacts

Read the detailed contract in [`docs/TIMING_ISOLATION.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/TIMING_ISOLATION.md).

## Run Artifacts

Every modern run stores:

- `manifest.json`
- `results.csv`
- `events.ndjson`
- `cases/*.case`
- `raw/*.log`

Legacy runs are still supported and are imported into the same analysis flow.

Tracked history starts in:

- [`runs/index.csv`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/runs/index.csv)

## Runtime Bundles

The launcher prefers repo-local runtimes first and falls back to system tools if the bundles are not present.

Bundle layout is documented in:

- [`tools/runtime/README.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/tools/runtime/README.md)

For fully self-contained fresh-clone operation, place the expected Python, Node, and Java runtimes into those directories.

## Repository Guide

Start here:

- [`docs/ARCHITECTURE.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/ARCHITECTURE.md)
- [`docs/UI_UX.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/UI_UX.md)
- [`docs/TIMING_ISOLATION.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/docs/TIMING_ISOLATION.md)
- [`AGENTS.md`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/AGENTS.md)
