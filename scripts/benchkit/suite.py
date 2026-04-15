from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable

from .common import (
    HOST_ARCH,
    HOST_OS,
    RESULT_FIELD_ORDER,
    ROOT_DIR,
    RUNS_DIR,
    TESTRUNS_DIR,
    build_run_id,
    implementation_metadata,
    next_run_number,
    read_json,
    stringify,
    write_csv_rows,
    write_json,
    write_key_value_file,
)
from .history import append_index_rows
from .runtimes import ResolvedTool, resolve_tool


EventCallback = Callable[[dict[str, str]], None]


@dataclass(frozen=True)
class ImplementationSpec:
    implementation_id: str
    runtime_name: str

    def command(self, runtime: ResolvedTool, case_file: Path, java_output_dir: Path, native_binary: Path) -> list[str]:
        if self.implementation_id == "c_native":
            return [str(native_binary), "--case-file", str(case_file)]
        if self.implementation_id.startswith("python_"):
            return [str(runtime.path), str(ROOT_DIR / "scripts" / f"{self.implementation_id}.py"), "--case-file", str(case_file)]
        if self.implementation_id.startswith("node_"):
            return [str(runtime.path), str(ROOT_DIR / "scripts" / f"{self.implementation_id}.mjs"), "--case-file", str(case_file)]
        if self.implementation_id.startswith("java_"):
            class_name = "bench.JavaSloppy" if self.implementation_id == "java_sloppy" else "bench.JavaOptimized"
            return [str(runtime.path), "-cp", str(java_output_dir), class_name, "--case-file", str(case_file)]
        raise KeyError(f"Unsupported implementation: {self.implementation_id}")


IMPLEMENTATION_SPECS = {
    "c_native": ImplementationSpec("c_native", "native"),
    "python_sloppy": ImplementationSpec("python_sloppy", "python"),
    "python_optimized": ImplementationSpec("python_optimized", "python"),
    "node_sloppy": ImplementationSpec("node_sloppy", "node"),
    "node_optimized": ImplementationSpec("node_optimized", "node"),
    "java_sloppy": ImplementationSpec("java_sloppy", "java"),
    "java_optimized": ImplementationSpec("java_optimized", "java"),
}


def list_profiles() -> list[dict[str, str]]:
    profiles = []
    for path in sorted(TESTRUNS_DIR.glob("*.testrun.json")):
        payload = read_json(path)
        profiles.append(
            {
                "profile_id": payload["id"],
                "name": payload["name"],
                "implementations": ",".join(payload["implementations"]),
                "cases": str(len(payload["matrix"])),
                "warmups": str(payload["defaults"]["warmups"]),
                "repeats": str(payload["defaults"]["repeats"]),
            }
        )
    return profiles


def load_profile(profile_id: str) -> dict:
    path = TESTRUNS_DIR / f"{profile_id}.testrun.json"
    if not path.exists():
        raise FileNotFoundError(f"Unknown profile '{profile_id}'.")
    return read_json(path)


def _resolve_runtime(name: str) -> ResolvedTool:
    if name == "native":
        return ResolvedTool("native", Path("native"), "bundled")
    return resolve_tool(name)


def _event(stream: EventCallback | None, events: list[dict[str, str]], **payload: object) -> None:
    normalized = {key: stringify(value) for key, value in payload.items()}
    events.append(normalized)
    if stream is not None:
        stream(normalized)


def _case_values(profile_id: str, implementation: str, case: dict, defaults: dict, repeat_index: int, warmup: bool) -> dict[str, object]:
    override = case.get("overrides", {}).get(implementation, {})
    effective = {
        "run_id": "",
        "profile_id": profile_id,
        "implementation": implementation,
        "case_id": case["case_id"],
        "iterations": override.get("iterations", case["iterations"]),
        "parallel_chains": override.get("parallel_chains", case["parallel_chains"]),
        "priority_mode": override.get("priority_mode", case.get("priority_mode", defaults["priority_mode"])),
        "affinity_mode": override.get("affinity_mode", case.get("affinity_mode", defaults["affinity_mode"])),
        "timer_mode": override.get("timer_mode", case.get("timer_mode", defaults["timer_mode"])),
        "warmup": "true" if warmup else "false",
        "repeat_index": repeat_index,
    }
    return effective


