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
    "c_native": _entry(
        "c_native",
        language="c",
        variant="native",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="default",
        description="Portable C baseline with host-native timing adapters.",
        runner_kind="native-binary",
        source_path=BENCHMARK_C_DIR / "c_native.c",
        binary_stem="c_native",
        compiler_kind="c",
    ),
    "python_sloppy": _entry(
        "python_sloppy",
        language="python",
        variant="sloppy",
        runtime_kind="python",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Straightforward Python loop structure with list-backed state objects.",
        runner_kind="python-script",
        source_path=BENCHMARK_PYTHON_DIR / "python_sloppy.py",
    ),
    "python_optimized": _entry(
        "python_optimized",
        language="python",
        variant="optimized",
        runtime_kind="python",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Specialized Python paths for common chain counts to reduce interpreter overhead.",
        runner_kind="python-script",
        source_path=BENCHMARK_PYTHON_DIR / "python_optimized.py",
    ),
    "node_sloppy": _entry(
        "node_sloppy",
        language="node",
        variant="sloppy",
        runtime_kind="node",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Node benchmark with generic array-of-pairs state management.",
        runner_kind="node-script",
        source_path=BENCHMARK_NODE_DIR / "node_sloppy.mjs",
    ),
    "node_optimized": _entry(
        "node_optimized",
        language="node",
        variant="optimized",
        runtime_kind="node",
        delivery_kind="repo_script",
        default_profile_scope="default",
        description="Node benchmark with specialized fast paths for 4 and 8 chains.",
        runner_kind="node-script",
        source_path=BENCHMARK_NODE_DIR / "node_optimized.mjs",
    ),
    "java_sloppy": _entry(
        "java_sloppy",
        language="java",
        variant="sloppy",
        runtime_kind="java",
        delivery_kind="java_class",
        default_profile_scope="default",
        description="Java benchmark using generic nested loops and shared state arrays.",
        runner_kind="java-class",
        java_class="bench.JavaSloppy",
    ),
    "java_optimized": _entry(
        "java_optimized",
        language="java",
        variant="optimized",
        runtime_kind="java",
        delivery_kind="java_class",
        default_profile_scope="default",
        description="Java benchmark with specialized hot paths for common chain counts.",
        runner_kind="java-class",
        java_class="bench.JavaOptimized",
    ),
    "ruby_sloppy": _entry(
        "ruby_sloppy",
        language="ruby",
        variant="sloppy",
        runtime_kind="ruby",
        delivery_kind="repo_script",
        default_profile_scope="polyglot",
        description="Ruby benchmark using array-backed mutable state for each chain.",
        runner_kind="ruby-script",
        source_path=BENCHMARK_RUBY_DIR / "ruby_sloppy.rb",
    ),
    "ruby_optimized": _entry(
        "ruby_optimized",
        language="ruby",
        variant="optimized",
        runtime_kind="ruby",
        delivery_kind="repo_script",
        default_profile_scope="polyglot",
        description="Ruby benchmark with flatter hot loops and chain-count specializations.",
        runner_kind="ruby-script",
        source_path=BENCHMARK_RUBY_DIR / "ruby_optimized.rb",
    ),
    "perl_sloppy": _entry(
        "perl_sloppy",
        language="perl",
        variant="sloppy",
        runtime_kind="perl",
        delivery_kind="repo_script",
        default_profile_scope="polyglot",
        description="Perl benchmark using nested array state and generic loops.",
        runner_kind="perl-script",
        source_path=BENCHMARK_PERL_DIR / "perl_sloppy.pl",
    ),
    "perl_optimized": _entry(
        "perl_optimized",
        language="perl",
        variant="optimized",
        runtime_kind="perl",
        delivery_kind="repo_script",
        default_profile_scope="polyglot",
        description="Perl benchmark with flatter state layout and specialized fast paths.",
        runner_kind="perl-script",
        source_path=BENCHMARK_PERL_DIR / "perl_optimized.pl",
    ),
    "cpp_sloppy": _entry(
        "cpp_sloppy",
        language="cpp",
        variant="sloppy",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="polyglot",
        description="C++ benchmark using vector-backed chain state and generic loops.",
        runner_kind="native-binary",
        source_path=BENCHMARK_CPP_DIR / "cpp_sloppy.cpp",
        binary_stem="cpp_sloppy",
        compiler_kind="cpp",
    ),
    "cpp_optimized": _entry(
        "cpp_optimized",
        language="cpp",
        variant="optimized",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="polyglot",
        description="C++ benchmark with specialized chain-count kernels and tight loops.",
        runner_kind="native-binary",
        source_path=BENCHMARK_CPP_DIR / "cpp_optimized.cpp",
        binary_stem="cpp_optimized",
        compiler_kind="cpp",
    ),
    "go_sloppy": _entry(
        "go_sloppy",
        language="go",
        variant="sloppy",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="polyglot",
        description="Go benchmark with generic slice-backed state updates.",
        runner_kind="native-binary",
        source_path=BENCHMARK_GO_DIR / "go_sloppy.go",
        binary_stem="go_sloppy",
        compiler_kind="go",
    ),
    "go_optimized": _entry(
        "go_optimized",
        language="go",
        variant="optimized",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="polyglot",
        description="Go benchmark with specialized 4-chain and 8-chain kernels.",
        runner_kind="native-binary",
        source_path=BENCHMARK_GO_DIR / "go_optimized.go",
        binary_stem="go_optimized",
        compiler_kind="go",
    ),
    "rust_sloppy": _entry(
        "rust_sloppy",
        language="rust",
        variant="sloppy",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="polyglot",
        description="Rust benchmark using vector-backed tuples and generic loops.",
        runner_kind="native-binary",
        source_path=BENCHMARK_RUST_DIR / "rust_sloppy.rs",
        binary_stem="rust_sloppy",
        compiler_kind="rust",
    ),
    "rust_optimized": _entry(
        "rust_optimized",
        language="rust",
        variant="optimized",
        runtime_kind="native",
        delivery_kind="repo_binary",
        default_profile_scope="polyglot",
        description="Rust benchmark with flattened state arrays and chain-count specializations.",
        runner_kind="native-binary",
        source_path=BENCHMARK_RUST_DIR / "rust_optimized.rs",
        binary_stem="rust_optimized",
        compiler_kind="rust",
    ),
}


def implementation_entry(implementation_id: str) -> ImplementationEntry:
    return IMPLEMENTATIONS[implementation_id]


def implementation_catalog_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for entry in IMPLEMENTATIONS.values():
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
            }
        )
    return rows
