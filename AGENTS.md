# AGENTS.md

## Purpose

This file is a handoff guide for future coding agents working in this repository.

## Core Rules

- preserve support for `windows-x64` and `macos-arm64` only
- do not reintroduce `macos-x64`
- keep the main application as the only supported prep-and-run workflow
- keep monitoring controller-side and measured loops silent
- keep run history backward compatible with legacy tracked runs
- treat cycle counts as optional host-specific data and never invent or estimate them

## Source Boundaries

Keep source responsibilities clearly separated:

- workspace tooling and orchestration live in [`scripts/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/scripts>)
- backend orchestration modules live in [`scripts/benchkit/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/scripts/benchkit>)
- Swing UI sources live in [`gui/src/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/gui/src>)
- benchmark worker sources live in [`benchmarks/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks>)
- built-in profiles live in [`testruns/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns>)
- editable custom profiles live in [`testruns/custom/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/testruns/custom>)

Do not drift back toward mixing GUI code, worker code, and orchestration code in the same tree.

## Timing-Safety Rules

- never add log output inside measured loops
- never add GUI callbacks inside measured loops
- never add writes to `runs/` from inside measured loops
- event streaming must remain phase-boundary only
- graphs and monitors must render from stored results or controller-side events only

## Profile Rules

- keep `.testrun.json` schema compatible
- built-in profiles are read-only templates
- custom profiles are saved to `testruns/custom`
- validate new drafts through the Python backend before saving or running
- prefer extending profiles over hardcoding parameter branches in workers

## Important Paths

- launch flow: [`scripts/launcher.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/launcher.py)
- UI backend: [`scripts/controller_api.py`](/Users/aaronpumm/Desktop/Projekte%20Lokal/GitHub/Testing/CPU/scripts/controller_api.py)
- orchestration core: [`scripts/benchkit/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/scripts/benchkit>)
- Swing UI: [`gui/src/cpubench/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/gui/src/cpubench>)
- benchmark workers: [`benchmarks/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/benchmarks>)
- bundled runtimes: [`tools/runtime/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/runtime>)
- bundled native binaries: [`tools/bin/`](</Users/aaronpumm/Desktop/Projekte Lokal/GitHub/Testing/CPU/tools/bin>)

## UI Guidance

- keep the app dark-mode first
- generated icons are preferred over external icon dependencies
- the custom run builder should stay modular and backend-driven
- raw logs, manifests, and validation output should stay inspectable from the UI
- if new docs are added, expose them through the Help menu

## Validation Checklist

Run these after substantial changes:

```bash
python3 -m compileall scripts benchmarks/python
python3 scripts/controller_api.py profiles
python3 scripts/launcher.py summary
python3 scripts/launcher.py analyze
```

Use the application UI to load profiles, create a custom draft, save it, and launch at least one smoke-sized run when the environment allows it.
