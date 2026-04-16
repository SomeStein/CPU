from __future__ import annotations

import copy
import json
import re
from pathlib import Path

from .catalog import IMPLEMENTATIONS
from .common import CUSTOM_TESTRUNS_DIR, TESTRUNS_DIR, read_json, write_json

PROFILE_ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_-]*$")
VALID_PRIORITY_MODES = {"high", "unchanged"}
VALID_AFFINITY_MODES = {"single_core", "unchanged"}
VALID_TIMER_MODES = {"monotonic_ns"}


def _profile_paths_in(directory: Path) -> list[Path]:
    if not directory.exists():
        return []
    return sorted(directory.glob("*.testrun.json"))


def builtin_profile_paths() -> list[Path]:
    return [path for path in _profile_paths_in(TESTRUNS_DIR) if path.parent == TESTRUNS_DIR]


def custom_profile_paths() -> list[Path]:
    return _profile_paths_in(CUSTOM_TESTRUNS_DIR)


def profile_location(profile_id: str) -> tuple[str, Path]:
    custom_path = CUSTOM_TESTRUNS_DIR / f"{profile_id}.testrun.json"
    if custom_path.exists():
        return "custom", custom_path
    builtin_path = TESTRUNS_DIR / f"{profile_id}.testrun.json"
    if builtin_path.exists():
        return "builtin", builtin_path
    raise FileNotFoundError(f"Unknown profile '{profile_id}'.")


def load_profile(profile_id: str) -> dict:
    _, path = profile_location(profile_id)
    return read_json(path)


def load_profile_path(path: Path) -> dict:
    return read_json(path)


def profile_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for source, paths in (("builtin", builtin_profile_paths()), ("custom", custom_profile_paths())):
        for path in paths:
            payload = read_json(path)
            defaults = payload.get("defaults", {})
            rows.append(
                {
                    "profile_id": payload.get("id", path.stem),
                    "name": payload.get("name", ""),
                    "source": source,
                    "editable": "true" if source == "custom" else "false",
                    "path": str(path.relative_to(TESTRUNS_DIR.parent)).replace("\\", "/"),
                    "implementations": ",".join(payload.get("implementations", [])),
                    "cases": str(len(payload.get("matrix", []))),
                    "warmups": str(defaults.get("warmups", "")),
                    "repeats": str(defaults.get("repeats", "")),
                }
            )
    rows.sort(key=lambda row: (row["source"] != "builtin", row["profile_id"]))
    return rows


def profile_detail_rows(profile_id: str) -> list[dict[str, str]]:
    source, path = profile_location(profile_id)
    payload = read_json(path)
    defaults = payload.get("defaults", {})
    rows: list[dict[str, str]] = []
    for index, case in enumerate(payload.get("matrix", []), start=1):
        rows.append(
            {
                "profile_id": payload.get("id", ""),
                "profile_name": payload.get("name", ""),
                "source": source,
                "editable": "true" if source == "custom" else "false",
                "case_index": str(index),
                "case_id": str(case.get("case_id", "")),
                "iterations": str(case.get("iterations", "")),
                "parallel_chains": str(case.get("parallel_chains", "")),
                "warmups": str(defaults.get("warmups", "")),
                "repeats": str(defaults.get("repeats", "")),
                "priority_mode": str(defaults.get("priority_mode", "")),
                "affinity_mode": str(defaults.get("affinity_mode", "")),
                "timer_mode": str(defaults.get("timer_mode", "")),
                "case_priority_mode": str(case.get("priority_mode", "")),
                "case_affinity_mode": str(case.get("affinity_mode", "")),
                "case_timer_mode": str(case.get("timer_mode", "")),
                "implementations": ",".join(payload.get("implementations", [])),
                "overrides_json": json.dumps(case.get("overrides", {}), sort_keys=True),
            }
        )
    return rows


def profile_override_rows(profile_id: str) -> list[dict[str, str]]:
    payload = load_profile(profile_id)
    rows: list[dict[str, str]] = []
    for case in payload.get("matrix", []):
        overrides = case.get("overrides", {})
        for implementation_id, override in overrides.items():
            rows.append(
                {
                    "profile_id": payload.get("id", ""),
                    "case_id": str(case.get("case_id", "")),
                    "implementation": implementation_id,
                    "iterations": str(override.get("iterations", "")),
                    "parallel_chains": str(override.get("parallel_chains", "")),
                    "priority_mode": str(override.get("priority_mode", "")),
                    "affinity_mode": str(override.get("affinity_mode", "")),
                    "timer_mode": str(override.get("timer_mode", "")),
                }
            )
    return rows


