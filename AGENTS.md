# AGENTS.md

## Purpose

This file is a handoff guide for future coding agents working in this repository.

## High-Level Rules

- preserve support for `windows-x64` and `macos-arm64` only
- do not reintroduce `macos-x64`
- keep monitoring controller-side
- keep worker measurement sections silent
- prefer extending saved profiles over hardcoding new parameters in workers
- keep run history backward compatible with legacy tracked runs
- treat cycle counts as optional host-specific data and never invent or estimate them
- keep the main application as the only supported build-and-run workflow

## Timing-Safety Rules

- never add log output inside the measured loops
- never add GUI callbacks inside the measured loops
- never add writes to `runs/` from inside the measured loops
- event streaming must remain phase-boundary only

## Important Paths

- launch flow: [`scripts/launcher.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/launcher.py)
- UI backend: [`scripts/controller_api.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/controller_api.py)
- orchestration: [`scripts/benchkit/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/scripts/benchkit>)
- Swing UI: [`controller/src/cpubench/ui/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/controller/src/cpubench/ui>)
- benchmark workers: [`scripts/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/scripts>), [`java-src/bench/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/java-src/bench>), [`tsc_benchmark.c`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/tsc_benchmark.c)

## UI Guidance

- keep the app dark-mode first
- generated icons are preferred over external icon dependencies
- chart panels should render from stored data or controller events only
- raw logs and manifests should stay inspectable from the UI
- if new docs are added, surface them through the Help menu

## Runtime Guidance

- bundled runtimes belong under [`tools/runtime/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/runtime>)
- bundled native binaries belong under [`tools/bin/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/bin>)
- launcher fallbacks may use system tools, but should prefer bundled assets first

## Validation Checklist

Run these after substantial changes:

```bash
python3 -m compileall scripts
python3 scripts/analyze_runs.py
python3 scripts/launcher.py analyze
```

Use the application UI to prepare assets and launch smoke or balanced runs. The last command opens a GUI and may need to run outside a restricted sandbox.
