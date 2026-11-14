#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE_ENV="${EST_SOURCE_ENV_FILE:-$ROOT/.env.local}"
SANDBOX_ENV="${EST_SANDBOX_ENV_FILE:-$ROOT/.env.sandbox.local}"

for file in "$SOURCE_ENV" "$SANDBOX_ENV"; do
  if [[ ! -f "$file" ]]; then
    echo "Environment file not found: $file" >&2
    exit 1
  fi
done

eval "$(
  EST_SOURCE_ENV_FILE="$SOURCE_ENV" EST_SANDBOX_ENV_FILE="$SANDBOX_ENV" python3 - <<'PY'
import ast
import os
import shlex
from pathlib import Path

def read_url(name):
    values = {}
    for raw in Path(os.environ[name]).read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() not in {"DATABASE_URL", "DATABASE_URL_UNPOOLED"}:
            continue
        value = value.strip()
        if value[:1] in {"'", '"'}:
            value = ast.literal_eval(value)
        values[key.strip()] = value
    return values.get("DATABASE_URL_UNPOOLED") or values.get("DATABASE_URL") or ""

for key, value in {
    "EST_SOURCE_DATABASE_URL": read_url("EST_SOURCE_ENV_FILE"),
    "EST_SANDBOX_DATABASE_URL": read_url("EST_SANDBOX_ENV_FILE"),
}.items():
    if not value:
        raise SystemExit(f"{key} could not be resolved")
    print(f"export {key}={shlex.quote(value)}")
PY
)"

set +u
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 25.0.4-tem >/dev/null
set -u
"$ROOT/backend/gradlew" -p "$ROOT/backend" replicateCalendarEvidence --no-daemon --console=plain
