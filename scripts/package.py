from __future__ import annotations

import argparse
import shutil
import subprocess
from pathlib import Path

from benchkit.build import build_compiled_entry, build_java
from benchkit.catalog import IMPLEMENTATIONS
from benchkit.common import BUILD_DIR, HOST_KEY, HOST_OS, ROOT_DIR, TOOLS_DIR, ensure_supported_host
from benchkit.runtimes import resolve_tool


DIST_DIR = ROOT_DIR / "dist"
PACKAGE_INPUT_DIR = BUILD_DIR / "package-input"
CONTROLLER_STAGE_DIR = PACKAGE_INPUT_DIR / "controller"
GUI_JAR_PATH = PACKAGE_INPUT_DIR / "gui.jar"
JAVA_WORKER_JAR = TOOLS_DIR / "jars" / "java-worker.jar"
RUNTIME_CHECKS = [
    ("python", "python/python.exe or python/bin/python3"),
    ("node", "node/node.exe or node/bin/node"),
    ("ruby", "ruby/ruby.exe or ruby/bin/ruby"),
    ("perl", "perl/perl.exe or perl/bin/perl"),
    ("java", "java/bin/java(.exe)"),
]
CONTROLLER_CONTENT = [
    "AGENTS.md",
    "README.md",
    "benchmarks",
    "docs",
    "gui",
    "runs",
    "scripts",
    "testruns",
    "tools",
]


def _run(command: list[str], *, cwd: Path | None = None) -> None:
    subprocess.run(command, cwd=(cwd or ROOT_DIR), check=True)


def ensure_runtimes() -> None:
    runtime_root = TOOLS_DIR / "runtime" / HOST_KEY
    missing: list[str] = []
    for directory, description in RUNTIME_CHECKS:
        if not (runtime_root / directory).exists():
            missing.append(f"{directory}: expected {runtime_root / description}")
    if missing:
        raise FileNotFoundError(
            "Bundled runtimes are missing for packaging:\n"
            + "\n".join(f"- {entry}" for entry in missing)
            + "\n\nPopulate tools/runtime/README.md before building a bundle."
        )


def compile_natives() -> None:
    failures: list[str] = []
    for entry in IMPLEMENTATIONS.values():
        if entry.compiler_kind is None:
            continue
        try:
            build_compiled_entry(entry, required=True)
        except (FileNotFoundError, subprocess.CalledProcessError) as error:
            failures.append(f"{entry.implementation_id}: {error}")
    if failures:
        raise RuntimeError(
            "Unable to build packaged native benchmarks:\n"
            + "\n".join(f"- {failure}" for failure in failures)
        )


def compile_java() -> Path:
    java_output_dir = build_java()
    jar_tool = resolve_tool("jar")
    PACKAGE_INPUT_DIR.mkdir(parents=True, exist_ok=True)
    TOOLS_DIR.joinpath("jars").mkdir(parents=True, exist_ok=True)
    if GUI_JAR_PATH.exists():
        GUI_JAR_PATH.unlink()
    _run(
        [
            str(jar_tool.path),
            "--create",
            "--file",
            str(GUI_JAR_PATH),
            "--main-class",
            "cpubench.ControllerApp",
            "-C",
            str(java_output_dir),
            ".",
        ]
    )
    _run(
        [
            str(jar_tool.path),
            "--create",
            "--file",
            str(JAVA_WORKER_JAR),
            "--main-class",
            "bench.JavaWorker",
            "-C",
            str(java_output_dir),
            "bench",
        ]
    )
    return java_output_dir


def stage_controller_tree() -> None:
    if CONTROLLER_STAGE_DIR.exists():
        shutil.rmtree(CONTROLLER_STAGE_DIR)
    CONTROLLER_STAGE_DIR.mkdir(parents=True, exist_ok=True)
    for relative in CONTROLLER_CONTENT:
        source = ROOT_DIR / relative
        target = CONTROLLER_STAGE_DIR / relative
        if source.is_dir():
            shutil.copytree(source, target)
        elif source.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
    CONTROLLER_STAGE_DIR.joinpath("build", "tmp").mkdir(parents=True, exist_ok=True)


def package_type_from_host() -> str:
    return "dmg" if HOST_OS == "macos" else "msi"


def maybe_icon_arg() -> list[str]:
    resources_dir = ROOT_DIR / "gui" / "resources"
    icon_name = "app.icns" if HOST_OS == "macos" else "app.ico"
    icon_path = resources_dir / icon_name
    return ["--icon", str(icon_path)] if icon_path.exists() else []


def run_jpackage(package_type: str) -> None:
    jpackage = resolve_tool("jpackage")
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    command = [
        str(jpackage.path),
        "--type",
        package_type,
        "--dest",
        str(DIST_DIR),
        "--input",
        str(PACKAGE_INPUT_DIR),
        "--main-jar",
        GUI_JAR_PATH.name,
        "--name",
        "CPU Lab",
        "--java-options",
        "-Xmx512m",
    ]
    command.extend(maybe_icon_arg())
    if HOST_OS == "windows":
        command.extend(["--win-menu", "--win-shortcut"])
    _run(command)


def build_bundle(package_type: str) -> None:
    ensure_supported_host()
    ensure_runtimes()
    if PACKAGE_INPUT_DIR.exists():
        shutil.rmtree(PACKAGE_INPUT_DIR)
    PACKAGE_INPUT_DIR.mkdir(parents=True, exist_ok=True)
    compile_natives()
    compile_java()
    stage_controller_tree()
    run_jpackage("app-image")
    if package_type != "app-image":
        run_jpackage(package_type)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build a host-native CPU Lab bundle with jpackage.")
    parser.add_argument(
        "--type",
        default=package_type_from_host(),
        choices=["app-image", "dmg", "msi"],
        help="Installer or bundle type to build on the current host.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    build_bundle(args.type)
    print(f"Packaged CPU Lab for {HOST_KEY} into {DIST_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
