from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import tarfile
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from .common import BUILD_TMP_DIR, HOST_KEY, ROOT_DIR, TOOLS_INSTALLED_STATE_PATH, TOOLS_MANIFESTS_DIR, write_json


DOWNLOAD_CACHE_DIR = BUILD_TMP_DIR / "downloads"
TEMP_EXTRACT_DIR = BUILD_TMP_DIR / "bootstrap-extract"
SUPPORTED_ARCHIVE_TYPES = {"zip", "tar.gz", "tar.xz", "exe", "rustup-init"}


@dataclass(frozen=True)
class ToolManifest:
    tool_id: str
    host: str
    version: str
    url: str
    sha256: str
    archive_type: str
    destination: str
    executables: tuple[str, ...]
    license_files: tuple[str, ...]
    component_kind: str
    required_for_launch: bool
    required_for_build: bool
    archive_subdir: str = ""
    install_args: tuple[str, ...] = ()
    install_env: dict[str, str] | None = None
    install_enabled: bool = True
    install_hint: str = ""

    @property
    def destination_path(self) -> Path:
        return (ROOT_DIR / self.destination).resolve()

    @property
    def executable_paths(self) -> tuple[Path, ...]:
        return tuple(self.destination_path / executable for executable in self.executables)


def load_manifests(host: str = HOST_KEY) -> list[ToolManifest]:
    manifest_dir = TOOLS_MANIFESTS_DIR / host
    if not manifest_dir.exists():
        return []
    manifests: list[ToolManifest] = []
    for path in sorted(manifest_dir.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        manifests.append(
            ToolManifest(
                tool_id=str(payload["tool_id"]),
                host=str(payload["host"]),
                version=str(payload["version"]),
                url=str(payload.get("url", "")),
                sha256=str(payload.get("sha256", "")).strip().lower(),
                archive_type=str(payload["archive_type"]),
                destination=str(payload["destination"]),
                executables=tuple(payload.get("executables", [])),
                license_files=tuple(payload.get("license_files", [])),
                component_kind=str(payload.get("component_kind", "runtime")),
                required_for_launch=bool(payload.get("required_for_launch", False)),
                required_for_build=bool(payload.get("required_for_build", False)),
                archive_subdir=str(payload.get("archive_subdir", "")),
                install_args=tuple(payload.get("install_args", [])),
                install_env=dict(payload.get("install_env", {})),
                install_enabled=bool(payload.get("install_enabled", True)),
                install_hint=str(payload.get("install_hint", "")),
            )
        )
    return manifests


def manifest_by_tool_id(host: str = HOST_KEY) -> dict[str, ToolManifest]:
    return {manifest.tool_id: manifest for manifest in load_manifests(host)}


def readiness_rows(host: str = HOST_KEY) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for manifest in load_manifests(host):
        ready = is_manifest_ready(manifest)
        status = "ready" if ready else ("manual" if not manifest.install_enabled else "missing")
        message = "Bundled component is ready."
        if not ready:
            if manifest.install_enabled:
                message = "Bundled component is missing. Run prepare-host --download-missing to install it."
            elif manifest.install_hint:
                message = manifest.install_hint
            else:
                message = "Bundled component is missing and must be supplied manually for this host."
        rows.append(
            {
                "component_id": manifest.tool_id,
                "component_kind": manifest.component_kind,
                "host": manifest.host,
                "status": status,
                "version": manifest.version,
                "path": str(manifest.destination_path),
                "required_for_launch": "true" if manifest.required_for_launch else "false",
                "required_for_build": "true" if manifest.required_for_build else "false",
                "message": message,
            }
        )
    return rows


def is_manifest_ready(manifest: ToolManifest) -> bool:
    if not manifest.executables:
        return manifest.destination_path.exists()
    return all(path.exists() for path in manifest.executable_paths)


def prepare_host(*, download_missing: bool, host: str = HOST_KEY) -> list[dict[str, str]]:
    manifests = load_manifests(host)
    if download_missing:
        for manifest in manifests:
            if is_manifest_ready(manifest) or not manifest.install_enabled:
                continue
            install_manifest(manifest)
        write_installed_state(manifests)
    return readiness_rows(host)


def write_installed_state(manifests: list[ToolManifest], *, host: str = HOST_KEY) -> None:
    payload = {
        "schema_version": 1,
        "host": host,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "components": [
            {
                "tool_id": manifest.tool_id,
                "version": manifest.version,
                "status": "ready" if is_manifest_ready(manifest) else "missing",
                "path": str(manifest.destination_path),
            }
            for manifest in manifests
        ],
    }
    TOOLS_INSTALLED_STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    write_json(TOOLS_INSTALLED_STATE_PATH, payload)


def install_manifest(manifest: ToolManifest) -> None:
    if manifest.archive_type not in SUPPORTED_ARCHIVE_TYPES:
        raise ValueError(f"Unsupported archive type '{manifest.archive_type}' for {manifest.tool_id}.")
    _ensure_within_repo(manifest.destination_path)
    DOWNLOAD_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    TEMP_EXTRACT_DIR.mkdir(parents=True, exist_ok=True)
    if manifest.archive_type == "exe":
        _install_exe_manifest(manifest)
        return
    if manifest.archive_type == "rustup-init":
        _install_rustup_manifest(manifest)
        return

    archive_path = _download_archive(manifest)
    stage_dir = TEMP_EXTRACT_DIR / manifest.tool_id
    if stage_dir.exists():
        shutil.rmtree(stage_dir)
    stage_dir.mkdir(parents=True, exist_ok=True)
    _extract_archive(archive_path, stage_dir, manifest.archive_type)
    source_dir = stage_dir / manifest.archive_subdir if manifest.archive_subdir else stage_dir
    if not source_dir.exists():
        raise FileNotFoundError(f"Archive root '{manifest.archive_subdir}' not found for {manifest.tool_id}.")
    if manifest.destination_path.exists():
        shutil.rmtree(manifest.destination_path)
    manifest.destination_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(source_dir), str(manifest.destination_path))


