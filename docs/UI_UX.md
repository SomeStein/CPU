# UI / UX Notes

## Design Direction

The launch deck is intentionally a dark mission-control dashboard.

Visual principles:

- deep blue surfaces instead of flat black
- cyan and teal accents for action and telemetry
- generated icons instead of external asset dependencies
- modular card sections with large breathable work areas
- separate workspaces for configuration, monitoring, analysis, and inspection

## Main Layout

The controller is divided into three adaptive zones.

1. Hero header
2. Sidebar
3. Workspace tabs

### Hero Header

Contains:

- profile selector
- primary run action
- refresh action
- docs action
- status pill
- progress bar
- fullscreen hints and launch state

### Sidebar

Contains:

- profile blueprint summary
- selected run summary
- run filters
- global filters

### Workspace Tabs

Contains:

- run browser
- custom run builder
- per-run analysis
- live monitor
- global analysis
- artifacts inspection

Each major task gets its own tab so graphs and tables have room instead of being squeezed onto one screen.

## Custom Run Builder UX

The custom builder is meant to feel like part of the main application, not an add-on dialog.

Builder sections:

- profile source and template picker
- profile metadata
- default scheduling/timing controls
- grouped implementation selection
- case matrix editor
- per-implementation override table
- validation and save/run feedback

Builder actions:

- `New`
- `Load`
- `Duplicate Built-In`
- `Save`
- `Save As`
- `Run Now`
- `Revert`

Builder rules:

- built-in profiles are templates
- custom profiles are editable and saved under `testruns/custom`
- temporary drafts can run without being persisted
- all validation is delegated to the Python backend before save or run

## Monitoring UX

Live monitoring must remain timing-safe.

- live status is driven by controller-side phase-boundary events only
- worker loops never send progress callbacks
- graphs update from completed samples after they finish
- logs shown in the UI come from controller events or stored raw logs

The monitor views should emphasize:

- run progress
- active implementation and case
- elapsed and latest metric context
- event flow and persisted artifacts

## Analysis UX

Analysis is intentionally split into per-run and global scopes.

- run analysis focuses on the currently loaded run
- global analysis compares stored results across runs
- artifacts focus on one selected result row

This separation keeps filters understandable and gives each chart enough space to stay readable.

## Interaction Guardrails

The controller must never:

- read timing from inside worker loops
- request high-frequency callbacks from workers
- write monitoring artifacts from inside measured regions
- infer missing cycle data or synthesize counters

The controller may:

- build new graphs from persisted result rows
- compare stored runs globally
- validate and save custom profiles
- open repository documentation inside the app
