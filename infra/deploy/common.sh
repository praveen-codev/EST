#!/usr/bin/env bash
# Shared by every deploy script: loads ~/est-deploy.env, validates inputs, defines helpers.
set -euo pipefail

ENV_FILE="${EST_DEPLOY_ENV:-$HOME/est-deploy.env}"
if [ ! -f "$ENV_FILE" ]; then
  echo "✖ Settings file not found: $ENV_FILE"
  echo "  Copy infra/deploy/est-deploy.env.example to ~/est-deploy.env and fill it in (Step 1 of the guide)."
  exit 1
fi
# Read KEY=VALUE lines literally — the file is NOT executed, so passwords may contain $ ! ` \ etc.
# Rules: one KEY=VALUE per line; `# comment` lines and trailing " # comments" are ignored; wrap a value
# in double quotes if it contains spaces or a #.
load_env_file() {
  local line key val n=0
  while IFS= read -r line || [ -n "$line" ]; do
    n=$((n + 1)); line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
    if [[ "$line" != *=* ]]; then echo "✖ $ENV_FILE line $n is not KEY=VALUE: $line" >&2; exit 1; fi
    key="${line%%=*}"; val="${line#*=}"
    key="${key//[[:space:]]/}"; key="${key#export}"
    if ! [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then echo "✖ $ENV_FILE line $n has an invalid name: $key" >&2; exit 1; fi
    if [[ "$val" =~ ^[[:space:]]*\"(.*)\"[[:space:]]*(#.*)?$ ]]; then val="${BASH_REMATCH[1]}"
    elif [[ "$val" =~ ^[[:space:]]*\'(.*)\'[[:space:]]*(#.*)?$ ]]; then val="${BASH_REMATCH[1]}"
    else
      val="${val%%[[:space:]]#*}"                      # drop a trailing " # comment"
      val="${val#"${val%%[![:space:]]*}"}"; val="${val%"${val##*[![:space:]]}"}"   # trim
    fi
    printf -v "$key" '%s' "$val"; export "$key"
  done < "$ENV_FILE"
}
load_env_file

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

# Cloud Run only answers anonymous requests when allUsers holds roles/run.invoker. Organisations created
# recently carry Google's "Domain Restricted Sharing" policy, which silently rejects that grant → every
# request gets a 403 "Your client does not have permission". Detect it and say exactly what to do.
ensure_public() { # service
  if gcloud run services get-iam-policy "$1" --region "$REGION" --format=json 2>/dev/null | grep -q '"allUsers"'; then
    ok "$1 is publicly reachable (allUsers → roles/run.invoker)"; return 0
  fi
  local out
  out="$(gcloud run services add-iam-policy-binding "$1" --region "$REGION" --member=allUsers --role=roles/run.invoker 2>&1)" && { ok "$1 is now publicly reachable"; return 0; }
  if echo "$out" | grep -qi "organization policy\|permitted customer"; then
    cat >&2 <<EOT
✖ Google Cloud refused to make $1 public: your organisation enforces the "Domain Restricted Sharing"
  policy (iam.allowedPolicyMemberDomains), so 'allUsers' may not be granted roles/run.invoker.
  Fix (one-time, 2 minutes, needs the Organization Policy Administrator role — a Workspace super admin can grant it):
    1. https://console.cloud.google.com/iam-admin/orgpolicies/iam-allowedPolicyMemberDomains?project=$PROJECT_ID
    2. Manage policy → "Override parent's policy" → Rules → Add a rule → "Allow all" → Set policy
    3. Re-run this script.
  Or from Terminal, once you have that role:
    printf 'name: projects/$PROJECT_ID/policies/iam.allowedPolicyMemberDomains\nspec:\n  rules:\n  - allowAll: true\n' > /tmp/est-orgpolicy.yaml
    gcloud services enable orgpolicy.googleapis.com && gcloud org-policies set-policy /tmp/est-orgpolicy.yaml
  This only relaxes the policy for project $PROJECT_ID; the rest of the organisation is unchanged.
EOT
    exit 1
  fi
  fail "Could not make $1 public: $out"
}

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="$HOME/.est-deploy"; mkdir -p "$STATE_DIR"
API_SA="est-api@${PROJECT_ID:-}.iam.gserviceaccount.com"
IMG="${REGION:-}-docker.pkg.dev/${PROJECT_ID:-}/est"
SPRING_DATASOURCE_URL="jdbc:postgresql://${SUPABASE_POOLER_HOST:-}:5432/postgres?sslmode=require&currentSchema=est"
SPRING_DATASOURCE_USERNAME="postgres.${SUPABASE_PROJECT_REF:-}"

# Public addresses. custom: your hostnames. none: the Cloud Run URLs remembered after each deploy.
DOMAIN_MODE="${DOMAIN_MODE:-none}"
case "$DOMAIN_MODE" in
  custom) require DOMAIN WEB_HOST API_HOST; WEB_URL="https://$WEB_HOST"; API_PUBLIC_URL="https://$API_HOST" ;;
  none)   WEB_URL="$(cat "$STATE_DIR/web-url" 2>/dev/null || true)"; API_PUBLIC_URL="$WEB_URL" ;;
  *)      fail "DOMAIN_MODE must be 'none' or 'custom' (got '$DOMAIN_MODE')" ;;
esac
API_SERVICE_URL="$(cat "$STATE_DIR/api-url" 2>/dev/null || true)"
