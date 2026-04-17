# Runtime Bundles

`scripts/controller_api.py prepare-host --download-missing` installs pinned repo-local
runtimes and toolchains from `tools/manifests/<host>/` into `tools/runtime/<host>/`
and `tools/toolchains/<host>/`.

Supported hosts:

- `macos-arm64`
- `windows-x64`

Expected runtime directories:

- `tools/runtime/<host>/python`
- `tools/runtime/<host>/node`
- `tools/runtime/<host>/ruby`
- `tools/runtime/<host>/perl`
- `tools/runtime/<host>/java`
- `tools/runtime/<host>/go`

Expected toolchain directories:

- `tools/toolchains/<host>/llvm`
- `tools/toolchains/<host>/rust`

Expected executables:

- `python/python.exe` or `python/bin/python3`
- `node/node.exe` or `node/bin/node`
- `ruby/ruby.exe` or `ruby/bin/ruby`
- `perl/perl.exe` or `perl/bin/perl`
- `java/bin/java(.exe)`
- `java/bin/javac(.exe)`
- `java/bin/jar(.exe)`
- `java/bin/jpackage(.exe)`
- `go/bin/go(.exe)`
- `../toolchains/<host>/llvm/bin/clang(.exe)`
- `../toolchains/<host>/llvm/bin/clang++(.exe)`
- `../toolchains/<host>/rust/cargo/bin/rustc(.exe)`

Bundle checklist:

- Keep pinned manifests in `tools/manifests/<host>/`.
- Record the exact version chosen for each runtime before producing a distribution build.
- Keep one pinned version per host/runtime combination inside your release notes.
- Prebuilt native workers belong in `tools/bin/<host>/`.
- `tools/jars/java-worker.jar` is created during packaging and should not be hand-edited.
