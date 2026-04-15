# UI / UX Notes

## Design Direction

CPU Lab now presents itself as a dark, editor-like shell instead of a hero-dashboard.

Visual principles:

- dark navy surfaces with teal and amber accents
- thin rounded scrollbars instead of native heavy chrome
- activity-first navigation with a collapsible side panel
- language-first visuals using colored SVG badges
- charts and inspectors that favor direct manipulation over nested tabs

## Main Layout

The controller is divided into four zones.

1. Activity bar
2. Side panel
3. Workspace tabs
4. Status bar

### Activity Bar

Contains one icon toggle per mode:

- runs
- analysis
- monitor
- artifacts
- config
- docs

Clicking the active mode again collapses the side panel.

### Side Panel

Contains mode-specific controls:

- run search and stored run browser
- run/global filters
- live monitor summary and event tail
- artifact shortcuts for the current selection
- profile preview and builder shortcut
- bundled documentation shortcuts

### Workspace Tabs

Contains the large working surfaces:

- global overview
- live monitor
- run configuration
- on-demand run overview
- on-demand artifact inspector
- document tabs

Nested tabs were removed so each screen owns one clear layout.

## Custom Run Builder UX

The custom builder is meant to feel like part of the main application, not an add-on dialog.

Builder sections:

- profile source and template picker
- profile metadata
- default scheduling and timing controls
- icon-backed implementation selection
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

The monitor views emphasize:

- run progress
- active implementation and case
- latest metric context
- controller event flow
- persisted artifacts instead of in-loop telemetry

## Analysis UX

Analysis is intentionally split into per-run and global scopes.

- run analysis focuses on the currently loaded run
- global analysis compares stored results across runs
- artifacts focus on one selected result row
- charts support drag-pan and wheel zoom without native scrollbars

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
