import csv
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path

from benchmark_common import CSV_FIELD_ORDER, FIELD_ORDER, ROOT_DIR, RUNS_DIR, parse_key_value_text

RUN_DIR_PATTERN = re.compile(r"^run(?P<number>\d+)_(?P<stamp>\d{2}_\d{2}_\d{2}_\d{2}_\d{2})$")
IMPLEMENTATION_ORDER = ["c_native", "python_sloppy", "python_optimized"]


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


def build_batch_name(run_number: int, timestamp: datetime) -> str:
    return f"run{run_number:02d}_{timestamp:%H_%M_%d_%m_%y}"


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELD_ORDER)
        writer.writeheader()
        writer.writerows(rows)


def append_index(path: Path, rows: list[dict]) -> None:
    write_header = not path.exists()
    with path.open("a", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELD_ORDER)
        if write_header:
            writer.writeheader()
        writer.writerows(rows)


def make_row(batch_name: str, run_number: int, timestamp: datetime, log_path: Path, exit_code: int, parsed: dict) -> dict:
    row = {
        "batch_name": batch_name,
        "run_number": str(run_number),
        "timestamp": timestamp.isoformat(timespec="seconds"),
        "log_file": str(log_path.relative_to(ROOT_DIR)).replace("\\", "/"),
        "exit_code": str(exit_code),
    }
    for field in FIELD_ORDER:
        row[field] = parsed.get(field, "")
    return row


def run_one(command: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        capture_output=True,
        text=True,
        check=False,
        env=os.environ.copy(),
    )


def main() -> int:
    RUNS_DIR.mkdir(exist_ok=True)

    c_binary = ROOT_DIR / "tsc_benchmark.exe"
    if not c_binary.exists():
        raise FileNotFoundError(f"Missing C benchmark binary: {c_binary}")

    timestamp = datetime.now()
    run_number = next_run_number()
    batch_name = build_batch_name(run_number, timestamp)
    batch_dir = RUNS_DIR / batch_name
    batch_dir.mkdir(parents=True, exist_ok=False)

    commands = [
        ("c_native", [str(c_binary)], batch_dir / "c_native.txt"),
        ("python_sloppy", [sys.executable, str(ROOT_DIR / "scripts" / "python_sloppy.py")], batch_dir / "python_sloppy.txt"),
        ("python_optimized", [sys.executable, str(ROOT_DIR / "scripts" / "python_optimized.py")], batch_dir / "python_optimized.txt"),
    ]

    rows = []
    failures = []

    for expected_name, command, log_path in commands:
        completed = run_one(command, ROOT_DIR)
        output = completed.stdout
        if completed.stderr:
            if output and not output.endswith("\n"):
                output += "\n"
            output += "[stderr]\n"
            output += completed.stderr
        log_path.write_text(output, encoding="utf-8")

        parsed = parse_key_value_text(completed.stdout)
        if parsed:
            parsed["implementation"] = parsed.get("implementation", expected_name)
        else:
            parsed = {"implementation": expected_name}

        row = make_row(batch_name, run_number, timestamp, log_path, completed.returncode, parsed)
        rows.append(row)

        if completed.returncode != 0:
            failures.append(f"{expected_name} exited with {completed.returncode}")
        elif parsed.get("implementation") != expected_name:
            failures.append(f"{expected_name} reported implementation={parsed.get('implementation')}")

    rows.sort(key=lambda row: IMPLEMENTATION_ORDER.index(row["implementation"]))
    write_csv(batch_dir / "summary.csv", rows)
    append_index(RUNS_DIR / "index.csv", rows)

    print(f"batch_name = {batch_name}")
    print(f"batch_dir = {batch_dir}")
    print(f"summary_csv = {batch_dir / 'summary.csv'}")
    print(f"global_index = {RUNS_DIR / 'index.csv'}")

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
