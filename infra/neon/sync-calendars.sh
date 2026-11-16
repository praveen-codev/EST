#!/usr/bin/env bash
# Headless production calendar sync for an external scheduler (see infra/neon/README.md).
# Required environment: EST_PROD_DATABASE_URL, EST_CRYPTO_KEY, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET.
# Exit codes: 0 ok, 1 error, 2 refused, 3 some identities failed.
set -euo pipefail

missing=()
for name in EST_PROD_DATABASE_URL EST_CRYPTO_KEY GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET; do
  [[ -n "${!name:-}" ]] || missing+=("$name")
done
if (( ${#missing[@]} )); then
  echo "Missing required environment: ${missing[*]}" >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE=prod
exec "$(dirname "${BASH_SOURCE[0]}")/run-job.sh" com.cognition.est.calendar.CalendarSyncJob
