#!/usr/bin/env bash
# Step 9: deploy the web app to Cloud Run.
source "$(dirname "$0")/common.sh"
require PROJECT_ID REGION
TAG="${1:-$(cat "$STATE_DIR/last-tag" 2>/dev/null || true)}"; [ -n "$TAG" ] || fail "No image tag. Run 03-build-push.sh first."
gcloud config set project "$PROJECT_ID" >/dev/null
info "Deploying est-web:$TAG…"
gcloud run deploy est-web --image "$IMG/est-web:$TAG" --region "$REGION" --platform managed \
  --allow-unauthenticated --port 3000 --cpu 1 --memory 512Mi --min-instances 0 --max-instances 3 --quiet >/dev/null
URL="$(gcloud run services describe est-web --region "$REGION" --format='value(status.url)')"
echo "$URL" > "$STATE_DIR/web-url"
if [ "$DOMAIN_MODE" = none ]; then
  curl -fs -o /dev/null "$URL/sign-in" && ok "Web is serving at $URL — this is your app's address."
  echo; echo "Next: infra/deploy/04-deploy-api.sh   (tells the API about this address), then add it to the Google OAuth client:"
  echo "  Authorised JavaScript origin:  $URL"
  echo "  Authorised redirect URI:       $URL/api/v1/calendar/identities/link/callback"
else
  curl -fs -o /dev/null "$URL/sign-in" && ok "Web is serving. Temporary address: $URL (sign-in will only work once Step 10 domains are live)"
fi
