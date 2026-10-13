#!/usr/bin/env bash
# Step 7: build the API and web images on your Mac and push them to Artifact Registry (5–10 minutes the first time).
source "$(dirname "$0")/common.sh"
require PROJECT_ID REGION GOOGLE_CLIENT_ID
if [ "$DOMAIN_MODE" = none ]; then
  [ -n "$API_SERVICE_URL" ] || fail "DOMAIN_MODE=none: deploy the API first (04-deploy-api.sh) so its address can be built into the web app."
  WEB_API_URL=""; INTERNAL="$API_SERVICE_URL"; where="this same host (/api is proxied to $API_SERVICE_URL)"
else
  WEB_API_URL="https://$API_HOST"; INTERNAL=""; where="https://$API_HOST"
fi
cd "$REPO_ROOT"
TAG="$(git rev-parse --short HEAD)"; echo "$TAG" > "$STATE_DIR/last-tag"
info "Building API image ($TAG)…"
docker build --platform linux/amd64 -q -f infra/Dockerfile.api -t "$IMG/est-api:$TAG" . >/dev/null
docker push -q "$IMG/est-api:$TAG" >/dev/null && ok "Pushed $IMG/est-api:$TAG"
info "Building web image (the browser will reach the API at: $where)…"
docker build --platform linux/amd64 -q -f infra/Dockerfile.web \
  --build-arg "NEXT_PUBLIC_API_URL=$WEB_API_URL" --build-arg "API_INTERNAL_URL=$INTERNAL" --build-arg "NEXT_PUBLIC_GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID" \
  -t "$IMG/est-web:$TAG" . >/dev/null
docker push -q "$IMG/est-web:$TAG" >/dev/null && ok "Pushed $IMG/est-web:$TAG"
echo
echo "Verify: gcloud artifacts docker images list $IMG   (both images with tag $TAG)"
