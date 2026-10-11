# Google Cloud setup — sign-in and calendar linking

The API uses one Google OAuth 2.0 **Web application** client for both purposes:

| Purpose | Flow | Scopes |
|---|---|---|
| Sign in to EST | Google Identity Services in the browser → ID token → `POST /api/v1/auth/google` | `openid email profile` |
| Link a Google Calendar ("partner account") | Authorization code + PKCE, server-side exchange, offline access → `POST /api/v1/calendar/identities/link/start` | `https://www.googleapis.com/auth/calendar.readonly` `https://www.googleapis.com/auth/calendar.events.readonly` |

The `local` profile never calls Google (`est.google.mock=true`); you only need this for `qa`/`prod`.

## 1. Project and API

1. <https://console.cloud.google.com/> → create project **est-enablement** (or reuse one).
2. **APIs & Services → Library** → enable **Google Calendar API**.
3. (Optional) enable **People API** if you later want richer profile data; not required.

## 2. OAuth consent screen

1. **APIs & Services → OAuth consent screen**.
2. User type: **Internal** if everyone signing in belongs to your Google Workspace organisation
   (recommended — no verification needed). Otherwise **External** and keep the app in **Testing**
   with the engineers' addresses listed as *test users* (max 100) until you request verification.
3. App name `Enablement Session Tracker`, support email, developer contact.
4. **Scopes**: add `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`,
   `.../auth/calendar.readonly`, `.../auth/calendar.events.readonly`.
   The calendar scopes are *sensitive*; for External apps Google requires verification before
   anyone outside the test-user list can grant them.

## 3. OAuth client

1. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type **Web application**, name `est-api`.
3. **Authorised JavaScript origins** (where the sign-in button runs):
   - `http://localhost:3100` (local web)
   - your QA/prod web origin(s)
4. **Authorised redirect URIs** (where Google sends the calendar-link authorization code):
   - `http://localhost:8080/api/v1/calendar/identities/link/callback`
   - `https://<qa-api-host>/api/v1/calendar/identities/link/callback`
5. Copy the **Client ID** and **Client secret**.

## 4. Environment

```
GOOGLE_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-...
GOOGLE_REDIRECT_URI=http://localhost:8080/api/v1/calendar/identities/link/callback
EST_ALLOWED_DOMAINS=cognition.ai            # comma-separated; sign-in rejected for other domains
EST_BOOTSTRAP_ADMIN_EMAILS=dana@cognition.ai # first admins; everyone else starts PENDING
```

Generate the remaining secrets with `infra/scripts/gen-keys.sh >> .env`. The client secret is only
ever used server-side for the code exchange; the browser sees the client ID alone.

## 5. Verify

1. `docker compose -f infra/compose.yaml --profile qa up --build`
2. Open the web app, click **Sign in with Google** with an allow-listed account → you should land on
   `/pending` until an admin approves you (bootstrap admins are active immediately).
3. **Settings → Linked calendars → Add Google account** → consent → back in the app the identity
   shows *ACTIVE* and the first sync run appears.
4. `GET /api/v1/calendar/identities` returns no token material — only status and timestamps.

## Security notes

- Refresh tokens from Google are stored AES-256-GCM encrypted with `EST_CRYPTO_KEY`; rotate the key
  by re-encrypting (`backend` ships a `rotate-crypto-key` task in Phase 4).
- Unlinking an identity calls Google's revoke endpoint before deleting the row.
- If a token exchange returns `invalid_grant`, the identity flips to `NEEDS_REAUTH`; sync skips it
  and the UI shows a re-link prompt. Nothing is retried blindly.
