#!/usr/bin/env bash
# Step 11: end-to-end verification of the live deployment. Re-run until everything is ✔.
source "$(dirname "$0")/common.sh"
require REGION WEB_HOST API_HOST
status=0
check() { if "$@"; then :; else status=1; fi; }
dns() { if host "$1" >/dev/null 2>&1 || nslookup "$1" >/dev/null 2>&1; then ok "DNS resolves: $1"; else printf '✖ DNS does not resolve yet: %s (wait for your DNS change to propagate)\n' "$1"; return 1; fi; }
check dns "$API_HOST"; check dns "$WEB_HOST"
for h in "$API_HOST" "$WEB_HOST"; do
  c="$(gcloud beta run domain-mappings describe --domain "$h" --region "$REGION" --format='value(status.conditions[0].status)' 2>/dev/null || echo Unknown)"
  if [ "$c" = "True" ]; then ok "Certificate ready: $h"; else printf '✖ Certificate not ready for %s (status %s) — usually 5–30 min after DNS\n' "$h" "$c"; status=1; fi
done
if curl -fs "https://$API_HOST/actuator/health" | grep -q '"UP"'; then ok "https://$API_HOST/actuator/health → UP"; else printf '✖ API not reachable on https://%s yet\n' "$API_HOST"; status=1; fi
if curl -fs "https://$API_HOST/api/v1/features" | grep -q '"googleClientId":"[^"]\+"'; then ok "API knows the Google client id"; else printf '✖ /api/v1/features has no googleClientId\n'; status=1; fi
code="$(curl -s -o /dev/null -w '%{http_code}' "https://$API_HOST/api/v1/snapshot")"; [ "$code" = "401" ] && ok "Protected routes require sign-in (401)" || { printf '✖ /snapshot returned %s, expected 401\n' "$code"; status=1; }
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "https://$API_HOST/api/v1/dev/token?email=x")"; [ "$code" = "404" ] && ok "Dev endpoints are off (404)" || { printf '✖ /dev/token returned %s, expected 404\n' "$code"; status=1; }
if curl -fs -o /dev/null "https://$WEB_HOST/sign-in"; then ok "https://$WEB_HOST/sign-in loads"; else printf '✖ Web not reachable on https://%s yet\n' "$WEB_HOST"; status=1; fi
echo
[ $status -eq 0 ] && echo "All checks passed. Open https://$WEB_HOST and sign in with one of: $BOOTSTRAP_ADMINS" || echo "Some checks failed — fix the ✖ lines above and re-run this script."
exit $status
