import ctypes
import os
import sys
from pathlib import Path

THREAD_PRIORITY_HIGHEST = 2
DEFAULT_AFFINITY_MASK = 1 << 0
MASK64 = (1 << 64) - 1
ROOT_DIR = Path(__file__).resolve().parent.parent
RUNS_DIR = ROOT_DIR / "runs"
FIELD_ORDER = [
    "implementation",
    "pid",
    "tid",
    "iterations",
    "parallel_chains",
    "loop_trip_count",
    "remainder",
    "requested_affinity_mask",
    "previous_affinity_mask",
    "priority_set",
    "thread_priority",
    "cpu_before",
    "cpu_after",
    "timer_kind",
    "counter_start",
    "counter_end",
    "result",
    "cycles",
    "cycles/iteration",
    "cycles/add",
]
CSV_FIELD_ORDER = [
    "batch_name",
    "run_number",
    "timestamp",
    "log_file",
    "exit_code",
    *FIELD_ORDER,
]
HEX_FIELDS = {"requested_affinity_mask", "previous_affinity_mask"}
FLOAT_FIELDS = {"cycles/iteration", "cycles/add"}

DWORD = ctypes.c_uint32
BOOL = ctypes.c_int
HANDLE = ctypes.c_void_p
DWORD_PTR = ctypes.c_size_t
ULONG64 = ctypes.c_ulonglong

kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
kernel32.GetCurrentThread.restype = HANDLE
kernel32.GetCurrentProcessId.restype = DWORD
kernel32.GetCurrentThreadId.restype = DWORD
kernel32.SetThreadAffinityMask.argtypes = [HANDLE, DWORD_PTR]
kernel32.SetThreadAffinityMask.restype = DWORD_PTR
kernel32.SetThreadPriority.argtypes = [HANDLE, ctypes.c_int]
kernel32.SetThreadPriority.restype = BOOL
kernel32.GetThreadPriority.argtypes = [HANDLE]
kernel32.GetThreadPriority.restype = ctypes.c_int
kernel32.GetCurrentProcessorNumber.restype = DWORD
kernel32.QueryThreadCycleTime.argtypes = [HANDLE, ctypes.POINTER(ULONG64)]
kernel32.QueryThreadCycleTime.restype = BOOL


def get_positive_int_from_env(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    try:
        parsed = int(raw, 10)
    except ValueError:
        return default
    if parsed <= 0:
        return default
    return parsed


def prepare_thread_context(requested_mask: int = DEFAULT_AFFINITY_MASK) -> dict:
    thread = kernel32.GetCurrentThread()
    previous_mask = kernel32.SetThreadAffinityMask(thread, requested_mask)
    priority_ok = kernel32.SetThreadPriority(thread, THREAD_PRIORITY_HIGHEST)
    return {
        "thread": thread,
        "pid": int(kernel32.GetCurrentProcessId()),
        "tid": int(kernel32.GetCurrentThreadId()),
        "requested_affinity_mask": int(requested_mask),
        "previous_affinity_mask": int(previous_mask),
        "priority_set": 1 if priority_ok else 0,
        "thread_priority": int(kernel32.GetThreadPriority(thread)),
    }


def get_current_processor_number() -> int:
    return int(kernel32.GetCurrentProcessorNumber())


def query_thread_cycle_time(thread: HANDLE) -> int:
    value = ULONG64()
    ok = kernel32.QueryThreadCycleTime(thread, ctypes.byref(value))
    if not ok:
        raise ctypes.WinError(ctypes.get_last_error())
    return int(value.value)


def mask_u64(value: int) -> int:
    return value & MASK64


def stringify_field(key: str, value) -> str:
    if key in HEX_FIELDS:
        return f"0x{int(value):x}"
    if key in FLOAT_FIELDS:
        return f"{float(value):.6f}"
    return str(value)


def emit_record(record: dict) -> None:
    for key in FIELD_ORDER:
        sys.stdout.write(f"{key} = {stringify_field(key, record[key])}\n")
    sys.stdout.flush()


def parse_key_value_text(text: str) -> dict:
    parsed = {}
    for line in text.splitlines():
        if " = " not in line:
            continue
        key, value = line.split(" = ", 1)
        parsed[key.strip()] = value.strip()
    return parsed
