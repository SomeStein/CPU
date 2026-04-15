Place prebuilt Apple Silicon macOS binaries here when you want runs to work without host toolchains.

Expected native binary names:

- `c_native`
- `cpp_sloppy`
- `cpp_optimized`
- `go_sloppy`
- `go_optimized`
- `rust_sloppy`
- `rust_optimized`

If a binary is absent, the backend falls back to building from the corresponding source under `benchmarks/` when a matching local toolchain is available.
