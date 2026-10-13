# AGENTS.md — Enablement Session Tracker (monorepo)

```
web/        Next.js 16 app (pnpm)            — see web/AGENTS.md for frontend conventions
backend/    Spring Boot 4.1 API, Java 25     — Gradle 9.8 wrapper, profiles local|qa|prod
infra/      Dockerfile.api, compose.yaml, runbooks, scripts
contracts/  openapi.yaml (API v1, source of truth), ai-fixtures/ (golden tests for the AI port)
```

## Toolchain (this machine uses sdkman)

```bash
source ~/.sdkman/bin/sdkman-init.sh && sdk env     # picks java 25.0.4-tem + gradle 9.8.1 from backend/.sdkmanrc
cd web && pnpm install
```

## Verify before finishing any change

```bash
make verify            # web: lint, typecheck, vitest, build · backend: spotlessCheck + tests on H2
make verify-api        # backend only
EST_RUN_CONTAINER_TESTS=1 backend/gradlew -p backend test --tests '*ContainersIT'   # Flyway on real PostgreSQL (Docker)
make up-local          # compose: API (H2, seeded, mock Google) + web on :3100
make up-qa             # compose: API (qa) + PostgreSQL 17; needs .env (infra/scripts/gen-keys.sh >> .env)
cd web && pnpm e2e     # Playwright; starts the API (local profile) and the web dev server itself if not running
```

## Conventions that matter

- **Contract first.** Change `contracts/openapi.yaml` before changing a controller. The web reads one
  `/snapshot` (`web/lib/domain/state.ts` maps wire shapes: `roleId`→`role` on contacts/people) and keeps
  its pure selectors; every write in `web/lib/domain/store.ts` calls the endpoint and reloads the snapshot.
- **Web auth**: access token in memory only (`web/lib/api/client.ts`), refresh via httpOnly cookie +
  `X-CSRF` (nonce also returned in the token body and kept in localStorage — it is not a secret). Public
  routes: `/sign-in`, `/pending`. `GET /api/v1/features` tells the sign-in page about dev mode and the
  Google client id.
- **Three layers never mix** (plan §14): `calendar_events` are evidence written only by sync;
  `suggestions` are AI proposals that require a decision; `sessions` are member-owned with per-field
  `provenance`. Sync and AI may never overwrite a `manual` field.
- **No sessions on the server.** Access JWTs (RS256, 15 min) are validated statelessly; refresh
  tokens live in an httpOnly cookie and only their hashes are stored (`refresh_token_families`).
  Never log or return tokens, Google credentials or `refresh_token_enc`.
- **Org scoping everywhere.** Every query filters by the caller's `org` claim; cross-org access is a 404.
- **Flyway only.** Schema changes are new `V<n>__*.sql` files under `db/migration/common`; put
  vendor-specific SQL in `h2/` or `postgresql/` with the same version number. `ddl-auto=validate`.
  Avoid reserved words as column names (`value` broke H2 — use `value_json`).
- **Spring Boot 4 / Security 7 / Jackson 3** (`tools.jackson.*`): starters are modular
  (`spring-boot-starter-webmvc`, `-security-oauth2-resource-server`, `-flyway`, `…-test` companions).
  Read `docs.spring.io/spring-boot/4.1` before assuming 3.x APIs. Testcontainers is 2.x
  (`org.testcontainers:testcontainers-postgresql`, `org.testcontainers.postgresql.PostgreSQLContainer`, no generics).
- **AI engine** (`backend …/ai`, Phase 1D) must stay framework-free (ArchUnit rule) and deterministic;
  parity with `web/lib/ai` is proven by golden fixtures in `contracts/ai-fixtures/`.
- **Formatting**: `./gradlew spotlessApply` (google-java-format 1.27 AOSP). Web: eslint/prettier via `pnpm lint`.
- **Secrets** only via environment / `.env` (gitignored). `.env.example` documents every variable; the production
  mapping (Cloud Run env + Secret Manager names, Supabase session pooler) is in `infra/runbooks/deploy-gcp-supabase.html`.
- **Tests must not assume the weekday**: mock fixtures are week-relative except the two Mercedes events pinned to the
  real current day; assert containment on Today, not exact lists.

## Security rules (see infra/runbooks/security.md)

- Never return or log `refresh_token_enc`, Google tokens, ID tokens or our refresh tokens; `HardeningTest`
  scans every read endpoint. New read models must keep that test green.
- New unauthenticated or expensive endpoints get a rule in `RateLimitFilter`.
- Anything under `/api/v1/dev/**` must be guarded by `est.dev.endpoints-enabled`.

## Parallel work (Devin local)

One git worktree per session (`git worktree add ../EST-<track> <branch>`); fixed ports per track
(web 3100/3200/3300, api 8080/8081/8082, postgres 5432/5433); at most three concurrent sessions;
Playwright in only one session at a time; backend builds in secondary worktrees use `--no-daemon`.
Merge order for Phase 1: 1D (AI port) → 1A (domain/API) → 1B (auth).
