#!/usr/bin/env bash
# Step 8: deploy the API to Cloud Run with its settings and secrets, then check it is healthy.
source "$(dirname "$0")/common.sh"
require PROJECT_ID REGION WEB_HOST API_HOST ALLOWED_DOMAINS BOOTSTRAP_ADMINS SUPABASE_PROJECT_REF SUPABASE_POOLER_HOST GOOGLE_CLIENT_ID
TAG="${1:-$(cat "$STATE_DIR/last-tag" 2>/dev/null || true)}"; [ -n "$TAG" ] || fail "No image tag. Run 03-build-push.sh first."
gcloud config set project "$PROJECT_ID" >/dev/null
# One list, '|' separated (the ^|^ prefix tells gcloud to split on '|' because some values contain commas).
ENV_VARS="^|^SPRING_PROFILES_ACTIVE=prod"
ENV_VARS+="|SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME|EST_DB_SCHEMA=est|EST_DB_POOL_SIZE=5"
ENV_VARS+="|EST_WEB_ORIGINS=https://$WEB_HOST|EST_JWT_ISSUER=https://$API_HOST|EST_COOKIE_SECURE=true"
ENV_VARS+="|EST_ALLOWED_DOMAINS=$ALLOWED_DOMAINS|EST_BOOTSTRAP_ADMIN_EMAILS=$BOOTSTRAP_ADMINS"
ENV_VARS+="|GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID|GOOGLE_REDIRECT_URI=https://$API_HOST/api/v1/calendar/identities/link/callback"
ENV_VARS+="|EST_SYNC_ENABLED=true|EST_SYNC_INTERVAL=PT15M"
info "Deploying est-api:$TAG (1–2 minutes)…"
gcloud run deploy est-api --image "$IMG/est-api:$TAG" --region "$REGION" --platform managed \
  --service-account "$API_SA" --allow-unauthenticated \
  --port 8080 --cpu 1 --memory 1Gi --cpu-boost --min-instances 1 --max-instances 1 \
  --set-env-vars "$ENV_VARS" \
  --set-secrets "SPRING_DATASOURCE_PASSWORD=est-db-password:latest,EST_JWT_PRIVATE_KEY=est-jwt-private-key:latest,EST_JWT_PUBLIC_KEY=est-jwt-public-key:latest,EST_CRYPTO_KEY=est-crypto-key:latest,GOOGLE_CLIENT_SECRET=est-google-client-secret:latest" \
  --quiet >/dev/null
URL="$(gcloud run services describe est-api --region "$REGION" --format='value(status.url)')"
echo "$URL" > "$STATE_DIR/api-url"
ok "Deployed. Temporary address: $URL"
info "Health check…"
for i in $(seq 1 20); do
  if curl -fs "$URL/actuator/health" | grep -q '"UP"'; then ok "API is UP and connected to Supabase"; break; fi
  sleep 3; [ "$i" -eq 20 ] && fail "API not healthy. Run:  gcloud run services logs read est-api --region $REGION --limit 100"
done
curl -fs "$URL/api/v1/features" | grep -q '"devMode":false' && ok "Production flags: devMode=false, mockGoogle=false"
gcloud run services logs read est-api --region "$REGION" --limit 200 2>/dev/null | grep -i "Successfully applied\|is up to date" | tail -1 | sed 's/^/✔ Flyway: /' || true