def run_profile(
    profile_id: str,
    *,
    java_output_dir: Path,
    native_binary: Path,
    stream: EventCallback | None = None,
) -> dict[str, str]:
    profile = load_profile(profile_id)
    timestamp = datetime.now()
    run_number = next_run_number()
    run_id = build_run_id(run_number, timestamp)
    run_dir = RUNS_DIR / run_id
    cases_dir = run_dir / "cases"
    raw_dir = run_dir / "raw"
    run_dir.mkdir(parents=True, exist_ok=False)
    cases_dir.mkdir()
    raw_dir.mkdir()

    events: list[dict[str, str]] = []
    results: list[dict[str, str]] = []
    manifest = {
        "schema_version": 1,
        "run_id": run_id,
        "run_number": run_number,
        "started_at": timestamp.isoformat(timespec="seconds"),
        "profile": profile,
        "host_os": HOST_OS,
        "host_arch": HOST_ARCH,
    }
    write_json(run_dir / "manifest.json", manifest)

    runtimes = {
        "python": resolve_tool("python"),
        "node": resolve_tool("node"),
        "java": resolve_tool("java"),
    }

    defaults = profile["defaults"]
    warmups = int(defaults["warmups"])
    repeats = int(defaults["repeats"])
    step_total = len(profile["implementations"]) * len(profile["matrix"]) * (warmups + repeats)
    step_index = 0

    _event(
        stream,
        events,
        event_type="run",
        phase="started",
        run_id=run_id,
        profile_id=profile_id,
        step_index=0,
        step_total=step_total,
        message=f"Starting profile {profile_id}",
    )

    for implementation in profile["implementations"]:
        spec = IMPLEMENTATION_SPECS[implementation]
        runtime = runtimes.get(spec.runtime_name, ResolvedTool("native", native_binary, "bundled"))
        for case in profile["matrix"]:
            for warmup_index in range(1, warmups + 1):
                case_values = _case_values(profile_id, implementation, case, defaults, warmup_index, True)
                case_values["run_id"] = run_id
                execution_name = f"{implementation}__{case['case_id']}__warmup_{warmup_index}"
                _execute_case(
                    spec=spec,
                    runtime=runtime,
                    case_values=case_values,
                    execution_name=execution_name,
                    java_output_dir=java_output_dir,
                    native_binary=native_binary,
                    run_dir=run_dir,
                    cases_dir=cases_dir,
                    raw_dir=raw_dir,
                    manifest=manifest,
                    results=results,
                    events=events,
                    stream=stream,
                    step_index=step_index + 1,
                    step_total=step_total,
                )
                step_index += 1
            for repeat_index in range(1, repeats + 1):
                case_values = _case_values(profile_id, implementation, case, defaults, repeat_index, False)
                case_values["run_id"] = run_id
                execution_name = f"{implementation}__{case['case_id']}__repeat_{repeat_index}"
                _execute_case(
                    spec=spec,
                    runtime=runtime,
                    case_values=case_values,
                    execution_name=execution_name,
                    java_output_dir=java_output_dir,
                    native_binary=native_binary,
                    run_dir=run_dir,
                    cases_dir=cases_dir,
                    raw_dir=raw_dir,
                    manifest=manifest,
                    results=results,
                    events=events,
                    stream=stream,
                    step_index=step_index + 1,
                    step_total=step_total,
                )
                step_index += 1

    write_csv_rows(run_dir / "results.csv", RESULT_FIELD_ORDER, results)
    append_index_rows(results)
    events_path = run_dir / "events.ndjson"
    events_path.write_text("".join(json.dumps(event, sort_keys=True) + "\n" for event in events), encoding="utf-8")

    status = "success"
    if any(row.get("status") != "success" for row in results):
        status = "partial_failure"

    _event(
        stream,
        events,
        event_type="run",
        phase="completed",
        run_id=run_id,
        profile_id=profile_id,
        status=status,
        step_index=step_total,
        step_total=step_total,
        message=f"Completed profile {profile_id}",
    )
    events_path.write_text("".join(json.dumps(event, sort_keys=True) + "\n" for event in events), encoding="utf-8")
    return {"run_id": run_id, "status": status}


