# Runtime Bundles

The launcher checks these directories before falling back to system tools:

- `tools/runtime/macos-arm64/python/bin/python3`
- `tools/runtime/macos-arm64/node/bin/node`
- `tools/runtime/macos-arm64/ruby/bin/ruby`
- `tools/runtime/macos-arm64/perl/bin/perl`
- `tools/runtime/macos-arm64/java/bin/java`
- `tools/runtime/macos-arm64/java/bin/javac`
- `tools/runtime/macos-arm64/go/bin/go`
- `tools/runtime/macos-arm64/rust/bin/rustc`
- `tools/runtime/windows-x64/python/python.exe`
- `tools/runtime/windows-x64/node/node.exe`
- `tools/runtime/windows-x64/ruby/ruby.exe`
- `tools/runtime/windows-x64/perl/perl.exe`
- `tools/runtime/windows-x64/java/bin/java.exe`
- `tools/runtime/windows-x64/java/bin/javac.exe`
- `tools/runtime/windows-x64/go/bin/go.exe`
- `tools/runtime/windows-x64/rust/bin/rustc.exe`

Supported targets are:

- `macos-arm64`
- `windows-x64`

If you want fully self-contained fresh-clone execution, drop the matching runtime bundles into those paths. The current launchers already prefer them automatically, while compiled-language binaries can also be provided directly under `tools/bin/<host>/`.
