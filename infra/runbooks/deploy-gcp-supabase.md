# First deployment — Google Cloud Run + Supabase Postgres

End state:

```
https://app.<your-domain>   → Cloud Run service  est-web   (Next.js, infra/Dockerfile.web)
https://api.<your-domain>   → Cloud Run service  est-api   (Spring Boot, infra/Dockerfile.api, profile prod)
                                     └── Supabase Postgres (session pooler, schema `est`)
Google OAuth client (Web)   → sign-in (ID token) + calendar linking (code + PKCE)
Secret Manager              → every secret; Cloud Run reads them as env vars
```

> **Hard requirement: one registrable domain for web and API** (e.g. `app.acme.com` + `api.acme.com`).
> The refresh-token cookie is `SameSite=Strict`; browsers only send it when the web app and the API are
> on the *same site*. `*.run.app` + `*.vercel.app` will **not** work. Bring a domain you control.

Time: ~60–90 minutes the first time. Everything below is idempotent; re-running a step is safe.

---

## 0. Tools and variables

```bash
brew install --cask google-cloud-sdk        # or https://cloud.google.com/sdk/docs/install
gcloud auth login
gcloud components install beta

# Fill these once and keep the shell open (or put them in a local, gitignored file and `source` it).
export PROJECT_ID=est-prod                  # new or existing GCP project id
export REGION=europe-west1                  # pick the Cloud Run region closest to your users
export DOMAIN=acme.com                      # the registrable domain you control
export WEB_HOST=app.$DOMAIN
export API_HOST=api.$DOMAIN
export ALLOWED_DOMAINS=acme.com             # comma-separated email domains allowed to sign in
export BOOTSTRAP_ADMINS=you@acme.com        # comma-separated; become ADMIN + ACTIVE on first sign-in
```

---

## 1. Google Cloud project

```bash
gcloud projects create $PROJECT_ID --name="Enablement Session Tracker"   # skip if it exists
gcloud config set project $PROJECT_ID
gcloud billing accounts list                                              # then link one:
gcloud billing projects link $PROJECT_ID --billing-account=XXXXXX-XXXXXX-XXXXXX

gcloud services enable \
  run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com \
  cloudbuild.googleapis.com calendar-json.googleapis.com iamcredentials.googleapis.com

gcloud artifacts repositories create est --repository-format=docker --location=$REGION --description="EST images"
gcloud auth configure-docker $REGION-docker.pkg.dev
```

---

## 2. Supabase (database only — no Supabase Auth, no Data API)

The API uses Supabase purely as managed PostgreSQL. None of the Supabase **anon / service-role keys**
are used anywhere; do not put them in the app.

1. <https://supabase.com/dashboard> → **New project**: name `est`, **region closest to `$REGION`**,
   generate a strong **Database password** → save it, you need it below (`SUPABASE_DB_PASSWORD`).
2. **Project Settings → Database → Connection string → "Session pooler"** (not Direct, not
   Transaction). Cloud Run egress is IPv4; the direct host is IPv6-only on the free tier, and the
   transaction pooler (port 6543) breaks JDBC prepared statements. Note the values:
   - host: `aws-0-<supabase-region>.pooler.supabase.com`
   - port: `5432`
   - database: `postgres`
   - user: `postgres.<project-ref>`
3. **Project Settings → API → Exposed schemas**: remove `public` (or leave it — the app keeps all its
   tables in a private schema `est`, which is *not* exposed by default; removing `public` is defence
   in depth).
4. **Authentication**: nothing to configure. **Row Level Security**: not used; our tables live in
   schema `est`, reached only by the API over JDBC.

```bash
export SUPABASE_PROJECT_REF=abcdefghijklmnop            # from the dashboard URL
export SUPABASE_POOLER_HOST=aws-0-eu-west-1.pooler.supabase.com
export SUPABASE_DB_PASSWORD='...'                        # the password from step 1
export SPRING_DATASOURCE_URL="jdbc:postgresql://$SUPABASE_POOLER_HOST:5432/postgres?sslmode=require&currentSchema=est"
export SPRING_DATASOURCE_USERNAME="postgres.$SUPABASE_PROJECT_REF"
```

Flyway creates the `est` schema on first start (`EST_DB_SCHEMA=est`, `spring.flyway.create-schemas=true`)
and runs `V1__baseline`, `V1_1__jsonb_columns` (PostgreSQL), `V2__reference_data`.

