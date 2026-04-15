from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from .common import (
    IMPLEMENTATION_METADATA,
    RESULT_FIELD_ORDER,
    RUNS_DIR,
    metric_value,
    parse_float,
    parse_int,
    read_json,
    read_csv_rows,
    write_csv_rows,
)


def _legacy_row_to_result(row: dict[str, str]) -> dict[str, str]:
    metadata = IMPLEMENTATION_METADATA.get(row.get("implementation", ""), {"language": "", "variant": ""})
    return {
        "run_id": row.get("batch_name", ""),
        "run_number": row.get("run_number", ""),
        "started_at": row.get("timestamp", ""),
        "profile_id": "legacy",
        "profile_name": "Legacy Imported Runs",
        "host_os": "windows",
        "host_arch": "",
        "implementation": row.get("implementation", ""),
        "language": metadata["language"],
        "variant": metadata["variant"],
        "case_id": "legacy",
        "warmup": "false",
        "repeat_index": "1",
        "iterations": row.get("iterations", ""),
        "parallel_chains": row.get("parallel_chains", ""),
        "loop_trip_count": row.get("loop_trip_count", ""),
        "remainder": row.get("remainder", ""),
        "timer_kind": row.get("timer_kind", ""),
        "elapsed_ns": "",
        "ns_per_iteration": "",
        "ns_per_add": "",
        "legacy_cycles": row.get("cycles", ""),
        "legacy_cycles_per_iteration": row.get("cycles/iteration", ""),
        "legacy_cycles_per_add": row.get("cycles/add", ""),
        "result_checksum": row.get("result", ""),
        "requested_priority_mode": "high" if row.get("priority_set") == "1" else "unchanged",
        "requested_affinity_mode": "single_core" if row.get("requested_affinity_mask") == "0x1" else "",
        "applied_priority_mode": "high" if row.get("priority_set") == "1" else "unchanged",
        "applied_affinity_mode": "single_core" if row.get("requested_affinity_mask") == "0x1" else "",
        "scheduler_notes": "legacy key/value import",
        "pid": row.get("pid", ""),
        "tid": row.get("tid", ""),
        "runtime_name": "",
        "runtime_source": "legacy",
        "status": "success" if row.get("exit_code") == "0" else "failed",
        "log_file": row.get("log_file", ""),
        "raw_file": row.get("log_file", ""),
        "error_message": "",
        "platform_extras_json": "",
    }


def load_index_rows() -> list[dict[str, str]]:
    index_path = RUNS_DIR / "index.csv"
    if not index_path.exists():
        return []

    rows = read_csv_rows(index_path)
    if not rows:
        return []

    if "run_id" in rows[0]:
        return [{field: row.get(field, "") for field in RESULT_FIELD_ORDER} for row in rows]
    return [_legacy_row_to_result(row) for row in rows]


def write_index_rows(rows: list[dict[str, str]]) -> None:
    index_path = RUNS_DIR / "index.csv"
    write_csv_rows(index_path, RESULT_FIELD_ORDER, rows)


def append_index_rows(new_rows: list[dict[str, str]]) -> None:
    rows = load_index_rows()
    rows.extend([{field: row.get(field, "") for field in RESULT_FIELD_ORDER} for row in new_rows])
    write_index_rows(rows)


def load_run_results(run_id: str) -> list[dict[str, str]]:
    run_dir = RUNS_DIR / run_id
    results_path = run_dir / "results.csv"
    if results_path.exists():
        return read_csv_rows(results_path)

    summary_path = run_dir / "summary.csv"
    if summary_path.exists():
        return [_legacy_row_to_result(row) for row in read_csv_rows(summary_path)]

    return [row for row in load_index_rows() if row.get("run_id") == run_id]