def _download_archive(manifest: ToolManifest) -> Path:
    parsed = urllib.parse.urlparse(manifest.url)
    file_name = Path(parsed.path).name or f"{manifest.tool_id}-{manifest.version}"
    archive_path = DOWNLOAD_CACHE_DIR / file_name
    if archive_path.exists() and manifest.sha256:
        actual_sha = _sha256_file(archive_path)
        if actual_sha == manifest.sha256:
            return archive_path

    temp_archive_path = archive_path.with_name(f"{archive_path.name}.{manifest.tool_id}.download")
    if temp_archive_path.exists():
        temp_archive_path.unlink(missing_ok=True)
    with urllib.request.urlopen(manifest.url) as response, temp_archive_path.open("wb") as handle:
        shutil.copyfileobj(response, handle)
    if manifest.sha256:
        actual_sha = _sha256_file(temp_archive_path)
        if actual_sha != manifest.sha256:
            temp_archive_path.unlink(missing_ok=True)
            raise ValueError(f"SHA-256 mismatch for {manifest.tool_id}: expected {manifest.sha256}, got {actual_sha}.")
    try:
        if archive_path.exists():
            archive_path.unlink(missing_ok=True)
        shutil.move(str(temp_archive_path), str(archive_path))
        return archive_path
    except PermissionError:
        return temp_archive_path


def _extract_archive(archive_path: Path, destination: Path, archive_type: str) -> None:
    if archive_type == "zip":
        with zipfile.ZipFile(archive_path) as archive:
            archive.extractall(destination)
        return
    mode = "r:gz" if archive_type == "tar.gz" else "r:xz"
    with tarfile.open(archive_path, mode) as archive:
        archive.extractall(destination)


def _install_exe_manifest(manifest: ToolManifest) -> None:
    archive_path = _download_archive(manifest)
    if manifest.destination_path.exists():
        shutil.rmtree(manifest.destination_path)
    manifest.destination_path.mkdir(parents=True, exist_ok=True)
    args = [str(archive_path), *[_expand_install_token(token, manifest.destination_path) for token in manifest.install_args]]
    env = {**os.environ, **(manifest.install_env or {})}
    subprocess.run(args, check=True, cwd=ROOT_DIR, env=env)


def _install_rustup_manifest(manifest: ToolManifest) -> None:
    archive_path = _download_archive(manifest)
    destination = manifest.destination_path
    cargo_home = destination / "cargo"
    rustup_home = destination / "rustup"
    destination.mkdir(parents=True, exist_ok=True)
    env = {
        **os.environ,
        "CARGO_HOME": str(cargo_home),
        "RUSTUP_HOME": str(rustup_home),
        **(manifest.install_env or {}),
    }
    args = [str(archive_path), *[_expand_install_token(token, destination) for token in manifest.install_args]]
    subprocess.run(args, check=True, cwd=ROOT_DIR, env=env)


def _expand_install_token(token: str, destination: Path) -> str:
    return token.replace("{destination}", str(destination))


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def _ensure_within_repo(path: Path) -> None:
    try:
        path.resolve().relative_to(ROOT_DIR.resolve())
    except ValueError as error:
        raise ValueError(f"Refusing to write outside the repository: {path}") from error