def _execute_case(
    *,
    spec: ImplementationSpec,
    runtime: ResolvedTool,
    case_values: dict[str, object],
    execution_name: str,
    java_output_dir: Path,
    native_binary: Path,
    run_dir: Path,
    cases_dir: Path,
    raw_dir: Path,
    manifest: dict,
    results: list[dict[str, str]],
    events: list[dict[str, str]],
    stream: EventCallback | None,
    step_index: int,
    step_total: int,
) -> None:
    case_file = cases_dir / f"{execution_name}.case"
    write_key_value_file(case_file, case_values)
    command = spec.command(runtime, case_file, java_output_dir, native_binary)
    _event(
        stream,
        events,
        event_type="case",
        phase="launch",
        run_id=manifest["run_id"],
        profile_id=manifest["profile"]["id"],
        implementation=spec.implementation_id,
        case_id=stringify(case_values["case_id"]),
        repeat_index=stringify(case_values["repeat_index"]),
        warmup=stringify(case_values["warmup"]),
        step_index=step_index,
        step_total=step_total,
        command=" ".join(command),
        message=f"Launching {execution_name}",
    )

    completed = subprocess.run(command, cwd=ROOT_DIR, capture_output=True, text=True, check=False)
    raw_log_path = raw_dir / f"{execution_name}.log"
    raw_log_text = f"[command]\n{' '.join(command)}\n\n[stdout]\n{completed.stdout}\n[stderr]\n{completed.stderr}"
    raw_log_path.write_text(raw_log_text, encoding="utf-8")

    row = {
        "run_id": manifest["run_id"],
        "run_number": manifest["run_number"],
        "started_at": manifest["started_at"],
        "profile_id": manifest["profile"]["id"],
        "profile_name": manifest["profile"]["name"],
        "host_os": HOST_OS,
        "host_arch": HOST_ARCH,
        "implementation": spec.implementation_id,
        "case_id": stringify(case_values["case_id"]),
        "warmup": stringify(case_values["warmup"]),
        "repeat_index": stringify(case_values["repeat_index"]),
        "iterations": stringify(case_values["iterations"]),
        "parallel_chains": stringify(case_values["parallel_chains"]),
        "requested_priority_mode": stringify(case_values["priority_mode"]),
        "requested_affinity_mode": stringify(case_values["affinity_mode"]),
        "runtime_name": runtime.name,
        "runtime_source": runtime.source,
        "status": "failed",
        "log_file": str(raw_log_path.relative_to(ROOT_DIR)).replace("\\", "/"),
        "raw_file": str(raw_log_path.relative_to(ROOT_DIR)).replace("\\", "/"),
        "error_message": "",
    }
    row.update(implementation_metadata(spec.implementation_id))

    if completed.returncode == 0:
        try:
            payload = json.loads(completed.stdout.strip())
            row.update(
                {
                    "loop_trip_count": stringify(payload.get("loop_trip_count", "")),
                    "remainder": stringify(payload.get("remainder", "")),
                    "timer_kind": stringify(payload.get("timer_kind", "")),
                    "elapsed_ns": stringify(payload.get("elapsed_ns", "")),
                    "ns_per_iteration": stringify(payload.get("ns_per_iteration", "")),
                    "ns_per_add": stringify(payload.get("ns_per_add", "")),
                    "result_checksum": stringify(payload.get("result_checksum", "")),
                    "applied_priority_mode": stringify(payload.get("applied_priority_mode", "")),
                    "applied_affinity_mode": stringify(payload.get("applied_affinity_mode", "")),
                    "scheduler_notes": stringify(payload.get("scheduler_notes", "")),
                    "pid": stringify(payload.get("pid", "")),
                    "tid": stringify(payload.get("tid", "")),
                    "platform_extras_json": json.dumps(payload.get("platform_extras", {}), sort_keys=True),
                    "status": "success",
                }
            )
        except json.JSONDecodeError as exc:
            row["error_message"] = f"Invalid JSON output: {exc}"
    else:
        row["error_message"] = completed.stderr.strip() or f"Exit code {completed.returncode}"

    results.append({field: stringify(row.get(field, "")) for field in RESULT_FIELD_ORDER})
    _event(
        stream,
        events,
        event_type="case",
        phase="finished",
        run_id=manifest["run_id"],
        profile_id=manifest["profile"]["id"],
        implementation=spec.implementation_id,
        case_id=stringify(case_values["case_id"]),
        repeat_index=stringify(case_values["repeat_index"]),
        warmup=stringify(case_values["warmup"]),
        status=row["status"],
        metric=row.get("ns_per_iteration") or row.get("legacy_cycles_per_iteration", ""),
        metric_kind="ns/iter" if row.get("ns_per_iteration") else ("cycles/iter" if row.get("legacy_cycles_per_iteration") else ""),
        elapsed_ns=row.get("elapsed_ns", ""),
        timer_kind=row.get("timer_kind", ""),
        step_index=step_index,
        step_total=step_total,
        message=f"Finished {execution_name}",
    )
