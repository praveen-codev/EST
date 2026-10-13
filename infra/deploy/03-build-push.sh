#!/usr/bin/env bash
# Step 7: build the API and web images on your Mac and push them to Artifact Registry (5–10 minutes the first time).
source "$(dirname "$0")/common.sh"
require PROJECT_ID REGION API_HOST GOOGLE_CLIENT_ID
cd "$REPO_ROOT"
TAG="$(git rev-parse --short HEAD)"; echo "$TAG" > "$STATE_DIR/last-tag"
info "Building API image ($TAG)…"
docker build --platform linux/amd64 -q -f infra/Dockerfile.api -t "$IMG/est-api:$TAG" . >/dev/null
docker push -q "$IMG/est-api:$TAG" >/dev/null && ok "Pushed $IMG/est-api:$TAG"
info "Building web image (the API address https://$API_HOST is compiled in)…"
docker build --platform linux/amd64 -q -f infra/Dockerfile.web \
  --build-arg "NEXT_PUBLIC_API_URL=https://$API_HOST" --build-arg "NEXT_PUBLIC_GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID" \
  -t "$IMG/est-web:$TAG" . >/dev/null
docker push -q "$IMG/est-web:$TAG" >/dev/null && ok "Pushed $IMG/est-web:$TAG"
echo
echo "Verify: gcloud artifacts docker images list $IMG   (both images with tag $TAG)"
