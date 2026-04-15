# UI / UX Notes

## Design Direction

The launcher is intentionally styled as a dark mission-control dashboard.

Visual principles:

- deep blue surfaces instead of flat black
- bright cyan and teal accents for action and telemetry
- warm warning colors for failures and risky states
- rounded cards and panels for separation
- generated icons so the repo does not depend on external assets

## Layout

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
- fullscreen-ready launch state sized to the visible screen bounds

### Sidebar

Contains:

- profile blueprint summary
- selected-run summary
- run filters
- global filters

### Workspace Tabs

Contains:

- runs table
- a dedicated run-analysis workspace with larger charts
- a dedicated live-monitor workspace for event flow and the live feed
- a dedicated global-analysis workspace for cross-run comparison
- an artifacts workspace for detail, raw logs, and manifest inspection
- cycle and counter context in the inspector when the host/runtime can provide it

## Interaction Model

- selecting a run loads results, events, and manifest together
- selecting a result loads its raw log and the structured detail panel
- selecting a result also exposes optional cycle and platform-counter fields without synthesizing missing values
- run filters apply only to the loaded run
- global filters apply only to cross-run analysis
- live monitoring updates from event boundaries only
- help menu entries open repo documentation inside the app
- `F11` toggles fullscreen and `Esc` exits it

## UX Guardrails

The controller must never:

- sample timing inside worker loops
- request frequent worker callbacks
- add UI output from benchmark code
- write monitoring artifacts from measured sections

The controller may:

- render new graphs from persisted result rows
- render live status from launch/finish events
- inspect raw logs after completion
