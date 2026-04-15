from __future__ import annotations

import ctypes
import json
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable

from .catalog import IMPLEMENTATIONS, ImplementationEntry, implementation_entry
from .common import (
    HOST_ARCH,
    HOST_OS,
    RESULT_FIELD_ORDER,
    ROOT_DIR,
    RUNS_DIR,
    build_run_id,
    implementation_metadata,
    next_run_number,
    stringify,
    write_csv_rows,
    write_json,
    write_key_value_file,
)
from .history import append_index_rows
from .profiles import load_profile, load_profile_path, profile_location, profile_rows
from .runtimes import ResolvedTool, resolve_tool


EventCallback = Callable[[dict[str, str]], None]


if sys.platform == "win32":
    CREATE_SUSPENDED = 0x00000004
    HIGH_PRIORITY_CLASS = 0x00000080
    TH32CS_SNAPTHREAD = 0x00000004
    THREAD_SUSPEND_RESUME = 0x0002
    THREAD_QUERY_INFORMATION = 0x0040
    INVALID_HANDLE_VALUE = ctypes.c_void_p(-1).value

    class THREADENTRY32(ctypes.Structure):
        _fields_ = [
            ("dwSize", ctypes.c_uint32),
            ("cntUsage", ctypes.c_uint32),
            ("th32ThreadID", ctypes.c_uint32),
            ("th32OwnerProcessID", ctypes.c_uint32),
            ("tpBasePri", ctypes.c_int32),
            ("tpDeltaPri", ctypes.c_int32),
            ("dwFlags", ctypes.c_uint32),
        ]

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.SetProcessAffinityMask.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
    kernel32.SetProcessAffinityMask.restype = ctypes.c_int
    kernel32.SetPriorityClass.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
    kernel32.SetPriorityClass.restype = ctypes.c_int
    kernel32.CreateToolhelp32Snapshot.argtypes = [ctypes.c_uint32, ctypes.c_uint32]
    kernel32.CreateToolhelp32Snapshot.restype = ctypes.c_void_p
    kernel32.Thread32First.argtypes = [ctypes.c_void_p, ctypes.POINTER(THREADENTRY32)]
    kernel32.Thread32First.restype = ctypes.c_int
    kernel32.Thread32Next.argtypes = [ctypes.c_void_p, ctypes.POINTER(THREADENTRY32)]
    kernel32.Thread32Next.restype = ctypes.c_int
    kernel32.OpenThread.argtypes = [ctypes.c_uint32, ctypes.c_int, ctypes.c_uint32]
    kernel32.OpenThread.restype = ctypes.c_void_p
    kernel32.ResumeThread.argtypes = [ctypes.c_void_p]
    kernel32.ResumeThread.restype = ctypes.c_uint32
    kernel32.CloseHandle.argtypes = [ctypes.c_void_p]
    kernel32.CloseHandle.restype = ctypes.c_int


@dataclass(frozen=True)
class LoadedProfile:
    payload: dict
    source: str
    path: Path


def list_profiles() -> list[dict[str, str]]:
    return profile_rows()


def load_profile_record(profile_id: str) -> LoadedProfile:
    source, path = profile_location(profile_id)
    return LoadedProfile(load_profile(profile_id), source, path)


def load_profile_definition(profile_id: str) -> dict:
    return load_profile(profile_id)


def load_profile_from_path(path: Path) -> LoadedProfile:
    resolved = path.resolve()
    source = "temporary"
    try:
        known_source, known_path = profile_location(resolved.stem.replace(".testrun", ""))
        if known_path.resolve() == resolved:
            source = known_source
    except FileNotFoundError:
        if "testruns/custom" in resolved.as_posix():
            source = "custom"
        elif "/testruns/" in resolved.as_posix():
            source = "builtin"
    return LoadedProfile(load_profile_path(resolved), source, resolved)


def run_profile(
    profile_id: str,
    *,
    java_output_dir: Path,
    stream: EventCallback | None = None,
) -> dict[str, str]:
    profile = load_profile_record(profile_id)
    return run_profile_payload(profile, java_output_dir=java_output_dir, stream=stream)


