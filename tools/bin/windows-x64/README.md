Place prebuilt Windows binaries here when you want runs to work without a host compiler or toolchain.

Expected native binary names:

- `c_native.exe`
- `cpp_sloppy.exe`
- `cpp_optimized.exe`
- `go_sloppy.exe`
- `go_optimized.exe`
- `rust_sloppy.exe`
- `rust_optimized.exe`

If a binary is absent, the backend falls back to building from the corresponding source under `benchmarks/` when a matching local toolchain is available.
