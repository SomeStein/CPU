from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .common import (
    BENCHMARK_C_DIR,
    BENCHMARK_CPP_DIR,
    BENCHMARK_GO_DIR,
    BENCHMARK_JAVA_SRC_DIR,
    BENCHMARK_NODE_DIR,
    BENCHMARK_PERL_DIR,
    BENCHMARK_PYTHON_DIR,
    BENCHMARK_RUBY_DIR,
    BENCHMARK_RUST_DIR,
    HOST_BIN_DIR,
    HOST_OS,
    implementation_metadata,
)


@dataclass(frozen=True)
class ImplementationEntry:
    implementation_id: str
    language: str
    variant: str
    runtime_kind: str
    delivery_kind: str
    host_support: tuple[str, ...]
    default_profile_scope: str
    description: str
    runner_kind: str
    source_path: Path | None = None
    binary_stem: str | None = None
    java_class: str | None = None
    compiler_kind: str | None = None

    @property
    def binary_path(self) -> Path | None:
        if not self.binary_stem:
            return None
        suffix = ".exe" if HOST_OS == "windows" else ""
        return HOST_BIN_DIR / f"{self.binary_stem}{suffix}"


def _entry(
    implementation_id: str,
    *,
    language: str,
    variant: str,
    runtime_kind: str,
    delivery_kind: str,
    default_profile_scope: str,
    description: str,
    runner_kind: str,
    source_path: Path | None = None,
    binary_stem: str | None = None,
    java_class: str | None = None,
    compiler_kind: str | None = None,
) -> ImplementationEntry:
    return ImplementationEntry(
        implementation_id=implementation_id,
        language=language,
        variant=variant,
        runtime_kind=runtime_kind,
        delivery_kind=delivery_kind,
        host_support=("windows-x64", "macos-arm64"),
        default_profile_scope=default_profile_scope,
        description=description,
        runner_kind=runner_kind,
        source_path=source_path,
        binary_stem=binary_stem,
        java_class=java_class,
        compiler_kind=compiler_kind,
    )


IMPLEMENTATIONS: dict[str, ImplementationEntry] = {
    "c": _entry(
        "c",
        language="c",
        variant="default",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="default",
        description="Portable C worker with host-native timing adapters and process-wide scheduling hints.",
        runner_kind="native-binary",
        source_path=BENCHMARK_C_DIR / "c.c",
        binary_stem="c",
        compiler_kind="c",
    ),
    "python": _entry(
        "python",
        language="python",
        variant="default",
        runtime_kind="python",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Python worker with specialized hot paths for common chain counts.",
        runner_kind="python-script",
        source_path=BENCHMARK_PYTHON_DIR / "python.py",
    ),
    "node": _entry(
        "node",
        language="node",
        variant="default",
        runtime_kind="node",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Node.js worker with specialized fast paths for common chain counts.",
        runner_kind="node-script",
        source_path=BENCHMARK_NODE_DIR / "node.mjs",
    ),
    "java": _entry(
        "java",
        language="java",
        variant="default",
        runtime_kind="java",
        delivery_kind="java_class",
        default_profile_scope="default",
        description="Java worker with specialized hot paths for common chain counts.",
        runner_kind="java-class",
        java_class="bench.JavaWorker",
    ),
    "ruby": _entry(
        "ruby",
        language="ruby",
        variant="default",
        runtime_kind="ruby",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Ruby worker with flattened hot loops and chain-count specializations.",
        runner_kind="ruby-script",
        source_path=BENCHMARK_RUBY_DIR / "ruby.rb",
    ),
    "perl": _entry(
        "perl",
        language="perl",
        variant="default",
        runtime_kind="perl",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Perl worker with flattened state layout and specialized fast paths.",
        runner_kind="perl-script",
        source_path=BENCHMARK_PERL_DIR / "perl.pl",
    ),
    "cpp": _entry(
        "cpp",
        language="cpp",
        variant="default",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="default",
        description="C++ worker with specialized chain-count kernels and tight loops.",
        runner_kind="native-binary",
        source_path=BENCHMARK_CPP_DIR / "cpp.cpp",
        binary_stem="cpp",
        compiler_kind="cpp",
    ),
    "go": _entry(
        "go",
        language="go",
        variant="default",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="default",
        description="Go worker with specialized 4-chain and 8-chain kernels.",
        runner_kind="native-binary",
        source_path=BENCHMARK_GO_DIR / "go.go",
        binary_stem="go",
        compiler_kind="go",
    ),
    "rust": _entry(
        "rust",
        language="rust",
        variant="default",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="default",
        description="Rust benchmark with flattened state arrays and chain-count specializations.",
        runner_kind="native-binary",
        source_path=BENCHMARK_RUST_DIR / "rust.rs",
        binary_stem="rust",
        compiler_kind="rust",
    ),
}


def implementation_entry(implementation_id: str) -> ImplementationEntry:
    return IMPLEMENTATIONS[implementation_id]


def implementation_catalog_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for entry in IMPLEMENTATIONS.values():
        metadata = implementation_metadata(entry.implementation_id)
        rows.append(
            {
                "implementation_id": entry.implementation_id,
                "language": entry.language,
                "variant": entry.variant,
                "runtime_kind": entry.runtime_kind,
                "delivery_kind": entry.delivery_kind,
                "host_support": ",".join(entry.host_support),
                "default_profile_scope": entry.default_profile_scope,
                "description": entry.description,
                "display_name": metadata.get("display_name", entry.language.title()),
                "group": "native" if entry.runner_kind == "native-binary" else ("managed" if entry.runtime_kind in {"java", "node"} else "script"),
                "color": metadata.get("color", ""),
            }
        )
    return rows
