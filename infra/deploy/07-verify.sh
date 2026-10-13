#!/usr/bin/env bash
# Step 11: end-to-end verification of the live deployment. Re-run until everything is ✔.
source "$(dirname "$0")/common.sh"
require REGION
status=0
check() { if "$@"; then :; else status=1; fi; }
if [ "$DOMAIN_MODE" = custom ]; then
  dns() { if host "$1" >/dev/null 2>&1 || nslookup "$1" >/dev/null 2>&1; then ok "DNS resolves: $1"; else printf '✖ DNS does not resolve yet: %s (wait for your DNS change to propagate)\n' "$1"; return 1; fi; }
  check dns "$API_HOST"; check dns "$WEB_HOST"
  for h in "$API_HOST" "$WEB_HOST"; do
    c="$(gcloud beta run domain-mappings describe --domain "$h" --region "$REGION" --format='value(status.conditions[0].status)' 2>/dev/null || echo Unknown)"
    if [ "$c" = "True" ]; then ok "Certificate ready: $h"; else printf '✖ Certificate not ready for %s (status %s) — usually 5–30 min after DNS\n' "$h" "$c"; status=1; fi
  done
else
  [ -n "$WEB_URL" ] || fail "No web address recorded yet — run 05-deploy-web.sh first."
  ok "Single-host mode: web and API share $WEB_URL"
fi
API="${API_PUBLIC_URL:-$WEB_URL}"
if curl -fs "$API/actuator/health" | grep -q '"UP"'; then ok "$API/actuator/health → UP"; else printf '✖ API not reachable on %s yet\n' "$API"; status=1; fi
if curl -fs "$API/api/v1/features" | grep -q '"googleClientId":"[^"]\+"'; then ok "API knows the Google client id"; else printf '✖ /api/v1/features has no googleClientId\n'; status=1; fi
code="$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/snapshot")"; [ "$code" = "401" ] && ok "Protected routes require sign-in (401)" || { printf '✖ /snapshot returned %s, expected 401\n' "$code"; status=1; }
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/v1/dev/token?email=x")"; [ "$code" = "404" ] && ok "Dev endpoints are off (404)" || { printf '✖ /dev/token returned %s, expected 404\n' "$code"; status=1; }
if curl -fs -o /dev/null "$WEB_URL/sign-in"; then ok "$WEB_URL/sign-in loads"; else printf '✖ Web not reachable on %s yet\n' "$WEB_URL"; status=1; fi
origins="$(gcloud run services describe est-api --region "$REGION" --format='value(spec.template.spec.containers[0].env)' 2>/dev/null || true)"
if echo "$origins" | grep -q "pending.invalid"; then printf '✖ API still has the placeholder web address — run 04-deploy-api.sh again\n'; status=1; fi
echo
[ $status -eq 0 ] && echo "All checks passed. Open $WEB_URL and sign in with one of: $BOOTSTRAP_ADMINS" || echo "Some checks failed — fix the ✖ lines above and re-run this script."
exit $status
