# Security design and threat model

## Principles

1. **No server-side sessions.** Access tokens are short-lived RS256 JWTs validated statelessly. The
   only server state about a login is a *refresh-token family*: SHA-256 hashes of the current and
   previous refresh token, rotation count, expiry and revocation timestamps. Nothing recoverable.
2. **Google tokens never reach the browser.** Sign-in exchanges a Google *ID token* for our own
   tokens; calendar access uses an *authorization-code + PKCE* flow completed server-side. Google
   refresh tokens are AES-256-GCM encrypted at rest (`EST_CRYPTO_KEY`) and decrypted only inside
   `GoogleCalendarProvider`. No read endpoint ever serialises them (`HardeningTest`).
3. **Three layers never mix.** Calendar sync writes evidence; the engine writes proposals; only a
   member's decision writes the authoritative record. A field with `provenance=manual` is never
   overwritten by sync or re-analysis.
4. **Org scoping everywhere.** Every repository query is bound to the caller's `org` claim; a
   foreign id is a 404, never a 403 that would confirm existence.

## Controls (where they live)

| Threat (STRIDE) | Control | Code |
|---|---|---|
| Spoofed Google identity | Signature via Google JWKS, `aud` = our client id, `iss`, expiry, `email_verified`; domain allow-list; new accounts start `PENDING` until an admin approves | `auth/GoogleIdTokenVerifierImpl`, `auth/AuthService` |
| Stolen access token | 15-minute TTL, RS256, held in SPA memory only (never localStorage) | `auth/TokenService`, `web/lib/api/client.ts` |
| Stolen / replayed refresh token | httpOnly, `SameSite=Strict`, `Secure` (outside local), path-scoped cookie; rotation on every use; **reuse of a rotated token revokes the whole family**; 14-day family expiry; hourly purge | `auth/TokenService`, `auth/AuthCookies`, `auth/TokenHousekeeping` |
| CSRF on cookie endpoints | Double-submit: `X-CSRF` header must equal the `est_csrf` cookie (constant-time compare); bearer endpoints are CSRF-immune | `auth/AuthCookies.csrfOk` |
| Authorization-code interception | PKCE S256; `state` is random, stored hashed, bound to the member, expires in 10 min, single use | `calendar/IdentityService` |
| Credential theft at rest | AES-256-GCM with a random 96-bit IV per value; key only from the environment; `qa`/`prod` refuse to start without `EST_JWT_*` / `EST_CRYPTO_KEY` | `app/crypto/TokenCrypto`, `app/security/JwtKeyConfig` |
| Brute force / abuse | Token buckets on `/auth/*`, `/calendar/sync`, link endpoints, `/dev/*` (429 + `Retry-After`) | `app/security/RateLimitFilter` |
| Clickjacking / injection | `X-Frame-Options: DENY`, `CSP default-src 'none'`, `Referrer-Policy: no-referrer`, `nosniff`, HSTS (https), Permissions-Policy | `app/security/SecurityConfig` |
| Privilege escalation | `ROLE_ADMIN` from the token's `role` claim; admins cannot demote/suspend themselves; role or status change revokes all of the member's refresh families | `api/AdminController`, `api/CatalogController` |
| Prompt/evidence leakage | Links, passcodes, PINs and phone numbers are redacted before text reaches the engine; the redacted view is what the UI shows | `ai/Redactor` |
| Repudiation | Append-only `audit_events` for sign-in, member changes, identity link/unlink, candidate confirmation, suggestion decisions — never containing credentials | `audit/AuditRepository` |
| Lost evidence on unlink | Unlink revokes the Google token and disables the identity; imported events remain | `calendar/IdentityService.unlink` |
| Dev conveniences in production | `/api/v1/dev/**`, mock Google and persona sign-in exist only when `est.dev.endpoints-enabled=true` (local profile); Swagger UI off in `qa`/`prod` | `app/dev`, `application-*.yaml` |
| Supply chain | Pinned versions (`gradle/libs.versions.toml`, `pnpm-lock.yaml`); `pnpm audit --prod` clean; tooling-only packages are devDependencies | repo |

## Operational notes

- **Key rotation**: generate a new pair with `infra/scripts/gen-keys.sh`; deploy the new public key
  alongside the old one for one access-token TTL (15 min) before retiring the old private key.
  Rotating `EST_CRYPTO_KEY` requires re-encrypting `member_identities.refresh_token_enc`
  (one-off task; until then users re-link their calendars).
- **Multiple API nodes**: the rate-limit buckets and the sync scheduler guard are per process. Put a
  shared store (Redis/Bucket4j-distributed) behind the limiter and a DB lock (ShedLock) behind
  `SyncScheduler` before scaling horizontally.
- **Logs**: `qa`/`prod` emit ECS-structured JSON; the application never logs token values, ID
  tokens or `refresh_token_enc`.
- **Google scopes**: `calendar.readonly` and `calendar.events.readonly` are sensitive; keep the OAuth
  consent screen in *Testing* with named test users until verification is granted (see
  `google-cloud.md`).
