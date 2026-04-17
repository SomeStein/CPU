from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from benchkit.build import build_assets
from benchkit.common import RESULT_FIELD_ORDER
from benchkit.catalog import implementation_catalog_rows
from benchkit.history import delete_run_artifact, load_index_rows, load_raw_text, load_run_events, load_run_manifest, load_run_results, run_summaries
from benchkit.profiles import (
    delete_custom_profile,
    duplicate_to_custom,
    load_profile,
    profile_detail_rows,
    profile_override_rows,
    profile_rows,
    save_custom_profile,
    validation_rows,
)
from benchkit.suite import run_profile, run_profile_path


def print_tsv(rows: list[dict[str, str]]) -> None:
    if not rows:
        return
    try:
        headers = list(rows[0].keys())
        sys.stdout.write("\t".join(headers) + "\n")
        for row in rows:
            sys.stdout.write("\t".join(row.get(header, "").replace("\t", " ").replace("\n", " ") for header in headers))
            sys.stdout.write("\n")
        sys.stdout.flush()
    except BrokenPipeError:
        try:
            sys.stdout.close()
        except OSError:
            pass


def command_profiles(_: argparse.Namespace) -> int:
    print_tsv(profile_rows())
    return 0


def command_profile(args: argparse.Namespace) -> int:
    print_tsv(profile_detail_rows(args.profile_id))
    return 0


def command_profile_overrides(args: argparse.Namespace) -> int:
    print_tsv(profile_override_rows(args.profile_id))
    return 0


def command_profile_json_id(args: argparse.Namespace) -> int:
    profile = load_profile(args.profile_id)
    sys.stdout.write(json.dumps(profile, indent=2, sort_keys=True))
    sys.stdout.write("\n")
    return 0


def command_implementation_catalog(_: argparse.Namespace) -> int:
    print_tsv(implementation_catalog_rows())
    return 0


def command_validate_profile_file(args: argparse.Namespace) -> int:
    payload = json.loads(Path(args.path).read_text(encoding="utf-8"))
    print_tsv(validation_rows(payload))
    return 0


def command_save_profile_file(args: argparse.Namespace) -> int:
    payload = json.loads(Path(args.path).read_text(encoding="utf-8"))
    saved_path = save_custom_profile(payload)
    rows = [row for row in profile_rows() if row.get("profile_id") == payload.get("id")]
    if rows:
        print_tsv(rows)
    else:
        print_tsv(
            [
                {
                    "profile_id": str(payload.get("id", "")),
                    "name": str(payload.get("name", "")),
                    "source": "custom",
                    "editable": "true",
                    "path": str(saved_path),
                    "implementations": ",".join(payload.get("implementations", [])),
                    "cases": str(len(payload.get("matrix", []))),
                    "warmups": str(payload.get("defaults", {}).get("warmups", "")),
                    "repeats": str(payload.get("defaults", {}).get("repeats", "")),
                }
            ]
        )
    return 0


def command_duplicate_profile(args: argparse.Namespace) -> int:
    saved_path = duplicate_to_custom(args.profile_id, args.new_profile_id, args.name)
    rows = [row for row in profile_rows() if row.get("profile_id") == args.new_profile_id]
    if rows:
        print_tsv(rows)
    else:
        print_tsv(
            [
                {
                    "profile_id": args.new_profile_id,
                    "name": args.name or args.new_profile_id,
                    "source": "custom",
                    "editable": "true",
                    "path": str(saved_path),
                    "implementations": "",
                    "cases": "",
                    "warmups": "",
                    "repeats": "",
                }
            ]
        )
    return 0


def command_runs(_: argparse.Namespace) -> int:
    print_tsv(run_summaries())
    return 0


def command_global_results(_: argparse.Namespace) -> int:
    print_tsv(load_index_rows())
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


def command_delete_run(args: argparse.Namespace) -> int:
    delete_run_artifact(args.run_id)
    print_tsv([{"status": "ok", "run_id": args.run_id}])
    return 0


def command_delete_custom_profile(args: argparse.Namespace) -> int:
    deleted = delete_custom_profile(args.profile_id)
    print_tsv([{"status": "ok", "profile_id": args.profile_id, "path": str(deleted)}])
    return 0


