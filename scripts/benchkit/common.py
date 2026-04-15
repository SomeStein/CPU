from __future__ import annotations

import csv
import json
import platform
import re
import sys
from datetime import datetime
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
SCRIPTS_DIR = ROOT_DIR / "scripts"
RUNS_DIR = ROOT_DIR / "runs"
TESTRUNS_DIR = ROOT_DIR / "testruns"
BUILD_DIR = ROOT_DIR / "build"
TOOLS_DIR = ROOT_DIR / "tools"

RUN_DIR_PATTERN = re.compile(r"^run(?P<number>\d+)_(?P<stamp>\d{2}_\d{2}_\d{2}_\d{2}_\d{2})$")

IMPLEMENTATION_METADATA = {
    "c_native": {"language": "c", "variant": "native"},
    "python_sloppy": {"language": "python", "variant": "sloppy"},
    "python_optimized": {"language": "python", "variant": "optimized"},
    "node_sloppy": {"language": "node", "variant": "sloppy"},
    "node_optimized": {"language": "node", "variant": "optimized"},
    "java_sloppy": {"language": "java", "variant": "sloppy"},
    "java_optimized": {"language": "java", "variant": "optimized"},
}

RESULT_FIELD_ORDER = [
    "run_id",
    "run_number",
    "started_at",
    "profile_id",
    "profile_name",
    "host_os",
    "host_arch",
    "implementation",
    "language",
    "variant",
    "case_id",
    "warmup",
    "repeat_index",
    "iterations",
    "parallel_chains",
    "loop_trip_count",
    "remainder",
    "timer_kind",
    "elapsed_ns",
    "ns_per_iteration",
    "ns_per_add",
    "legacy_cycles",
    "legacy_cycles_per_iteration",
    "legacy_cycles_per_add",
    "result_checksum",
    "requested_priority_mode",
    "requested_affinity_mode",
    "applied_priority_mode",
    "applied_affinity_mode",
    "scheduler_notes",
    "pid",
    "tid",
    "runtime_name",
    "runtime_source",
    "status",
    "log_file",
    "raw_file",
    "error_message",
    "platform_extras_json",
]


def normalize_arch(value: str) -> str:
    lowered = value.lower()
    if lowered in {"amd64", "x86_64", "x64"}:
        return "x64"
    if lowered in {"arm64", "aarch64"}:
        return "arm64"
    return lowered


def host_os() -> str:
    if sys.platform == "win32":
        return "windows"
    if sys.platform == "darwin":
        return "macos"
    return sys.platform


HOST_OS = host_os()
HOST_ARCH = normalize_arch(platform.machine())
HOST_KEY = f"{HOST_OS}-{HOST_ARCH}"
SUPPORTED_HOSTS = {"windows-x64", "macos-arm64"}


def ensure_supported_host() -> None:
    if HOST_KEY not in SUPPORTED_HOSTS:
        supported = ", ".join(sorted(SUPPORTED_HOSTS))
        raise RuntimeError(f"Unsupported host '{HOST_KEY}'. Supported hosts: {supported}.")


def next_run_number() -> int:
    highest = 0
    if not RUNS_DIR.exists():
        return 1
    for entry in RUNS_DIR.iterdir():
        if not entry.is_dir():
            continue
        match = RUN_DIR_PATTERN.match(entry.name)
        if match:
            highest = max(highest, int(match.group("number")))
    return highest + 1


def build_run_id(run_number: int, timestamp: datetime) -> str:
    return f"run{run_number:02d}_{timestamp:%H_%M_%d_%m_%y}"


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: object) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_key_value_file(path: Path) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        parsed[key.strip()] = value.strip()
    return parsed


def write_key_value_file(path: Path, values: dict[str, object]) -> None:
    lines = [f"{key}={value}" for key, value in values.items()]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def read_csv_rows(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv_rows(path: Path, fieldnames: list[str], rows: list[dict[str, object]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: stringify(row.get(field, "")) for field in fieldnames})


def append_csv_rows(path: Path, fieldnames: list[str], rows: list[dict[str, object]]) -> None:
    existing = read_csv_rows(path)
    combined = existing + [{field: stringify(row.get(field, "")) for field in fieldnames} for row in rows]
    write_csv_rows(path, fieldnames, combined)


def stringify(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def parse_key_value_text(text: str) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for line in text.splitlines():
        if " = " not in line:
            continue
        key, value = line.split(" = ", 1)
        parsed[key.strip()] = value.strip()
    return parsed


def implementation_metadata(implementation: str) -> dict[str, str]:
    return IMPLEMENTATION_METADATA.get(implementation, {"language": "", "variant": ""})


def metric_value(row: dict[str, str]) -> tuple[str, str]:
    ns_value = row.get("ns_per_iteration", "")
    if ns_value:
        return ns_value, "ns/iter"
    legacy_value = row.get("legacy_cycles_per_iteration", "")
    if legacy_value:
        return legacy_value, "cycles/iter"
    return "", ""


def parse_int(value: str | None, default: int = 0) -> int:
    if value in {None, ""}:
        return default
    return int(value, 10)


def parse_float(value: str | None, default: float = 0.0) -> float:
    if value in {None, ""}:
        return default
    return float(value)

