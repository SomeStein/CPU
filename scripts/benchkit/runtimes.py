from __future__ import annotations

import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

from .common import HOST_KEY, TOOLS_DIR


@dataclass(frozen=True)
class ResolvedTool:
    name: str
    path: Path
    source: str


def _host_runtime_dir() -> Path:
    return TOOLS_DIR / "runtime" / HOST_KEY


def _tool_candidates(name: str) -> list[Path]:
    runtime_dir = _host_runtime_dir()
    candidates: list[Path] = []
    if name == "python":
        candidates.extend(
            [
                runtime_dir / "python" / "python.exe",
                runtime_dir / "python" / "bin" / "python3",
                runtime_dir / "python" / "bin" / "python",
            ]
        )
    elif name == "node":
        candidates.extend(
            [
                runtime_dir / "node" / "node.exe",
                runtime_dir / "node" / "bin" / "node",
            ]
        )
    elif name == "java":
        candidates.extend(
            [
                runtime_dir / "java" / "bin" / "java.exe",
                runtime_dir / "java" / "bin" / "java",
                runtime_dir / "jdk" / "bin" / "java.exe",
                runtime_dir / "jdk" / "bin" / "java",
            ]
        )
    elif name == "javac":
        candidates.extend(
            [
                runtime_dir / "java" / "bin" / "javac.exe",
                runtime_dir / "java" / "bin" / "javac",
                runtime_dir / "jdk" / "bin" / "javac.exe",
                runtime_dir / "jdk" / "bin" / "javac",
            ]
        )
    return candidates


def resolve_tool(name: str, *, required: bool = True) -> ResolvedTool:
    for candidate in _tool_candidates(name):
        if candidate.exists():
            return ResolvedTool(name=name, path=candidate, source="bundled")

    if name == "python":
        return ResolvedTool(name=name, path=Path(sys.executable), source="system")

    which_value = shutil.which(name)
    if which_value:
        return ResolvedTool(name=name, path=Path(which_value), source="system")

    if not required:
        return ResolvedTool(name=name, path=Path(name), source="missing")

    runtime_dir = _host_runtime_dir()
    raise FileNotFoundError(
        f"Could not resolve '{name}'. Add a bundled runtime under {runtime_dir} or install it on PATH."
    )

