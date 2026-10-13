#!/usr/bin/env bash
# Step 2: confirm the tools are installed and you are logged in to Google Cloud.
source "$(dirname "$0")/common.sh"
need_tool gcloud "Install from https://cloud.google.com/sdk/docs/install (or: brew install --cask google-cloud-sdk)"
need_tool docker "Install Docker Desktop from https://www.docker.com/products/docker-desktop/"
need_tool git "Install Xcode command line tools: xcode-select --install"
need_tool openssl "Comes with macOS; on Linux: apt install openssl"
docker info >/dev/null 2>&1 || fail "Docker Desktop is installed but not running. Open it and wait for the whale icon to settle."
ACCOUNT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' 2>/dev/null || true)"
[ -n "$ACCOUNT" ] || fail "Not logged in to Google Cloud. Run:  gcloud auth login"
ok "gcloud $(gcloud version --format='value(core)' 2>/dev/null | head -1) — logged in as $ACCOUNT"
ok "docker $(docker version --format '{{.Server.Version}}') running"
require PROJECT_ID REGION ALLOWED_DOMAINS BOOTSTRAP_ADMINS
ok "Settings file $ENV_FILE has the basic values (DOMAIN_MODE=$DOMAIN_MODE)"