def validate_profile(payload: dict, *, target_path: Path | None = None) -> list[str]:
    errors: list[str] = []
    profile_id = str(payload.get("id", "")).strip()
    name = str(payload.get("name", "")).strip()
    implementations = payload.get("implementations", [])
    defaults = payload.get("defaults", {})
    matrix = payload.get("matrix", [])

    if payload.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    if not PROFILE_ID_PATTERN.match(profile_id):
        errors.append("id must match ^[a-z0-9][a-z0-9_-]*$")
    if not name:
        errors.append("name must not be empty")
    if not isinstance(implementations, list) or not implementations:
        errors.append("implementations must contain at least one entry")
    else:
        unknown = [implementation for implementation in implementations if implementation not in IMPLEMENTATIONS]
        if unknown:
            errors.append("unknown implementations: " + ", ".join(sorted(unknown)))
    if not isinstance(matrix, list) or not matrix:
        errors.append("matrix must contain at least one case")

    for field in ("warmups", "repeats"):
        value = defaults.get(field)
        if not _is_positive_int(value):
            errors.append(f"defaults.{field} must be a positive integer")
    if defaults.get("priority_mode") not in VALID_PRIORITY_MODES:
        errors.append("defaults.priority_mode must be one of: high, unchanged")
    if defaults.get("affinity_mode") not in VALID_AFFINITY_MODES:
        errors.append("defaults.affinity_mode must be one of: single_core, unchanged")
    if defaults.get("timer_mode") not in VALID_TIMER_MODES:
        errors.append("defaults.timer_mode must be monotonic_ns")

    seen_case_ids: set[str] = set()
    for index, case in enumerate(matrix, start=1):
        prefix = f"matrix[{index}]"
        case_id = str(case.get("case_id", "")).strip()
        if not case_id:
            errors.append(f"{prefix}.case_id must not be empty")
        elif case_id in seen_case_ids:
            errors.append(f"{prefix}.case_id must be unique")
        else:
            seen_case_ids.add(case_id)
        for field in ("iterations", "parallel_chains"):
            if not _is_positive_int(case.get(field)):
                errors.append(f"{prefix}.{field} must be a positive integer")
        if "priority_mode" in case and case.get("priority_mode") not in VALID_PRIORITY_MODES:
            errors.append(f"{prefix}.priority_mode must be one of: high, unchanged")
        if "affinity_mode" in case and case.get("affinity_mode") not in VALID_AFFINITY_MODES:
            errors.append(f"{prefix}.affinity_mode must be one of: single_core, unchanged")
        if "timer_mode" in case and case.get("timer_mode") not in VALID_TIMER_MODES:
            errors.append(f"{prefix}.timer_mode must be monotonic_ns")
        overrides = case.get("overrides", {})
        if not isinstance(overrides, dict):
            errors.append(f"{prefix}.overrides must be an object when present")
            continue
        for implementation_id, override in overrides.items():
            if implementation_id not in implementations:
                errors.append(f"{prefix}.overrides.{implementation_id} targets an unselected implementation")
                continue
            if not isinstance(override, dict):
                errors.append(f"{prefix}.overrides.{implementation_id} must be an object")
                continue
            for field in ("iterations", "parallel_chains"):
                if field in override and not _is_positive_int(override.get(field)):
                    errors.append(f"{prefix}.overrides.{implementation_id}.{field} must be a positive integer")
            if "priority_mode" in override and override.get("priority_mode") not in VALID_PRIORITY_MODES:
                errors.append(f"{prefix}.overrides.{implementation_id}.priority_mode must be one of: high, unchanged")
            if "affinity_mode" in override and override.get("affinity_mode") not in VALID_AFFINITY_MODES:
                errors.append(f"{prefix}.overrides.{implementation_id}.affinity_mode must be one of: single_core, unchanged")
            if "timer_mode" in override and override.get("timer_mode") not in VALID_TIMER_MODES:
                errors.append(f"{prefix}.overrides.{implementation_id}.timer_mode must be monotonic_ns")

    if profile_id:
        existing_ids = {row["profile_id"]: Path(TESTRUNS_DIR.parent, row["path"]) for row in profile_rows()}
        existing = existing_ids.get(profile_id)
        if existing is not None and (target_path is None or existing.resolve() != target_path.resolve()):
            errors.append(f"id '{profile_id}' is already used by another profile")
    return errors


def save_custom_profile(payload: dict) -> Path:
    CUSTOM_TESTRUNS_DIR.mkdir(parents=True, exist_ok=True)
    target_path = CUSTOM_TESTRUNS_DIR / f"{payload.get('id', '')}.testrun.json"
    errors = validate_profile(payload, target_path=target_path)
    if errors:
        raise ValueError("\n".join(errors))
    write_json(target_path, payload)
    return target_path


def duplicate_to_custom(profile_id: str, new_profile_id: str, new_name: str | None = None) -> Path:
    payload = copy.deepcopy(load_profile(profile_id))
    payload["id"] = new_profile_id
    if new_name:
        payload["name"] = new_name
    return save_custom_profile(payload)


def delete_custom_profile(profile_id: str) -> Path:
    source, path = profile_location(profile_id)
    if source != "custom":
        raise ValueError(f"Profile '{profile_id}' is not a custom profile.")
    if not path.exists():
        raise FileNotFoundError(f"Profile '{profile_id}' does not exist.")
    path.unlink()
    return path


def validation_rows(payload: dict, *, target_path: Path | None = None) -> list[dict[str, str]]:
    errors = validate_profile(payload, target_path=target_path)
    if not errors:
        return [{"status": "ok", "message": "Profile is valid."}]
    return [{"status": "error", "message": error} for error in errors]


def _is_positive_int(value: object) -> bool:
    try:
        return int(value) > 0
    except (TypeError, ValueError):
        return False
