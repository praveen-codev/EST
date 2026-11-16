#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${EST_ARCHIVE_ENV_FILE:-$ROOT/.env.archive.local}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Archive environment file not found: $ENV_FILE" >&2
  exit 1
fi

eval "$(
  EST_ENV_FILE="$ENV_FILE" EST_ROOT="$ROOT" python3 - <<'PY'
import ast
import os
import shlex
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

values = {}
for raw in Path(os.environ["EST_ENV_FILE"]).read_text().splitlines():
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

url = urlparse(values.get("DATABASE_URL_UNPOOLED") or values.get("DATABASE_URL") or "")
if url.scheme not in {"postgres", "postgresql"}:
    raise SystemExit("Archive DATABASE_URL_UNPOOLED must be a PostgreSQL URL")

query = parse_qs(url.query)
sslmode = query.get("sslmode", ["require"])[0]
database = url.path.lstrip("/") or "neondb"
port = url.port or 5432
jdbc = f"jdbc:postgresql://{url.hostname}:{port}/{database}?sslmode={sslmode}"

for key, value in {
    "ARCHIVE_JDBC_URL": jdbc,
    "ARCHIVE_DB_USER": unquote(url.username or ""),
    "ARCHIVE_DB_PASSWORD": unquote(url.password or ""),
    "ARCHIVE_FLYWAY_LOCATIONS": f"filesystem:{Path(os.environ['EST_ROOT']) / 'infra/neon/archive-migrations'}",
}.items():
    print(f"export {key}={shlex.quote(value)}")
PY
)"

set +u
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 25.0.4-tem >/dev/null
set -u
"$ROOT/backend/gradlew" -p "$ROOT/backend" migrateArchive --no-daemon --console=plain
