from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

from benchkit.build import build_assets
from benchkit.history import run_summaries
from benchkit.runtimes import resolve_tool


def command_analyze(_: argparse.Namespace) -> int:
    assets = build_assets()
    java = resolve_tool("java")
    python = resolve_tool("python")
    api_script = Path(__file__).resolve().parent / "controller_api.py"
    command = [
        str(java.path),
        "-cp",
        assets["java_output_dir"],
        "cpubench.ControllerApp",
        str(Path(__file__).resolve().parents[1]),
        str(python.path),
        str(api_script),
    ]
    return subprocess.run(command, check=False).returncode


def command_summary(_: argparse.Namespace) -> int:
    for row in run_summaries()[:10]:
        metric = row.get("best_metric_value", "")
        unit = row.get("best_metric_kind", "")
        print(
            f"{row['run_id']} | {row['profile_id']} | {row['status']} | "
            f"{row['result_count']} results | {metric} {unit}".strip()
        )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Open the benchmark launch deck or inspect stored run summaries.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    analyze_cmd = subparsers.add_parser("analyze")
    analyze_cmd.set_defaults(handler=command_analyze)

    summary_cmd = subparsers.add_parser("summary")
    summary_cmd.set_defaults(handler=command_summary)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main())
