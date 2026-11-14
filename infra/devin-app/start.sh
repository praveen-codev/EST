#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="${EST_STATE_DIR:-$HOME/.local/share/est-devin-app}"
APP_PORT="${PORT:-3000}"
API_PORT="${EST_API_PORT:-8080}"
ENV_FILE="${EST_ENV_FILE:-$ROOT/.env.local}"

if [[ -f "$ENV_FILE" ]]; then
  eval "$(
    EST_ENV_FILE="$ENV_FILE" python3 - <<'PY'
import ast
import os
import shlex
from pathlib import Path

wanted = {"DATABASE_URL", "DATABASE_URL_UNPOOLED"}
for raw in Path(os.environ["EST_ENV_FILE"]).read_text().splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    key = key.strip()
    if key not in wanted:
        continue
    value = value.strip()
    if value[:1] in {"'", '"'}:
        value = ast.literal_eval(value)
    print(f"export NEON_FILE_{key}={shlex.quote(value)}")
PY
  )"
fi

EST_DATABASE_URL="${EST_DATABASE_URL:-${DATABASE_URL:-${NEON_FILE_DATABASE_URL:-}}}"
EST_DATABASE_URL_UNPOOLED="${EST_DATABASE_URL_UNPOOLED:-${DATABASE_URL_UNPOOLED:-${NEON_FILE_DATABASE_URL_UNPOOLED:-$EST_DATABASE_URL}}}"

: "${EST_DATABASE_URL:?Set DATABASE_URL or EST_DATABASE_URL to the pooled Neon PostgreSQL connection string}"

mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"

if [[ ! -f "$STATE_DIR/jwt-private.pem" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$STATE_DIR/jwt-private.pem" 2>/dev/null
  openssl pkey -in "$STATE_DIR/jwt-private.pem" -pubout -out "$STATE_DIR/jwt-public.pem" 2>/dev/null
  openssl rand -base64 32 > "$STATE_DIR/crypto-key"
  chmod 600 "$STATE_DIR"/*
fi

eval "$(
  EST_DATABASE_URL="$EST_DATABASE_URL" \
  EST_DATABASE_URL_UNPOOLED="$EST_DATABASE_URL_UNPOOLED" \
  python3 - <<'PY'
import os
import shlex
from urllib.parse import parse_qs, unquote, urlparse

def parse_database_url(name, infer_string_types=False):
    url = urlparse(os.environ[name])
    if url.scheme not in {"postgres", "postgresql"}:
        raise SystemExit(f"{name} must use postgres:// or postgresql://")

    query = parse_qs(url.query)
    sslmode = query.get("sslmode", ["require"])[0]
    database = url.path.lstrip("/") or "neondb"
    port = url.port or 5432
    options = f"sslmode={sslmode}"
    if infer_string_types:
        options += "&stringtype=unspecified"
    return {
        "url": f"jdbc:postgresql://{url.hostname}:{port}/{database}?{options}",
        "username": unquote(url.username or ""),
        "password": unquote(url.password or ""),
    }

app = parse_database_url("EST_DATABASE_URL", infer_string_types=True)
flyway = parse_database_url("EST_DATABASE_URL_UNPOOLED")
values = {
    "SPRING_DATASOURCE_URL": app["url"],
    "SPRING_DATASOURCE_USERNAME": app["username"],
    "SPRING_DATASOURCE_PASSWORD": app["password"],
    "SPRING_FLYWAY_URL": flyway["url"],
    "SPRING_FLYWAY_USER": flyway["username"],
    "SPRING_FLYWAY_PASSWORD": flyway["password"],
}
for key, value in values.items():
    print(f"export {key}={shlex.quote(value)}")
PY
)"

export SPRING_PROFILES_ACTIVE=devin-app
export EST_JWT_PRIVATE_KEY
export EST_JWT_PUBLIC_KEY
export EST_CRYPTO_KEY
EST_JWT_PRIVATE_KEY="$(cat "$STATE_DIR/jwt-private.pem")"
EST_JWT_PUBLIC_KEY="$(cat "$STATE_DIR/jwt-public.pem")"
EST_CRYPTO_KEY="$(cat "$STATE_DIR/crypto-key")"

API_JAR="$(find "$ROOT/backend/build/libs" -maxdepth 1 -name 'est-api-*.jar' -print -quit)"
if [[ -z "$API_JAR" ]]; then
  echo "Backend jar not found. Run infra/devin-app/build.sh first." >&2
  exit 1
fi
if [[ ! -f "$ROOT/web/.next/standalone/server.js" ]]; then
  echo "Next.js standalone build not found. Run infra/devin-app/build.sh first." >&2
  exit 1
fi

cleanup() {
  if [[ -n "${API_PID:-}" ]]; then kill "$API_PID" 2>/dev/null || true; fi
}
trap cleanup EXIT INT TERM

PORT="$API_PORT" java -jar "$API_JAR" &
API_PID=$!

for _ in $(seq 1 60); do
  if curl -fs "http://127.0.0.1:$API_PORT/actuator/health/readiness" >/dev/null; then
    break
  fi
  if ! kill -0 "$API_PID" 2>/dev/null; then
    wait "$API_PID"
    exit 1
  fi
  sleep 1
done

curl -fs "http://127.0.0.1:$API_PORT/actuator/health/readiness" >/dev/null

cd "$ROOT/web/.next/standalone"
HOSTNAME=0.0.0.0 PORT="$APP_PORT" node server.js
