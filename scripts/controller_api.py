from __future__ import annotations

import argparse
import sys
from pathlib import Path

from benchkit.build import build_assets
from benchkit.history import load_raw_text, load_run_events, load_run_manifest, load_run_results, run_summaries
from benchkit.suite import list_profiles, load_profile, run_profile


def print_tsv(rows: list[dict[str, str]]) -> None:
    if not rows:
        return
    headers = list(rows[0].keys())
    sys.stdout.write("\t".join(headers) + "\n")
    for row in rows:
        sys.stdout.write("\t".join(row.get(header, "").replace("\t", " ").replace("\n", " ") for header in headers))
        sys.stdout.write("\n")
    sys.stdout.flush()


def command_profiles(_: argparse.Namespace) -> int:
    print_tsv(list_profiles())
    return 0


def command_profile(args: argparse.Namespace) -> int:
    profile = load_profile(args.profile_id)
    rows = []
    defaults = profile.get("defaults", {})
    for index, case in enumerate(profile.get("matrix", []), start=1):
        rows.append(
            {
                "profile_id": profile["id"],
                "profile_name": profile["name"],
                "case_index": str(index),
                "case_id": case.get("case_id", ""),
                "iterations": str(case.get("iterations", "")),
                "parallel_chains": str(case.get("parallel_chains", "")),
                "warmups": str(defaults.get("warmups", "")),
                "repeats": str(defaults.get("repeats", "")),
                "priority_mode": str(case.get("priority_mode", defaults.get("priority_mode", ""))),
                "affinity_mode": str(case.get("affinity_mode", defaults.get("affinity_mode", ""))),
                "timer_mode": str(case.get("timer_mode", defaults.get("timer_mode", ""))),
                "implementations": ",".join(profile.get("implementations", [])),
            }
        )
    print_tsv(rows)
    return 0


def command_runs(_: argparse.Namespace) -> int:
    print_tsv(run_summaries())
    return 0


def command_results(args: argparse.Namespace) -> int:
    print_tsv(load_run_results(args.run_id))
    return 0


def command_events(args: argparse.Namespace) -> int:
    print_tsv(load_run_events(args.run_id))
    return 0


def command_manifest(args: argparse.Namespace) -> int:
    manifest = load_run_manifest(args.run_id)
    if not manifest:
        return 1
    rows = [{"field": key, "value": str(value)} for key, value in manifest.items()]
    print_tsv(rows)
    return 0


def command_raw(args: argparse.Namespace) -> int:
    sys.stdout.write(load_raw_text(args.relative_path))
    return 0


def command_run_profile(args: argparse.Namespace) -> int:
    assets = build_assets()
    headers_printed = False

    def emit(event: dict[str, str]) -> None:
        nonlocal headers_printed
        headers = [
            "event_type",
            "phase",
            "run_id",
            "profile_id",
            "implementation",
            "case_id",
            "repeat_index",
            "warmup",
            "status",
            "metric",
            "metric_kind",
            "elapsed_ns",
            "timer_kind",
            "step_index",
            "step_total",
            "message",
        ]
        if not headers_printed:
            sys.stdout.write("\t".join(headers) + "\n")
            headers_printed = True
        sys.stdout.write("\t".join(event.get(header, "").replace("\t", " ").replace("\n", " ") for header in headers))
        sys.stdout.write("\n")
        sys.stdout.flush()

    result = run_profile(
        args.profile_id,
        java_output_dir=Path(assets["java_output_dir"]),
        native_binary=Path(assets["native_binary"]),
        stream=emit,
    )
    if not headers_printed:
        emit({"event_type": "run", "phase": "completed", "run_id": result["run_id"], "status": result["status"]})
    return 0 if result["status"] == "success" else 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Controller backend for the benchmark GUI.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    profiles = subparsers.add_parser("profiles")
    profiles.set_defaults(handler=command_profiles)

    profile = subparsers.add_parser("profile")
    profile.add_argument("profile_id")
    profile.set_defaults(handler=command_profile)

    runs = subparsers.add_parser("runs")
    runs.set_defaults(handler=command_runs)

    results = subparsers.add_parser("results")
    results.add_argument("run_id")
    results.set_defaults(handler=command_results)

    events = subparsers.add_parser("events")
    events.add_argument("run_id")
    events.set_defaults(handler=command_events)

    manifest = subparsers.add_parser("manifest")
    manifest.add_argument("run_id")
    manifest.set_defaults(handler=command_manifest)

    raw = subparsers.add_parser("raw")
    raw.add_argument("relative_path")
    raw.set_defaults(handler=command_raw)

    run_cmd = subparsers.add_parser("run-profile")
    run_cmd.add_argument("profile_id")
    run_cmd.set_defaults(handler=command_run_profile)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main())