def run_profile_path(
    profile_path: Path,
    *,
    java_output_dir: Path,
    stream: EventCallback | None = None,
) -> dict[str, str]:
    profile = load_profile_from_path(profile_path)
    return run_profile_payload(profile, java_output_dir=java_output_dir, stream=stream)


def run_profile_payload(
    profile: LoadedProfile,
    *,
    java_output_dir: Path,
    stream: EventCallback | None = None,
) -> dict[str, str]:
    payload = profile.payload
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
    if profile.path.is_absolute():
        try:
            profile_path_text = str(profile.path.relative_to(ROOT_DIR)).replace("\\", "/")
        except ValueError:
            profile_path_text = str(profile.path)
    else:
        profile_path_text = str(profile.path)
    manifest = {
        "schema_version": 1,
        "run_id": run_id,
        "run_number": run_number,
        "started_at": timestamp.isoformat(timespec="seconds"),
        "profile": payload,
        "profile_source": profile.source,
        "profile_path": profile_path_text,
        "host_os": HOST_OS,
        "host_arch": HOST_ARCH,
    }
    write_json(run_dir / "manifest.json", manifest)

    defaults = payload["defaults"]
    warmups = int(defaults["warmups"])
    repeats = int(defaults["repeats"])
    step_total = len(payload["implementations"]) * len(payload["matrix"]) * (warmups + repeats)
    step_index = 0

    _event(
        stream,
        events,
        event_type="run",
        phase="started",
        run_id=run_id,
        profile_id=payload["id"],
        step_index=0,
        step_total=step_total,
        message=f"Starting profile {payload['id']}",
    )

    for implementation_id in payload["implementations"]:
        entry = IMPLEMENTATIONS[implementation_id]
        runtime = _resolve_runtime(entry)
        for case in payload["matrix"]:
            for warmup_index in range(1, warmups + 1):
                case_values = _case_values(payload["id"], implementation_id, case, defaults, warmup_index, True)
                case_values["run_id"] = run_id
                execution_name = f"{implementation_id}__{case['case_id']}__warmup_{warmup_index}"
                _execute_case(
                    entry=entry,
                    runtime=runtime,
                    case_values=case_values,
                    execution_name=execution_name,
                    java_output_dir=java_output_dir,
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
                case_values = _case_values(payload["id"], implementation_id, case, defaults, repeat_index, False)
                case_values["run_id"] = run_id
                execution_name = f"{implementation_id}__{case['case_id']}__repeat_{repeat_index}"
                _execute_case(
                    entry=entry,
                    runtime=runtime,
                    case_values=case_values,
                    execution_name=execution_name,
                    java_output_dir=java_output_dir,
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
    status = "success"
    if any(row.get("status") != "success" for row in results):
        status = "partial_failure"

    _event(
        stream,
        events,
        event_type="run",
        phase="completed",
        run_id=run_id,
        profile_id=payload["id"],
        status=status,
        step_index=step_total,
        step_total=step_total,
        message=f"Completed profile {payload['id']}",
    )
    events_path.write_text("".join(json.dumps(event, sort_keys=True) + "\n" for event in events), encoding="utf-8")
    return {"run_id": run_id, "status": status}


def _resolve_runtime(entry: ImplementationEntry) -> ResolvedTool:
    if entry.runner_kind == "native-binary":
        binary_path = entry.binary_path
        if binary_path is not None and binary_path.exists():
            return ResolvedTool(name=entry.binary_stem or entry.implementation_id, path=binary_path, source="repo_binary")
        return ResolvedTool(
            name=entry.binary_stem or entry.implementation_id,
            path=binary_path or Path(entry.implementation_id),
            source="missing",
        )
    return resolve_tool(entry.runtime_kind, required=False)


def _resume_primary_thread(pid: int) -> None:
    snapshot = kernel32.CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD, 0)
    if snapshot == INVALID_HANDLE_VALUE:
        raise OSError(ctypes.get_last_error(), "CreateToolhelp32Snapshot failed")
    try:
        entry = THREADENTRY32()
        entry.dwSize = ctypes.sizeof(THREADENTRY32)
        has_entry = kernel32.Thread32First(snapshot, ctypes.byref(entry))
        while has_entry:
            if entry.th32OwnerProcessID == pid:
                thread = kernel32.OpenThread(THREAD_SUSPEND_RESUME | THREAD_QUERY_INFORMATION, False, entry.th32ThreadID)
                if not thread:
                    raise OSError(ctypes.get_last_error(), "OpenThread failed")
                try:
                    if kernel32.ResumeThread(thread) == 0xFFFFFFFF:
                        raise OSError(ctypes.get_last_error(), "ResumeThread failed")
                    return
                finally:
                    kernel32.CloseHandle(thread)
            has_entry = kernel32.Thread32Next(snapshot, ctypes.byref(entry))
    finally:
        kernel32.CloseHandle(snapshot)
    raise RuntimeError(f"No primary thread found for pid {pid}")


def _spawn_pinned(command: list[str], *, case_values: dict[str, object]) -> subprocess.Popen[str]:
    if sys.platform != "win32":
        return subprocess.Popen(command, cwd=ROOT_DIR, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    process = subprocess.Popen(
        command,
        cwd=ROOT_DIR,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        creationflags=CREATE_SUSPENDED,
    )
    process_handle = ctypes.c_void_p(process._handle)
    try:
        if case_values.get("affinity_mode") == "single_core":
            if not kernel32.SetProcessAffinityMask(process_handle, 1):
                raise OSError(ctypes.get_last_error(), "SetProcessAffinityMask failed")
        if case_values.get("priority_mode") == "high":
            if not kernel32.SetPriorityClass(process_handle, HIGH_PRIORITY_CLASS):
                raise OSError(ctypes.get_last_error(), "SetPriorityClass failed")
        _resume_primary_thread(process.pid)
    except Exception:
        process.kill()
        process.wait(timeout=5)
        raise
    return process


def _command_for_entry(entry: ImplementationEntry, runtime: ResolvedTool, case_file: Path, java_output_dir: Path) -> list[str]:
    if entry.runner_kind == "native-binary":
        if runtime.source == "missing" or not runtime.path.exists():
            raise FileNotFoundError(
                f"Missing native benchmark binary for {entry.implementation_id}. "
                f"Expected {runtime.path.relative_to(ROOT_DIR) if runtime.path.is_absolute() else runtime.path}."
            )
        return [str(runtime.path), "--case-file", str(case_file)]
    if runtime.source == "missing" or (runtime.path.name and not runtime.path.exists()):
        raise FileNotFoundError(
            f"Missing runtime '{entry.runtime_kind}' for {entry.implementation_id}. "
            "Bundle it under tools/runtime or install it on PATH."
        )
    if entry.runner_kind in {"python-script", "node-script", "ruby-script", "perl-script"}:
        assert entry.source_path is not None
        return [str(runtime.path), str(entry.source_path), "--case-file", str(case_file)]
    if entry.runner_kind == "java-class":
        assert entry.java_class is not None
        return [str(runtime.path), "-cp", str(java_output_dir), entry.java_class, "--case-file", str(case_file)]
    raise KeyError(f"Unsupported runner kind: {entry.runner_kind}")


def _event(stream: EventCallback | None, events: list[dict[str, str]], **payload: object) -> None:
    normalized = {key: stringify(value) for key, value in payload.items()}
    events.append(normalized)
    if stream is not None:
        stream(normalized)


def _case_values(profile_id: str, implementation: str, case: dict, defaults: dict, repeat_index: int, warmup: bool) -> dict[str, object]:
    override = case.get("overrides", {}).get(implementation, {})
    return {
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


def _execute_case(
    *,
    entry: ImplementationEntry,
    runtime: ResolvedTool,
    case_values: dict[str, object],
    execution_name: str,
    java_output_dir: Path,
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

    raw_log_path = raw_dir / f"{execution_name}.log"
    base_row = {
        "run_id": manifest["run_id"],
        "run_number": manifest["run_number"],
        "started_at": manifest["started_at"],
        "profile_id": manifest["profile"]["id"],
        "profile_name": manifest["profile"]["name"],
        "host_os": HOST_OS,
        "host_arch": HOST_ARCH,
        "implementation": entry.implementation_id,
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
    base_row.update(implementation_metadata(entry.implementation_id))

    try:
        command = _command_for_entry(entry, runtime, case_file, java_output_dir)
    except Exception as error:
        raw_log_path.write_text(f"[command-error]\n{error}\n", encoding="utf-8")
        row = dict(base_row)
        row["error_message"] = str(error)
        _append_result_and_event(
            row=row,
            entry=entry,
            execution_name=execution_name,
            manifest=manifest,
            results=results,
            events=events,
            stream=stream,
            step_index=step_index,
            step_total=step_total,
        )
        return

    _event(
        stream,
        events,
        event_type="case",
        phase="launch",
        run_id=manifest["run_id"],
        profile_id=manifest["profile"]["id"],
        implementation=entry.implementation_id,
        case_id=stringify(case_values["case_id"]),
        repeat_index=stringify(case_values["repeat_index"]),
        warmup=stringify(case_values["warmup"]),
        step_index=step_index,
        step_total=step_total,
        command=" ".join(command),
        message=f"Launching {execution_name}",
    )

    try:
        process = _spawn_pinned(command, case_values=case_values)
        stdout, stderr = process.communicate()
        returncode = process.returncode
        raw_log_text = f"[command]\n{' '.join(command)}\n\n[stdout]\n{stdout}\n[stderr]\n{stderr}"
        raw_log_path.write_text(raw_log_text, encoding="utf-8")
    except Exception as error:
        raw_log_path.write_text(f"[command]\n{' '.join(command)}\n\n[start-error]\n{error}\n", encoding="utf-8")
        row = dict(base_row)
        row["error_message"] = str(error)
        _append_result_and_event(
            row=row,
            entry=entry,
            execution_name=execution_name,
            manifest=manifest,
            results=results,
            events=events,
            stream=stream,
            step_index=step_index,
            step_total=step_total,
        )
        return

    row = dict(base_row)
    if returncode == 0:
        try:
            payload = json.loads(stdout.strip())
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
        row["error_message"] = stderr.strip() or stdout.strip() or f"Exit code {returncode}"

    _append_result_and_event(
        row=row,
        entry=entry,
        execution_name=execution_name,
        manifest=manifest,
        results=results,
        events=events,
        stream=stream,
        step_index=step_index,
        step_total=step_total,
    )


def _append_result_and_event(
    *,
    row: dict[str, str],
    entry: ImplementationEntry,
    execution_name: str,
    manifest: dict,
    results: list[dict[str, str]],
    events: list[dict[str, str]],
    stream: EventCallback | None,
    step_index: int,
    step_total: int,
) -> None:
    results.append({field: stringify(row.get(field, "")) for field in RESULT_FIELD_ORDER})
    _event(
        stream,
        events,
        event_type="case",
        phase="finished",
        run_id=manifest["run_id"],
        profile_id=manifest["profile"]["id"],
        implementation=entry.implementation_id,
        case_id=row.get("case_id", ""),
        repeat_index=row.get("repeat_index", ""),
        warmup=row.get("warmup", ""),
        status=row["status"],
        metric=row.get("ns_per_iteration") or row.get("legacy_cycles_per_iteration", ""),
        metric_kind="ns/iter" if row.get("ns_per_iteration") else ("cycles/iter" if row.get("legacy_cycles_per_iteration") else ""),
        elapsed_ns=row.get("elapsed_ns", ""),
        timer_kind=row.get("timer_kind", ""),
        step_index=step_index,
        step_total=step_total,
        message=f"Finished {execution_name}",
    )
