#!/usr/bin/env bash
set -eo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 25.0.4-tem >/dev/null
set -u

"$ROOT/backend/gradlew" -p "$ROOT/backend" bootJar --no-daemon

cd "$ROOT/web"
corepack enable
pnpm install --frozen-lockfile
NEXT_PUBLIC_API_URL="" \
NEXT_PUBLIC_GOOGLE_CLIENT_ID="" \
API_INTERNAL_URL="${API_INTERNAL_URL:-http://127.0.0.1:8080}" \
pnpm build

mkdir -p .next/standalone/.next
rm -rf .next/standalone/.next/static .next/standalone/public
cp -R .next/static .next/standalone/.next/static
cp -R public .next/standalone/public
