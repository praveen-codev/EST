#!/usr/bin/env bash
# Step 10: attach your own domain names to both services and print the DNS records to create.
source "$(dirname "$0")/common.sh"
[ "$DOMAIN_MODE" = custom ] || { ok "DOMAIN_MODE=none — no domain to attach; your app lives at ${WEB_URL:-<run 05-deploy-web.sh>}. Skip to 07-verify.sh."; exit 0; }
require PROJECT_ID REGION WEB_HOST API_HOST
gcloud config set project "$PROJECT_ID" >/dev/null
map() { # service host
  gcloud beta run domain-mappings describe --domain "$2" --region "$REGION" >/dev/null 2>&1 \
    || gcloud beta run domain-mappings create --service "$1" --domain "$2" --region "$REGION" --quiet >/dev/null
  ok "Domain mapping: https://$2 → $1"
}
map est-api "$API_HOST"
map est-web "$WEB_HOST"
echo
echo "Create these DNS records at your domain provider (exactly as printed):"
for h in "$API_HOST" "$WEB_HOST"; do
  gcloud beta run domain-mappings describe --domain "$h" --region "$REGION" \
    --format='table[no-heading](status.resourceRecords[].name:label=NAME,status.resourceRecords[].type:label=TYPE,status.resourceRecords[].rrdata:label=VALUE)' \
    | sed "s/^/  $h  /"
done
echo
echo "Then run:  infra/deploy/07-verify.sh   (certificates take 5–30 minutes after DNS is in place)"
