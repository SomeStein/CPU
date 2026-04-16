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
GUI_DIR = ROOT_DIR / "gui"
GUI_SRC_DIR = GUI_DIR / "src"
BENCHMARKS_DIR = ROOT_DIR / "benchmarks"
BENCHMARK_C_DIR = BENCHMARKS_DIR / "c"
BENCHMARK_CPP_DIR = BENCHMARKS_DIR / "cpp"
BENCHMARK_GO_DIR = BENCHMARKS_DIR / "go"
BENCHMARK_JAVA_DIR = BENCHMARKS_DIR / "java"
BENCHMARK_JAVA_SRC_DIR = BENCHMARK_JAVA_DIR / "src"
BENCHMARK_NODE_DIR = BENCHMARKS_DIR / "node"
BENCHMARK_PERL_DIR = BENCHMARKS_DIR / "perl"
BENCHMARK_PYTHON_DIR = BENCHMARKS_DIR / "python"
BENCHMARK_RUBY_DIR = BENCHMARKS_DIR / "ruby"
BENCHMARK_RUST_DIR = BENCHMARKS_DIR / "rust"
RUNS_DIR = ROOT_DIR / "runs"
TESTRUNS_DIR = ROOT_DIR / "testruns"
CUSTOM_TESTRUNS_DIR = TESTRUNS_DIR / "custom"
BUILD_DIR = ROOT_DIR / "build"
TOOLS_DIR = ROOT_DIR / "tools"
TOOLS_BIN_DIR = TOOLS_DIR / "bin"
BUILD_TMP_DIR = BUILD_DIR / "tmp"

ARTIFACT_URI_PREFIX = "artifact://"

RUN_DIR_PATTERN = re.compile(r"^run(?P<number>\d+)_(?P<stamp>\d{2}_\d{2}_\d{2}_\d{2}_\d{2})$")

IMPLEMENTATION_METADATA = {
    "c": {"language": "c", "variant": "default", "display_name": "C", "group": "native", "color": "#A8B9CC"},
    "cpp": {"language": "cpp", "variant": "default", "display_name": "C++", "group": "native", "color": "#659AD2"},
    "go": {"language": "go", "variant": "default", "display_name": "Go", "group": "native", "color": "#00ADD8"},
    "java": {"language": "java", "variant": "default", "display_name": "Java", "group": "managed", "color": "#EA2D2E"},
    "node": {"language": "node", "variant": "default", "display_name": "Node.js", "group": "managed", "color": "#5FA04E"},
    "perl": {"language": "perl", "variant": "default", "display_name": "Perl", "group": "script", "color": "#39457E"},
    "python": {"language": "python", "variant": "default", "display_name": "Python", "group": "script", "color": "#3776AB"},
    "ruby": {"language": "ruby", "variant": "default", "display_name": "Ruby", "group": "script", "color": "#CC342D"},
    "rust": {"language": "rust", "variant": "default", "display_name": "Rust", "group": "native", "color": "#DEA584"},
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
HOST_BIN_DIR = TOOLS_BIN_DIR / HOST_KEY


def ensure_supported_host() -> None:
    if HOST_KEY not in SUPPORTED_HOSTS:
        supported = ", ".join(sorted(SUPPORTED_HOSTS))
        raise RuntimeError(f"Unsupported host '{HOST_KEY}'. Supported hosts: {supported}.")


def next_run_number() -> int:
    highest = 0
    if not RUNS_DIR.exists():
        return 1
    for entry in RUNS_DIR.iterdir():
        candidate_name = ""
        if entry.is_dir():
            candidate_name = entry.name
        elif entry.is_file() and entry.suffix == ".json":
            candidate_name = entry.stem
        if not candidate_name:
            continue
        match = RUN_DIR_PATTERN.match(candidate_name)
        if match:
            highest = max(highest, int(match.group("number")))
    return highest + 1


def build_run_id(run_number: int, timestamp: datetime) -> str:
    return f"run{run_number:02d}_{timestamp:%H_%M_%d_%m_%y}"


def run_bundle_path(run_id: str) -> Path:
    return RUNS_DIR / f"{run_id}.json"


def temp_run_dir(run_id: str) -> Path:
    return BUILD_TMP_DIR / f"run-{run_id}"


def artifact_uri(run_id: str, artifact_key: str) -> str:
    return f"{ARTIFACT_URI_PREFIX}{run_id}/{artifact_key}"


def parse_artifact_uri(value: str) -> tuple[str, str] | None:
    if not value.startswith(ARTIFACT_URI_PREFIX):
        return None
    remainder = value[len(ARTIFACT_URI_PREFIX):]
    run_id, separator, artifact_key = remainder.partition("/")
    if not separator or not run_id or not artifact_key:
        return None
    return run_id, artifact_key


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
    return IMPLEMENTATION_METADATA.get(
        implementation,
        {"language": "", "variant": "", "display_name": "", "group": "", "color": ""},
    )


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