def load_run_manifest(run_id: str) -> dict[str, object]:
    run_dir = RUNS_DIR / run_id
    manifest_path = run_dir / "manifest.json"
    if manifest_path.exists():
        return read_json(manifest_path)

    rows = load_run_results(run_id)
    if not rows:
        return {}

    first = rows[0]
    return {
        "schema_version": 0,
        "run_id": run_id,
        "run_number": parse_int(first.get("run_number", "0")),
        "started_at": first.get("started_at", ""),
        "host_os": first.get("host_os", ""),
        "host_arch": first.get("host_arch", ""),
        "profile": {
            "id": first.get("profile_id", "legacy"),
            "name": first.get("profile_name", "Legacy Imported Runs"),
            "implementations": sorted({row.get("implementation", "") for row in rows if row.get("implementation")}),
            "matrix": [{"case_id": case_id} for case_id in sorted({row.get("case_id", "") for row in rows if row.get("case_id")})],
            "defaults": {},
        },
    }


def load_run_events(run_id: str) -> list[dict[str, str]]:
    run_dir = RUNS_DIR / run_id
    events_path = run_dir / "events.ndjson"
    if events_path.exists():
        events: list[dict[str, str]] = []
        for line in events_path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            payload = json.loads(line)
            events.append({key: str(value) for key, value in payload.items()})
        return events

    rows = load_run_results(run_id)
    if not rows:
        return []

    events = [
        {
            "event_type": "run",
            "phase": "started",
            "run_id": run_id,
            "profile_id": rows[0].get("profile_id", "legacy"),
            "step_total": str(len(rows)),
            "step_index": "0",
            "message": "Synthetic legacy timeline",
        }
    ]
    for index, row in enumerate(rows, start=1):
        events.append(
            {
                "event_type": "case",
                "phase": "finished",
                "run_id": run_id,
                "profile_id": row.get("profile_id", "legacy"),
                "implementation": row.get("implementation", ""),
                "case_id": row.get("case_id", ""),
                "repeat_index": row.get("repeat_index", "1"),
                "warmup": row.get("warmup", "false"),
                "status": row.get("status", "success"),
                "metric": row.get("ns_per_iteration") or row.get("legacy_cycles_per_iteration", ""),
                "metric_kind": "ns/iter" if row.get("ns_per_iteration") else "cycles/iter",
                "elapsed_ns": row.get("elapsed_ns", ""),
                "timer_kind": row.get("timer_kind", ""),
                "step_index": str(index),
                "step_total": str(len(rows)),
                "command": "",
                "message": f"Synthetic finished event for {row.get('implementation', '')}",
            }
        )
    events.append(
        {
            "event_type": "run",
            "phase": "completed",
            "run_id": run_id,
            "profile_id": rows[0].get("profile_id", "legacy"),
            "status": "success",
            "step_total": str(len(rows)),
            "step_index": str(len(rows)),
            "message": "Synthetic legacy completion event",
        }
    )
    return events


def load_raw_text(relative_path: str) -> str:
    target = (RUNS_DIR.parent / relative_path).resolve()
    return target.read_text(encoding="utf-8")


def run_summaries() -> list[dict[str, str]]:
    grouped: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in load_index_rows():
        grouped[row.get("run_id", "")].append(row)

    summaries: list[dict[str, str]] = []
    for run_id, rows in grouped.items():
        measured = [row for row in rows if row.get("warmup") != "true"]
        status = "success"
        if any(row.get("status") != "success" for row in rows):
            status = "partial_failure"
        metric_values = []
        metric_kind = ""
        for row in measured:
            value, kind = metric_value(row)
            if value:
                metric_values.append(parse_float(value))
                metric_kind = kind
        best_metric = min(metric_values) if metric_values else 0.0
        implementations = sorted({row.get("implementation", "") for row in measured if row.get("implementation")})
        cases = sorted({row.get("case_id", "") for row in measured if row.get("case_id")})
        first = rows[0]
        summaries.append(
            {
                "run_id": run_id,
                "run_number": first.get("run_number", ""),
                "started_at": first.get("started_at", ""),
                "profile_id": first.get("profile_id", ""),
                "profile_name": first.get("profile_name", ""),
                "host_os": first.get("host_os", ""),
                "host_arch": first.get("host_arch", ""),
                "status": status,
                "result_count": str(len(measured)),
                "implementation_count": str(len(implementations)),
                "case_count": str(len(cases)),
                "best_metric_value": f"{best_metric:.6f}" if metric_values else "",
                "best_metric_kind": metric_kind,
            }
        )

    summaries.sort(key=lambda row: (parse_int(row.get("run_number")), row.get("run_id")))
    return list(reversed(summaries))
