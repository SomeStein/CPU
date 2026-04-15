from __future__ import annotations

import ctypes
import json
import os
import sys
import threading
import time
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
SCRIPTS_DIR = ROOT_DIR / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from benchkit.common import HOST_ARCH, HOST_OS, implementation_metadata, read_key_value_file

MASK64 = (1 << 64) - 1
SEED_PAIRS = [
    (1, 1),
    (3, 5),
    (8, 13),
    (21, 34),
    (55, 89),
    (144, 233),
    (377, 610),
    (987, 1597),
]


def mask_u64(value: int) -> int:
    return value & MASK64


def monotonic_ns() -> int:
    return time.perf_counter_ns()


def extend_seed_pairs(count: int) -> list[tuple[int, int]]:
    pairs = list(SEED_PAIRS)
    while len(pairs) < count:
        left = mask_u64(pairs[-2][0] + pairs[-1][1])
        right = mask_u64(left + pairs[-1][0])
        pairs.append((left, right))
    return pairs[:count]


def load_case_file(path: Path) -> dict[str, object]:
    raw = read_key_value_file(path)
    return {
        "run_id": raw["run_id"],
        "profile_id": raw["profile_id"],
        "implementation": raw["implementation"],
        "case_id": raw["case_id"],
        "iterations": int(raw["iterations"], 10),
        "parallel_chains": int(raw["parallel_chains"], 10),
        "priority_mode": raw["priority_mode"],
        "affinity_mode": raw["affinity_mode"],
        "timer_mode": raw["timer_mode"],
        "warmup": raw["warmup"] == "true",
        "repeat_index": int(raw["repeat_index"], 10),
    }


def load_case_from_argv() -> dict[str, object]:
    if len(sys.argv) != 3 or sys.argv[1] != "--case-file":
        raise SystemExit("Usage: <script> --case-file <path>")
    return load_case_file(Path(sys.argv[2]))


if sys.platform == "win32":
    DWORD = ctypes.c_uint32
    BOOL = ctypes.c_int
    HANDLE = ctypes.c_void_p
    DWORD_PTR = ctypes.c_size_t

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.GetCurrentProcessId.restype = DWORD
    kernel32.GetCurrentThreadId.restype = DWORD
    kernel32.GetCurrentThread.restype = HANDLE
    kernel32.SetThreadPriority.argtypes = [HANDLE, ctypes.c_int]
    kernel32.SetThreadPriority.restype = BOOL
    kernel32.SetThreadAffinityMask.argtypes = [HANDLE, DWORD_PTR]
    kernel32.SetThreadAffinityMask.restype = DWORD_PTR


def _windows_context(priority_mode: str, affinity_mode: str) -> dict[str, object]:
    thread = kernel32.GetCurrentThread()
    applied_priority = "unchanged"
    applied_affinity = "unchanged"
    notes: list[str] = []

    if priority_mode == "high":
        if kernel32.SetThreadPriority(thread, 2):
            applied_priority = "high"
        else:
            applied_priority = "failed"
            notes.append("SetThreadPriority failed")

    if affinity_mode == "single_core":
        previous = kernel32.SetThreadAffinityMask(thread, 1)
        if previous:
            applied_affinity = "single_core"
        else:
            applied_affinity = "failed"
            notes.append("SetThreadAffinityMask failed")

    return {
        "pid": int(kernel32.GetCurrentProcessId()),
        "tid": int(kernel32.GetCurrentThreadId()),
        "requested_priority_mode": priority_mode,
        "requested_affinity_mode": affinity_mode,
        "applied_priority_mode": applied_priority,
        "applied_affinity_mode": applied_affinity,
        "scheduler_notes": "; ".join(notes),
    }


def _posix_context(priority_mode: str, affinity_mode: str) -> dict[str, object]:
    applied_priority = "unchanged"
    notes: list[str] = []

    if priority_mode == "high":
        try:
            os.nice(-5)
            applied_priority = "high"
        except (AttributeError, PermissionError, OSError):
            applied_priority = "unsupported"
            notes.append("priority elevation unavailable")

    applied_affinity = "unsupported" if affinity_mode == "single_core" else "unchanged"
    if affinity_mode == "single_core":
        notes.append("affinity control unavailable")

    return {
        "pid": os.getpid(),
        "tid": threading.get_native_id(),
        "requested_priority_mode": priority_mode,
        "requested_affinity_mode": affinity_mode,
        "applied_priority_mode": applied_priority,
        "applied_affinity_mode": applied_affinity,
        "scheduler_notes": "; ".join(notes),
    }


def prepare_process_context(priority_mode: str, affinity_mode: str) -> dict[str, object]:
    if sys.platform == "win32":
        return _windows_context(priority_mode, affinity_mode)
    return _posix_context(priority_mode, affinity_mode)


def build_result(
    *,
    implementation: str,
    case: dict[str, object],
    context: dict[str, object],
    elapsed_ns: int,
    loop_trip_count: int,
    remainder: int,
    result_checksum: int,
    timer_kind: str,
    platform_extras: dict[str, object] | None = None,
) -> dict[str, object]:
    iterations = int(case["iterations"])
    total_adds = iterations * 2 if iterations else 0
    metadata = implementation_metadata(implementation)
    return {
        "schema_version": 1,
        "implementation": implementation,
        "language": metadata["language"],
        "variant": metadata["variant"],
        "case_id": case["case_id"],
        "warmup": bool(case["warmup"]),
        "repeat_index": int(case["repeat_index"]),
        "iterations": iterations,
        "parallel_chains": int(case["parallel_chains"]),
        "loop_trip_count": loop_trip_count,
        "remainder": remainder,
        "timer_kind": timer_kind,
        "elapsed_ns": elapsed_ns,
        "ns_per_iteration": (elapsed_ns / iterations) if iterations else 0.0,
        "ns_per_add": (elapsed_ns / total_adds) if total_adds else 0.0,
        "result_checksum": str(mask_u64(result_checksum)),
        "host_os": HOST_OS,
        "host_arch": HOST_ARCH,
        "pid": context["pid"],
        "tid": context["tid"],
        "requested_priority_mode": context["requested_priority_mode"],
        "requested_affinity_mode": context["requested_affinity_mode"],
        "applied_priority_mode": context["applied_priority_mode"],
        "applied_affinity_mode": context["applied_affinity_mode"],
        "scheduler_notes": context["scheduler_notes"],
        "runtime_name": Path(sys.executable).name,
        "platform_extras": platform_extras or {},
    }


def emit_result_json(payload: dict[str, object]) -> None:
    sys.stdout.write(json.dumps(payload, sort_keys=True))
    sys.stdout.write("\n")
    sys.stdout.flush()