---

## 3. Google OAuth client (sign-in + calendar)

1. **APIs & Services → OAuth consent screen**
   - User type **Internal** if `$DOMAIN` is a Google Workspace organisation (no verification needed).
     Otherwise **External**, publishing status **Testing**, and add every engineer as a *test user*
     until Google verifies the app (sensitive scopes).
   - App name `Enablement Session Tracker`, support + developer emails, authorised domain `$DOMAIN`.
   - Scopes: `openid`, `…/auth/userinfo.email`, `…/auth/userinfo.profile`,
     `https://www.googleapis.com/auth/calendar.readonly`, `https://www.googleapis.com/auth/calendar.events.readonly`.
2. **APIs & Services → Credentials → Create credentials → OAuth client ID → Web application**, name `est-api`
   - Authorised JavaScript origins: `https://$WEB_HOST`
   - Authorised redirect URIs: `https://$API_HOST/api/v1/calendar/identities/link/callback`
3. Copy the client id and secret:

```bash
export GOOGLE_CLIENT_ID='1234567890-abc.apps.googleusercontent.com'
export GOOGLE_CLIENT_SECRET='GOCSPX-...'
```

---

## 4. Secrets → Secret Manager

Generate the app's own keys (RSA pair for access tokens, AES key for Google refresh tokens):

```bash
infra/scripts/gen-keys.sh > /tmp/est-keys.env && source /tmp/est-keys.env && shred -u /tmp/est-keys.env 2>/dev/null || rm -f /tmp/est-keys.env
# exports EST_JWT_PRIVATE_KEY, EST_JWT_PUBLIC_KEY, EST_CRYPTO_KEY (and an unused POSTGRES_PASSWORD)

put() { printf '%s' "$2" | gcloud secrets create "$1" --data-file=- --replication-policy=automatic 2>/dev/null \
     || printf '%s' "$2" | gcloud secrets versions add "$1" --data-file=-; }

put est-jwt-private-key   "$(printf '%b' "$EST_JWT_PRIVATE_KEY")"   # gen-keys emits \n escapes; %b restores newlines
put est-jwt-public-key    "$(printf '%b' "$EST_JWT_PUBLIC_KEY")"
put est-crypto-key        "$EST_CRYPTO_KEY"
put est-db-password       "$SUPABASE_DB_PASSWORD"
put est-google-client-secret "$GOOGLE_CLIENT_SECRET"
```

Grant the Cloud Run runtime identity read access:

```bash
gcloud iam service-accounts create est-api --display-name="EST API runtime"
export API_SA=est-api@$PROJECT_ID.iam.gserviceaccount.com
for s in est-jwt-private-key est-jwt-public-key est-crypto-key est-db-password est-google-client-secret; do
  gcloud secrets add-iam-policy-binding $s --member="serviceAccount:$API_SA" --role=roles/secretmanager.secretAccessor
done
```

---

## 5. Build and push images

```bash
export IMG=$REGION-docker.pkg.dev/$PROJECT_ID/est
export TAG=$(git rev-parse --short HEAD)

# API (Java 25, layered jar)
docker build --platform linux/amd64 -f infra/Dockerfile.api -t $IMG/est-api:$TAG .
docker push $IMG/est-api:$TAG

# Web — NEXT_PUBLIC_* are compiled into the bundle, so the API URL is a build arg
docker build --platform linux/amd64 -f infra/Dockerfile.web \
  --build-arg NEXT_PUBLIC_API_URL=https://$API_HOST \
  --build-arg NEXT_PUBLIC_GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID \
  -t $IMG/est-web:$TAG .
docker push $IMG/est-web:$TAG
```

(Alternative without local Docker: `gcloud builds submit --tag $IMG/est-api:$TAG --file infra/Dockerfile.api .`)

---

## 6. Deploy the API

