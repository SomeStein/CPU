from __future__ import annotations

import os
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

from .common import HOST_KEY, TOOLS_DIR, TOOLS_TOOLCHAINS_DIR


@dataclass(frozen=True)
class ResolvedTool:
    name: str
    path: Path
    source: str


def _host_runtime_dir() -> Path:
    return TOOLS_DIR / "runtime" / HOST_KEY


def _host_toolchain_dir() -> Path:
    return TOOLS_TOOLCHAINS_DIR / HOST_KEY


def _allow_system_tools(explicit: bool | None) -> bool:
    if explicit is not None:
        return explicit
    value = os.environ.get("CPU_LAB_ALLOW_SYSTEM_TOOLS", "")
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _tool_candidates(name: str) -> list[Path]:
    runtime_dir = _host_runtime_dir()
    toolchain_dir = _host_toolchain_dir()
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
    elif name == "ruby":
        candidates.extend(
            [
                runtime_dir / "ruby" / "ruby.exe",
                runtime_dir / "ruby" / "bin" / "ruby.exe",
                runtime_dir / "ruby" / "bin" / "ruby",
            ]
        )
    elif name == "perl":
        candidates.extend(
            [
                runtime_dir / "perl" / "perl.exe",
                runtime_dir / "perl" / "bin" / "perl.exe",
                runtime_dir / "perl" / "bin" / "perl",
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
    elif name == "jar":
        candidates.extend(
            [
                runtime_dir / "java" / "bin" / "jar.exe",
                runtime_dir / "java" / "bin" / "jar",
                runtime_dir / "jdk" / "bin" / "jar.exe",
                runtime_dir / "jdk" / "bin" / "jar",
            ]
        )
    elif name == "jpackage":
        candidates.extend(
            [
                runtime_dir / "java" / "bin" / "jpackage.exe",
                runtime_dir / "java" / "bin" / "jpackage",
                runtime_dir / "jdk" / "bin" / "jpackage.exe",
                runtime_dir / "jdk" / "bin" / "jpackage",
            ]
        )
    elif name == "go":
        candidates.extend(
            [
                runtime_dir / "go" / "bin" / "go.exe",
                runtime_dir / "go" / "bin" / "go",
            ]
        )
    elif name == "rustc":
        candidates.extend(
            [
                toolchain_dir / "rust" / "cargo" / "bin" / "rustc.exe",
                toolchain_dir / "rust" / "cargo" / "bin" / "rustc",
                runtime_dir / "rust" / "bin" / "rustc.exe",
                runtime_dir / "rust" / "bin" / "rustc",
            ]
        )
    elif name == "cargo":
        candidates.extend(
            [
                toolchain_dir / "rust" / "cargo" / "bin" / "cargo.exe",
                toolchain_dir / "rust" / "cargo" / "bin" / "cargo",
            ]
        )
    elif name == "clang":
        candidates.extend(
            [
                toolchain_dir / "llvm" / "bin" / "clang.exe",
                toolchain_dir / "llvm" / "bin" / "clang",
            ]
        )
    elif name == "clang++":
        candidates.extend(
            [
                toolchain_dir / "llvm" / "bin" / "clang++.exe",
                toolchain_dir / "llvm" / "bin" / "clang++",
            ]
        )
    return candidates


def resolve_tool(name: str, *, required: bool = True, allow_system: bool | None = None) -> ResolvedTool:
    for candidate in _tool_candidates(name):
        if candidate.exists():
            source = "bundled_toolchain" if _host_toolchain_dir() in candidate.parents else "bundled_runtime"
            return ResolvedTool(name=name, path=candidate, source=source)

    if not _allow_system_tools(allow_system):
        if not required:
            return ResolvedTool(name=name, path=Path(name), source="missing")
        runtime_dir = _host_runtime_dir()
        toolchain_dir = _host_toolchain_dir()
        raise FileNotFoundError(
            f"Could not resolve '{name}'. Add a bundled runtime under {runtime_dir} or a bundled toolchain under {toolchain_dir}."
        )

    if name == "python":
        return ResolvedTool(name=name, path=Path(sys.executable), source="system")

    which_value = shutil.which(name)
    if which_value:
        return ResolvedTool(name=name, path=Path(which_value), source="system")

    if not required:
        return ResolvedTool(name=name, path=Path(name), source="missing")

    runtime_dir = _host_runtime_dir()
    toolchain_dir = _host_toolchain_dir()
    raise FileNotFoundError(
        f"Could not resolve '{name}'. Add a bundled runtime under {runtime_dir}, a bundled toolchain under {toolchain_dir}, or enable system fallback."
    )
