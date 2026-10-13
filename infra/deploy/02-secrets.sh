#!/usr/bin/env bash
# Step 6: generate the app's own keys and store every secret in Secret Manager. Safe to re-run (adds new versions).
source "$(dirname "$0")/common.sh"
require PROJECT_ID SUPABASE_DB_PASSWORD GOOGLE_CLIENT_SECRET
gcloud config set project "$PROJECT_ID" >/dev/null
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/jwt.pem" 2>/dev/null
openssl pkey -in "$tmp/jwt.pem" -pubout -out "$tmp/jwt.pub" 2>/dev/null
openssl rand -base64 32 > "$tmp/crypto.key"
put() { # name file
  if gcloud secrets describe "$1" >/dev/null 2>&1; then
    gcloud secrets versions add "$1" --data-file="$2" >/dev/null && ok "Updated secret $1 (new version)"
  else
    gcloud secrets create "$1" --data-file="$2" --replication-policy=automatic >/dev/null && ok "Created secret $1"
  fi
}
if gcloud secrets describe est-jwt-private-key >/dev/null 2>&1 && [ "${ROTATE_KEYS:-no}" != "yes" ]; then
  ok "JWT key pair and crypto key already exist — keeping them (set ROTATE_KEYS=yes to replace)"
else
  put est-jwt-private-key "$tmp/jwt.pem"
  put est-jwt-public-key  "$tmp/jwt.pub"
  put est-crypto-key      "$tmp/crypto.key"
fi
printf '%s' "$SUPABASE_DB_PASSWORD"  > "$tmp/db";     put est-db-password "$tmp/db"
printf '%s' "$GOOGLE_CLIENT_SECRET"  > "$tmp/gcs";    put est-google-client-secret "$tmp/gcs"
for s in est-jwt-private-key est-jwt-public-key est-crypto-key est-db-password est-google-client-secret; do
  gcloud secrets add-iam-policy-binding "$s" --member="serviceAccount:$API_SA" --role=roles/secretmanager.secretAccessor >/dev/null
done
ok "API runtime identity may read all five secrets"
echo
echo "Verify: https://console.cloud.google.com/security/secret-manager?project=$PROJECT_ID  → 5 secrets starting with 'est-'."
