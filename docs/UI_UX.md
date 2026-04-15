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

The controller is divided into four zones.

1. Hero header
2. Sidebar
3. Analytics workspace
4. Inspector workspace

### Hero Header

Contains:

- profile selector
- primary run action
- refresh action
- docs action
- status pill
- progress bar

### Sidebar

Contains:

- profile blueprint summary
- live monitor summary
- interactive result filters

### Analytics Workspace

Contains:

- a metric trend line chart
- a best-by-implementation bar chart
- cycle and counter context in the inspector when the host/runtime can provide it
- a monitoring tab for event flow and the live feed

### Inspector Workspace

Contains:

- runs table
- results table
- events table
- structured result inspector
- raw log viewer
- manifest viewer

## Interaction Model

- selecting a run loads results, events, and manifest together
- selecting a result loads its raw log and the structured detail panel
- selecting a result also exposes optional cycle and platform-counter fields without synthesizing missing values
- filters apply only to the result layer and the charts
- live monitoring updates from event boundaries only
- help menu entries open repo documentation inside the app

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
