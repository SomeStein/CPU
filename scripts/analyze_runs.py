import csv
from collections import defaultdict
from pathlib import Path

from benchmark_common import RUNS_DIR, parse_key_value_text

RUN_ORDER = ["c_native", "python_sloppy", "python_optimized"]


def parse_float(value: str) -> float:
    return float(value)


def parse_run_number(batch_name: str) -> int:
    head = batch_name.split("_", 1)[0]
    return int(head.replace("run", ""))


def load_index_rows(index_path: Path) -> list[dict]:
    with index_path.open("r", newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        return list(reader)


def load_latest_batch_records(rows: list[dict]) -> tuple[str, list[dict]]:
    latest_batch = max(rows, key=lambda row: parse_run_number(row["batch_name"]))["batch_name"]
    batch_dir = RUNS_DIR / latest_batch
    records = []
    for log_path in sorted(batch_dir.glob("*.txt")):
        parsed = parse_key_value_text(log_path.read_text(encoding="utf-8"))
        if parsed:
            parsed["log_file"] = str(log_path.relative_to(RUNS_DIR)).replace("\\", "/")
            records.append(parsed)
    records.sort(key=lambda record: RUN_ORDER.index(record["implementation"]))
    return latest_batch, records


def summarize_history(rows: list[dict]) -> list[dict]:
    grouped = defaultdict(list)
    for row in rows:
        if row.get("exit_code") != "0":
            continue
        grouped[row["implementation"]].append(row)

    summary_rows = []
    for implementation in RUN_ORDER:
        values = grouped.get(implementation, [])
        if not values:
            continue
        cycles_per_iteration = [parse_float(row["cycles/iteration"]) for row in values]
        last_batch = max(values, key=lambda row: parse_run_number(row["batch_name"]))["batch_name"]
        summary_rows.append(
            {
                "implementation": implementation,
                "runs": str(len(values)),
                "best cycles/iteration": f"{min(cycles_per_iteration):.6f}",
                "avg cycles/iteration": f"{(sum(cycles_per_iteration) / len(cycles_per_iteration)):.6f}",
                "worst cycles/iteration": f"{max(cycles_per_iteration):.6f}",
                "last batch": last_batch,
            }
        )
    return summary_rows


def render_table(title: str, columns: list[str], rows: list[dict]) -> str:
    widths = {column: len(column) for column in columns}
    for row in rows:
        for column in columns:
            widths[column] = max(widths[column], len(str(row.get(column, ""))))

    separator = "-+-".join("-" * widths[column] for column in columns)
    lines = [title, " | ".join(column.ljust(widths[column]) for column in columns), separator]
    for row in rows:
        lines.append(" | ".join(str(row.get(column, "")).ljust(widths[column]) for column in columns))
    return "\n".join(lines)


def main() -> int:
    index_path = RUNS_DIR / "index.csv"
    if not index_path.exists():
        print("No runs available. Execute the run target first.")
        return 1

    rows = load_index_rows(index_path)
    if not rows:
        print("No runs available. Execute the run target first.")
        return 1

    latest_batch, latest_records = load_latest_batch_records(rows)
    history_rows = summarize_history(rows)

    latest_columns = [
        "implementation",
        "timer_kind",
        "iterations",
        "parallel_chains",
        "cycles",
        "cycles/iteration",
        "cycles/add",
        "cpu_before",
        "cpu_after",
        "log_file",
    ]
    history_columns = [
        "implementation",
        "runs",
        "best cycles/iteration",
        "avg cycles/iteration",
        "worst cycles/iteration",
        "last batch",
    ]

    print(f"Latest batch: {latest_batch}")
    print()
    print(render_table("Latest Batch Comparison", latest_columns, latest_records))
    print()
    print(render_table("Historical Summary", history_columns, history_rows))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
