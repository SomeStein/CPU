#!/bin/sh
set -eu

DEFAULT_PATH="/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:/opt/homebrew/bin:/Library/Frameworks/Python.framework/Versions/Current/bin"
if [ -n "${PATH:-}" ]; then
  PATH="$PATH:$DEFAULT_PATH"
else
  PATH="$DEFAULT_PATH"
fi
export PATH

SCRIPT_PATH=$0
case "$SCRIPT_PATH" in
  */*)
    SCRIPT_DIR=${SCRIPT_PATH%/*}
    ;;
  *)
    SCRIPT_DIR=.
    ;;
esac

ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
TASK_TMP_DIR="$ROOT_DIR/build/tmp"
/bin/mkdir -p "$TASK_TMP_DIR"
TMPDIR=${TMPDIR:-$TASK_TMP_DIR}
TMP=${TMP:-$TASK_TMP_DIR}
TEMP=${TEMP:-$TASK_TMP_DIR}
export TMPDIR TMP TEMP

resolve_python() {
  if [ -n "${PYTHON_BIN:-}" ] && [ -x "${PYTHON_BIN}" ]; then
    printf '%s\n' "${PYTHON_BIN}"
    return 0
  fi

  for candidate in \
    "$ROOT_DIR/tools/runtime/macos-arm64/python/bin/python3" \
    "/Library/Frameworks/Python.framework/Versions/Current/bin/python3" \
    "/opt/homebrew/bin/python3" \
    "/usr/local/bin/python3" \
    "/usr/bin/python3"
  do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  if command -v python3 >/dev/null 2>&1; then
    command -v python3
    return 0
  fi

  if command -v python >/dev/null 2>&1; then
    command -v python
    return 0
  fi

  printf '%s\n' "Unable to locate Python 3. Add a bundled runtime under $ROOT_DIR/tools/runtime/macos-arm64/python or install Python 3." >&2
  return 1
}

PYTHON_BIN=$(resolve_python)

exec "$PYTHON_BIN" "$ROOT_DIR/scripts/launcher.py" "$@"
