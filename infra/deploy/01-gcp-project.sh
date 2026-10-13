#!/usr/bin/env bash
# Step 3: select the project, turn on the Google services EST needs, create the image repository and runtime identity.
source "$(dirname "$0")/common.sh"
require PROJECT_ID REGION
gcloud config set project "$PROJECT_ID" >/dev/null
BILLING="$(gcloud billing projects describe "$PROJECT_ID" --format='value(billingEnabled)' 2>/dev/null || echo false)"
[ "$BILLING" = "True" ] || fail "Billing is not enabled on $PROJECT_ID. In the console: Billing → Link a billing account, then re-run."
info "Enabling services (1–2 minutes)…"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com \
  cloudbuild.googleapis.com calendar-json.googleapis.com iamcredentials.googleapis.com >/dev/null
ok "Services enabled"
gcloud artifacts repositories describe est --location="$REGION" >/dev/null 2>&1 \
  || gcloud artifacts repositories create est --repository-format=docker --location="$REGION" --description="EST images" >/dev/null
gcloud artifacts repositories set-cleanup-policies est --location="$REGION" --policy="$REPO_ROOT/infra/deploy/cleanup-policy.json" --no-dry-run --quiet >/dev/null 2>&1 || true
ok "Image repository: $IMG (keeps the 3 newest images per service, deletes older than 30 days — stays inside the free 0.5 GB)"
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet >/dev/null
ok "Docker can push to Artifact Registry"
gcloud iam service-accounts describe "$API_SA" >/dev/null 2>&1 \
  || gcloud iam service-accounts create est-api --display-name="EST API runtime" >/dev/null
ok "Runtime identity: $API_SA"
echo
echo "Verify in the browser: https://console.cloud.google.com/artifacts?project=$PROJECT_ID  → repository 'est' is listed."
