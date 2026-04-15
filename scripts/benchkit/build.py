from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

from .catalog import IMPLEMENTATIONS, ImplementationEntry
from .common import (
    BENCHMARK_JAVA_SRC_DIR,
    BUILD_DIR,
    GUI_SRC_DIR,
    HOST_OS,
    ROOT_DIR,
    SCRIPTS_DIR,
    ensure_supported_host,
)
from .runtimes import resolve_tool


def _run(command: list[str], *, quiet: bool = False) -> None:
    subprocess.run(command, cwd=ROOT_DIR, check=True, capture_output=quiet, text=quiet)


def _macos_sdk_flags() -> list[str]:
    if HOST_OS != "macos":
        return []
    xcrun = shutil.which("xcrun")
    if not xcrun:
        return []
    try:
        sdk_path = subprocess.check_output([xcrun, "--show-sdk-path"], cwd=ROOT_DIR, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return []
    return ["-isysroot", sdk_path] if sdk_path else []


def _find_compiler(candidates: list[str]) -> str | None:
    for candidate in candidates:
        path = shutil.which(candidate)
        if path:
            return path
    return None


def _find_c_compiler() -> str | None:
    return _find_compiler(["clang", "cc", "gcc"] if HOST_OS != "windows" else ["clang", "gcc", "cl"])


def _find_cpp_compiler() -> str | None:
    return _find_compiler(["clang++", "c++", "g++"] if HOST_OS != "windows" else ["clang++", "g++", "cl"])


def build_java() -> Path:
    javac = resolve_tool("javac")
    source_roots = [GUI_SRC_DIR, BENCHMARK_JAVA_SRC_DIR]
    sources = sorted(str(path) for root in source_roots if root.exists() for path in root.rglob("*.java"))
    if not sources:
        raise FileNotFoundError("No Java sources found for the controller or Java benchmark implementations.")

    output_dir = BUILD_DIR / "java"
    output_dir.mkdir(parents=True, exist_ok=True)
    _run([str(javac.path), "-d", str(output_dir), *sources])
    return output_dir


def build_compiled_entry(entry: ImplementationEntry, *, required: bool) -> Path:
    if entry.binary_path is None:
        raise ValueError(f"{entry.implementation_id} does not produce a host binary.")
    entry.binary_path.parent.mkdir(parents=True, exist_ok=True)

    if entry.compiler_kind == "c":
        compiler = _find_c_compiler()
        if compiler is None:
            if entry.binary_path.exists():
                return entry.binary_path
            raise FileNotFoundError("No C compiler found and no prebuilt c_native binary is available.")
        compiler_name = Path(compiler).name.lower()
        if compiler_name in {"cl.exe", "cl"}:
            _run([compiler, "/O2", "/W3", f"/Fe:{entry.binary_path}", str(entry.source_path)])
        else:
            _run(
                [
                    compiler,
                    "-O3",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-D_POSIX_C_SOURCE=200809L",
                    *_macos_sdk_flags(),
                    "-o",
                    str(entry.binary_path),
                    str(entry.source_path),
                ],
                quiet=not required,
            )
        return entry.binary_path

    if entry.compiler_kind == "cpp":
        compiler = _find_cpp_compiler()
        if compiler is None:
            if entry.binary_path.exists() or not required:
                return entry.binary_path
            raise FileNotFoundError(f"No C++ compiler found for {entry.implementation_id}.")
        compiler_name = Path(compiler).name.lower()
        if compiler_name in {"cl.exe", "cl"}:
            _run([compiler, "/O2", "/EHsc", f"/Fe:{entry.binary_path}", str(entry.source_path)], quiet=not required)
        else:
            _run(
                [
                    compiler,
                    "-O3",
                    "-std=c++17",
                    "-Wall",
                    "-Wextra",
                    *_macos_sdk_flags(),
                    "-o",
                    str(entry.binary_path),
                    str(entry.source_path),
                ],
                quiet=not required,
            )
        return entry.binary_path

    if entry.compiler_kind == "go":
        go = resolve_tool("go", required=False)
        if go.source == "missing":
            if entry.binary_path.exists() or not required:
                return entry.binary_path
            raise FileNotFoundError(f"No Go toolchain found for {entry.implementation_id}.")
        _run([str(go.path), "build", "-o", str(entry.binary_path), str(entry.source_path)], quiet=not required)
        return entry.binary_path

    if entry.compiler_kind == "rust":
        rustc = resolve_tool("rustc", required=False)
        if rustc.source == "missing":
            if entry.binary_path.exists() or not required:
                return entry.binary_path
            raise FileNotFoundError(f"No Rust toolchain found for {entry.implementation_id}.")
        _run([str(rustc.path), "-O", "-o", str(entry.binary_path), str(entry.source_path)], quiet=not required)
        return entry.binary_path

    raise ValueError(f"Unsupported compiler kind: {entry.compiler_kind}")


def build_compiled_benchmarks() -> dict[str, str]:
    built: dict[str, str] = {}
    for entry in IMPLEMENTATIONS.values():
        if entry.compiler_kind is None:
            continue
        required = entry.implementation_id == "c_native"
        try:
            path = build_compiled_entry(entry, required=required)
            built[entry.implementation_id] = str(path)
        except (FileNotFoundError, subprocess.CalledProcessError):
            if required:
                raise
    return built


def build_assets() -> dict[str, str]:
    ensure_supported_host()
    java_dir = build_java()
    binaries = build_compiled_benchmarks()
    return {
        "java_output_dir": str(java_dir),
        "native_binary": binaries.get("c_native", ""),
        "scripts_dir": str(SCRIPTS_DIR),
    }
