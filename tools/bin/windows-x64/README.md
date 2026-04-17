Place prebuilt Windows binaries here when you want runs to work without a host compiler or toolchain.

Expected native binary names:

- `c.exe`
- `cpp.exe`
- `go.exe`
- `rust.exe`

If a binary is absent, the backend falls back to building from the corresponding source under `benchmarks/` when a matching local toolchain is available.