```bash
gcloud run deploy est-api \
  --image $IMG/est-api:$TAG --region $REGION --platform managed \
  --service-account $API_SA \
  --allow-unauthenticated \
  --port 8080 --cpu 1 --memory 1Gi --cpu-boost \
  --min-instances 1 --max-instances 1 \
  --set-env-vars "SPRING_PROFILES_ACTIVE=prod" \
  --set-env-vars "SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL" \
  --set-env-vars "SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME" \
  --set-env-vars "EST_DB_SCHEMA=est,EST_DB_POOL_SIZE=5" \
  --set-env-vars "EST_WEB_ORIGINS=https://$WEB_HOST" \
  --set-env-vars "EST_ALLOWED_DOMAINS=$ALLOWED_DOMAINS,EST_BOOTSTRAP_ADMIN_EMAILS=$BOOTSTRAP_ADMINS" \
  --set-env-vars "EST_JWT_ISSUER=https://$API_HOST,EST_COOKIE_SECURE=true" \
  --set-env-vars "GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID,GOOGLE_REDIRECT_URI=https://$API_HOST/api/v1/calendar/identities/link/callback" \
  --set-env-vars "EST_SYNC_ENABLED=true,EST_SYNC_INTERVAL=PT15M" \
  --set-secrets "SPRING_DATASOURCE_PASSWORD=est-db-password:latest" \
  --set-secrets "EST_JWT_PRIVATE_KEY=est-jwt-private-key:latest,EST_JWT_PUBLIC_KEY=est-jwt-public-key:latest" \
  --set-secrets "EST_CRYPTO_KEY=est-crypto-key:latest,GOOGLE_CLIENT_SECRET=est-google-client-secret:latest"
```

Why `--min-instances 1 --max-instances 1`: the calendar scheduler and the rate-limit buckets are
in-process. One always-on instance keeps the 15-minute sync running and avoids duplicate syncs.
(Scale out later only after adding ShedLock + a shared rate-limit store — see `security.md`.)

Check:

```bash
export API_URL=$(gcloud run services describe est-api --region $REGION --format='value(status.url)')
curl -s $API_URL/actuator/health            # {"status":"UP"}
curl -s $API_URL/api/v1/features            # {"devMode":false,"mockGoogle":false,"googleClientId":"..."}
gcloud run services logs read est-api --region $REGION --limit 50 | grep -i -E "flyway|Started|ERROR"
```

Flyway output should show `Successfully applied 3 migrations to schema "est"`.

---

## 7. Deploy the web app

```bash
gcloud run deploy est-web \
  --image $IMG/est-web:$TAG --region $REGION --platform managed \
  --allow-unauthenticated --port 3000 --cpu 1 --memory 512Mi --min-instances 0 --max-instances 3
```

---

## 8. Custom domains (the same-site requirement)

```bash
gcloud beta run domain-mappings create --service est-api --domain $API_HOST --region $REGION
gcloud beta run domain-mappings create --service est-web --domain $WEB_HOST --region $REGION
gcloud beta run domain-mappings describe --domain $API_HOST --region $REGION --format='value(status.resourceRecords)'
```

Add the printed DNS records (usually a `CNAME` to `ghs.googlehosted.com.`) at your DNS provider for
both hosts. Certificates are provisioned automatically once DNS resolves (5–30 min).

If your region doesn't support domain mappings, put a global external HTTPS load balancer with two
serverless NEGs in front (same two hostnames) — the app needs nothing else.

---

## 9. First sign-in and smoke test

1. Open `https://$WEB_HOST` → **Sign in with Google** with one of `$BOOTSTRAP_ADMINS`. You land on
   Today as an ADMIN. Any other allow-listed colleague lands on `/pending` until you approve them in
   **Admin** (or `PATCH /api/v1/admin/members/{id}` with `{"status":"ACTIVE"}`).
2. Account menu → **Linked calendars → Add Google account** → consent → you are redirected back
   with `?linked=` and the first import has run. Add a second Google account the same way.
3. **Sync calendar** in the top bar: repeat it — counts show updated/unchanged, never duplicates.
4. Verify nothing sensitive is on the wire:
   `curl -s -H "Authorization: Bearer <token>" https://$API_HOST/api/v1/snapshot | grep -c refresh_token` → `0`.

---

## Environment variable reference

### API (`est-api`, Cloud Run env + secrets)

