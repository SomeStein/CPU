from __future__ import annotations

from benchkit.history import load_run_results, run_summaries


def main() -> int:
    summaries = run_summaries()
    if not summaries:
        print("No runs available.")
        return 1

    latest = summaries[0]
    print(f"Latest run: {latest['run_id']} ({latest['profile_id']})")
    print()
    print("Recent runs:")
    for row in summaries[:10]:
        print(
            f"{row['run_id']} | {row['profile_id']} | {row['status']} | "
            f"{row['result_count']} results | {row['best_metric_value']} {row['best_metric_kind']}".strip()
        )

    print()
    print("Latest run results:")
    for row in load_run_results(latest["run_id"]):
        metric = row.get("ns_per_iteration") or row.get("legacy_cycles_per_iteration", "")
        unit = "ns/iter" if row.get("ns_per_iteration") else "cycles/iter"
        print(
            f"{row.get('implementation', '')} | {row.get('case_id', '')} | "
            f"warmup={row.get('warmup', '')} | repeat={row.get('repeat_index', '')} | "
            f"{metric} {unit}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
