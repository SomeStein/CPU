# Timing Isolation

## The Rule

Nothing about monitoring, documentation, logging, graphing, or UI should change benchmark timing inside the measured loop.

## Allowed

- launching a worker process
- recording a controller event before launch
- waiting for the worker to finish
- parsing the worker JSON payload after exit
- writing raw logs after the worker has exited
- rendering UI updates when the controller receives launch/finish events
- rendering graphs from persisted results

## Forbidden

- printing progress from inside the measured section
- writing files from inside the measured section
- calling GUI code from benchmark workers
- polling the worker during the measured section
- inserting timing probes solely for the UI
- adding benchmark-side debug callbacks

## Practical Consequences

The repository follows a strict two-boundary model.

Boundary 1:

- `launch`

Boundary 2:

- `finished`

Everything visual in the Swing app is driven by those boundaries plus stored result rows.

## Metric Ownership

Worker ownership:

- benchmark execution
- final result payload

Controller ownership:

- progress bar
- live feed
- charts
- result tables
- raw log viewing
- manifest viewing

## Optional Cycles

Cycle counters are platform-specific and not guaranteed on every host.

- legacy Windows runs can expose cycle-based metrics
- modern runs use nanosecond metrics as the primary contract
- the UI surfaces cycle fields when they exist, but does not require them

