# Tool Manifests

Each host-specific manifest under `tools/manifests/<host>/` pins one repo-local
runtime or toolchain component used by the self-hosted build flow.

Required base fields:

- `tool_id`
- `host`
- `version`
- `url`
- `sha256`
- `archive_type`
- `destination`
- `executables`
- `license_files`

Supported archive types in the current bootstrap flow:

- `zip`
- `tar.gz`
- `tar.xz`
- `exe`
- `rustup-init`

Additional fields used by the repo-local installer:

- `component_kind`
- `required_for_launch`
- `required_for_build`
- `archive_subdir`
- `install_args`
- `install_env`
- `install_enabled`
- `install_hint`