| Variable | Source | Value / notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | env | `prod` |
| `PORT` | set by Cloud Run | `8080` (the app honours it) |
| `SPRING_DATASOURCE_URL` | env | `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require&currentSchema=est` |
| `SPRING_DATASOURCE_USERNAME` | env | `postgres.<supabase-project-ref>` |
| `SPRING_DATASOURCE_PASSWORD` | **secret** `est-db-password` | Supabase database password |
| `EST_DB_SCHEMA` | env | `est` — Flyway creates and migrates this schema |
| `EST_DB_POOL_SIZE` | env | `5` (Supabase free tier allows ~15 pooler connections; keep API instances × pool ≤ that) |
| `EST_WEB_ORIGINS` | env | `https://app.<domain>` (comma-separated for several) — CORS allow-list |
| `EST_ALLOWED_DOMAINS` | env | email domains allowed to sign in, comma-separated; empty = any verified Google account |
| `EST_BOOTSTRAP_ADMIN_EMAILS` | env | emails that become ADMIN/ACTIVE on first sign-in |
| `EST_JWT_ISSUER` | env | `https://api.<domain>` (any stable string; validated on every access token) |
| `EST_JWT_ACCESS_TTL` | env (optional) | ISO-8601 duration, default `PT15M` |
| `EST_JWT_REFRESH_TTL` | env (optional) | default `P14D` |
| `EST_JWT_PRIVATE_KEY` | **secret** `est-jwt-private-key` | PEM PKCS#8 RSA 2048 (from `gen-keys.sh`) |
| `EST_JWT_PUBLIC_KEY` | **secret** `est-jwt-public-key` | PEM X.509 public key |
| `EST_COOKIE_SECURE` | env | `true` (only the local profile uses `false`) |
| `EST_CRYPTO_KEY` | **secret** `est-crypto-key` | base64 of 32 random bytes — AES-256-GCM for Google refresh tokens |
| `GOOGLE_CLIENT_ID` | env | OAuth Web client id (public) |
| `GOOGLE_CLIENT_SECRET` | **secret** `est-google-client-secret` | OAuth Web client secret |
| `GOOGLE_REDIRECT_URI` | env | `https://api.<domain>/api/v1/calendar/identities/link/callback` — must match the OAuth client exactly |
| `EST_SYNC_ENABLED` | env | `true` |
| `EST_SYNC_INTERVAL` | env (optional) | default `PT15M` |
| `EST_SWAGGER_ENABLED` | env (qa only) | Swagger UI is off in `prod` regardless |

### Web (`est-web`, **build args** — compiled into the bundle)

| Variable | Value |
|---|---|
| `NEXT_PUBLIC_API_URL` | `https://api.<domain>` |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | optional; the sign-in page reads the client id from `GET /api/v1/features` anyway |

### Secret Manager names (what the commands above create)

`est-jwt-private-key`, `est-jwt-public-key`, `est-crypto-key`, `est-db-password`, `est-google-client-secret`

### Never configure
Supabase `anon` / `service_role` keys, Supabase Auth, Supabase JWT secret — unused. `EST_DEV_*` /
`est.dev.endpoints-enabled` — stays `false` outside the local profile (the `prod` profile never enables it).

---

## Upgrades, rotation, rollback

```bash
# New version: rebuild with a new TAG (step 5), redeploy (steps 6–7). Flyway applies new migrations on start.
# Rollback the API: gcloud run services update-traffic est-api --region $REGION --to-revisions <previous>=100
# Rotate the JWT keys: add new secret versions, redeploy; tokens signed by the old key expire within 15 min.
# Rotate EST_CRYPTO_KEY: requires re-encrypting member_identities.refresh_token_enc (else users re-link calendars).
# Rotate the Supabase password: Supabase dashboard → reset → `gcloud secrets versions add est-db-password` → redeploy.
```

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| API won't start: `EST_JWT_PRIVATE_KEY / EST_JWT_PUBLIC_KEY must be set` | secret not mounted / wrong secret name; check `--set-secrets` and IAM `secretAccessor` |
| `GOOGLE_CLIENT_ID must be set when est.google.mock=false` | env missing on `est-api` |
| Flyway: `connection refused` / timeout | using the Direct host (IPv6) instead of the **Session pooler**; or wrong region host |
| Flyway: `prepared statement "S_1" already exists` | you used the **Transaction pooler** (port 6543); switch to session pooler 5432 |
| Sign-in 401 "could not be verified" | web and API use different Google client ids, or the JS origin isn't authorised |
| Sign-in works, but reload sends you back to sign-in | web and API are not on the same site → refresh cookie not sent (step 8) |
| Calendar link: `redirect_uri_mismatch` | `GOOGLE_REDIRECT_URI` ≠ the URI in the OAuth client (scheme/host/path must match exactly) |
| Calendar link: `access_denied` for a colleague | External consent screen in Testing: add them as a test user, or publish after verification |
| Sync status `NEEDS_REAUTH` | Google refresh token revoked/expired → user re-links the calendar |