def _run_with_events(result_runner, profile_label: str) -> int:
    headers_printed = False
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
        *[field for field in RESULT_FIELD_ORDER if field not in {
            "run_id",
            "profile_id",
            "implementation",
            "case_id",
            "repeat_index",
            "warmup",
            "status",
            "elapsed_ns",
            "timer_kind",
        }],
    ]

    def emit(event: dict[str, str]) -> None:
        nonlocal headers_printed
        if not headers_printed:
            sys.stdout.write("\t".join(headers) + "\n")
            headers_printed = True
        sys.stdout.write("\t".join(event.get(header, "").replace("\t", " ").replace("\n", " ") for header in headers))
        sys.stdout.write("\n")
        sys.stdout.flush()

    try:
        assets = build_assets()
        result = result_runner(Path(assets["java_output_dir"]), emit)
    except Exception as error:
        emit(
            {
                "event_type": "run",
                "phase": "error",
                "run_id": "",
                "profile_id": profile_label,
                "status": "failed",
                "step_index": "0",
                "step_total": "0",
                "message": str(error),
            }
        )
        return 1
    if not headers_printed:
        emit({"event_type": "run", "phase": "completed", "run_id": result["run_id"], "profile_id": profile_label, "status": result["status"]})
    return 0 if result["status"] == "success" else 1


def command_run_profile(args: argparse.Namespace) -> int:
    return _run_with_events(
        lambda java_output_dir, emit: run_profile(args.profile_id, java_output_dir=java_output_dir, stream=emit),
        args.profile_id,
    )


def command_run_profile_file(args: argparse.Namespace) -> int:
    profile_path = Path(args.path)
    return _run_with_events(
        lambda java_output_dir, emit: run_profile_path(profile_path, java_output_dir=java_output_dir, stream=emit),
        profile_path.stem,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Controller backend for the benchmark GUI.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    profiles = subparsers.add_parser("profiles")
    profiles.set_defaults(handler=command_profiles)

    profile = subparsers.add_parser("profile")
    profile.add_argument("profile_id")
    profile.set_defaults(handler=command_profile)

    profile_overrides = subparsers.add_parser("profile-overrides")
    profile_overrides.add_argument("profile_id")
    profile_overrides.set_defaults(handler=command_profile_overrides)

    profile_json = subparsers.add_parser("profile-json")
    profile_json.add_argument("profile_id")
    profile_json.set_defaults(handler=command_profile_json_id)

    implementation_catalog = subparsers.add_parser("implementation-catalog")
    implementation_catalog.set_defaults(handler=command_implementation_catalog)

    validate_profile = subparsers.add_parser("validate-profile-file")
    validate_profile.add_argument("path")
    validate_profile.set_defaults(handler=command_validate_profile_file)

    save_profile = subparsers.add_parser("save-profile-file")
    save_profile.add_argument("path")
    save_profile.set_defaults(handler=command_save_profile_file)

    duplicate_profile = subparsers.add_parser("duplicate-profile")
    duplicate_profile.add_argument("profile_id")
    duplicate_profile.add_argument("new_profile_id")
    duplicate_profile.add_argument("--name", default=None)
    duplicate_profile.set_defaults(handler=command_duplicate_profile)

    runs = subparsers.add_parser("runs")
    runs.set_defaults(handler=command_runs)

    global_results = subparsers.add_parser("global-results")
    global_results.set_defaults(handler=command_global_results)

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

    delete_run = subparsers.add_parser("delete-run")
    delete_run.add_argument("run_id")
    delete_run.set_defaults(handler=command_delete_run)

    delete_profile = subparsers.add_parser("delete-custom-profile")
    delete_profile.add_argument("profile_id")
    delete_profile.set_defaults(handler=command_delete_custom_profile)

    run_cmd = subparsers.add_parser("run-profile")
    run_cmd.add_argument("profile_id")
    run_cmd.set_defaults(handler=command_run_profile)

    run_profile_file = subparsers.add_parser("run-profile-file")
    run_profile_file.add_argument("path")
    run_profile_file.set_defaults(handler=command_run_profile_file)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main())
