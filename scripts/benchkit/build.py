from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

from .common import BUILD_DIR, HOST_OS, ROOT_DIR, SCRIPTS_DIR, TOOLS_DIR, ensure_supported_host
from .runtimes import resolve_tool


def _run(command: list[str]) -> None:
    subprocess.run(command, cwd=ROOT_DIR, check=True)


def _find_c_compiler() -> str | None:
    candidates = ["clang", "cc", "gcc"] if HOST_OS != "windows" else ["clang", "gcc", "cl"]
    for candidate in candidates:
        path = shutil.which(candidate)
        if path:
            return path
    return None


def build_java() -> Path:
    javac = resolve_tool("javac")
    source_roots = [ROOT_DIR / "controller" / "src", ROOT_DIR / "java-src"]
    sources = sorted(str(path) for root in source_roots if root.exists() for path in root.rglob("*.java"))
    if not sources:
        raise FileNotFoundError("No Java sources found for the controller or Java benchmark implementations.")

    output_dir = BUILD_DIR / "java"
    output_dir.mkdir(parents=True, exist_ok=True)
    _run([str(javac.path), "-d", str(output_dir), *sources])
    return output_dir


def build_native() -> Path:
    compiler = _find_c_compiler()
    output_dir = TOOLS_DIR / "bin" / f"{HOST_OS}-{'arm64' if HOST_OS == 'macos' else 'x64'}"
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / ("c_native.exe" if HOST_OS == "windows" else "c_native")
    source_path = ROOT_DIR / "tsc_benchmark.c"

    if compiler is None:
        if output_path.exists():
            return output_path
        raise FileNotFoundError("No C compiler found and no prebuilt native benchmark binary is available.")

    compiler_name = Path(compiler).name.lower()
    if compiler_name == "cl.exe" or compiler_name == "cl":
        _run([compiler, "/O2", "/W3", f"/Fe:{output_path}", str(source_path)])
    else:
        _run(
            [
                compiler,
                "-O3",
                "-std=c11",
                "-Wall",
                "-Wextra",
                "-D_POSIX_C_SOURCE=200809L",
                "-o",
                str(output_path),
                str(source_path),
            ]
        )
    return output_path


def build_assets() -> dict[str, str]:
    ensure_supported_host()
    java_dir = build_java()
    native_binary = build_native()
    return {
        "java_output_dir": str(java_dir),
        "native_binary": str(native_binary),
        "scripts_dir": str(SCRIPTS_DIR),
    }

