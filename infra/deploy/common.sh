#!/usr/bin/env bash
# Shared by every deploy script: loads ~/est-deploy.env, validates inputs, defines helpers.
set -euo pipefail

ENV_FILE="${EST_DEPLOY_ENV:-$HOME/est-deploy.env}"
if [ ! -f "$ENV_FILE" ]; then
  echo "✖ Settings file not found: $ENV_FILE"
  echo "  Copy infra/deploy/est-deploy.env.example to ~/est-deploy.env and fill it in (Step 1 of the guide)."
  exit 1
fi
set -a; # shellcheck disable=SC1090
source "$ENV_FILE"; set +a

ok()   { printf '✔ %s\n' "$*"; }
info() { printf '… %s\n' "$*"; }
fail() { printf '✖ %s\n' "$*" >&2; exit 1; }

require() {
  local missing=0
  for v in "$@"; do
    if [ -z "${!v:-}" ] || [[ "${!v:-}" == *change-me* ]]; then
      printf '✖ %s is missing or still a placeholder in %s\n' "$v" "$ENV_FILE" >&2; missing=1
    fi
  done
  [ "$missing" -eq 0 ] || exit 1
}

need_tool() { command -v "$1" >/dev/null 2>&1 || fail "'$1' is not installed. $2"; }

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="$HOME/.est-deploy"; mkdir -p "$STATE_DIR"
API_SA="est-api@${PROJECT_ID:-}.iam.gserviceaccount.com"
IMG="${REGION:-}-docker.pkg.dev/${PROJECT_ID:-}/est"
SPRING_DATASOURCE_URL="jdbc:postgresql://${SUPABASE_POOLER_HOST:-}:5432/postgres?sslmode=require&currentSchema=est"
SPRING_DATASOURCE_USERNAME="postgres.${SUPABASE_PROJECT_REF:-}"
