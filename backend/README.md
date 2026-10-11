# est-api — Enablement Session Tracker backend

Spring Boot 4.1.1 · Java 25 · Gradle 9.8.1 · Flyway · H2 (local) / PostgreSQL 17 (qa)

```bash
source ~/.sdkman/bin/sdkman-init.sh && sdk env          # java 25.0.4-tem, gradle 9.8.1
./gradlew bootRun --args='--spring.profiles.active=local'
open http://localhost:8080/swagger-ui.html
```

## Profiles

| Profile | Database | Google | Dev endpoints | Notes |
|---|---|---|---|---|
| `local` | H2 in-memory, `MODE=PostgreSQL`, Flyway + `LocalSeedRunner` | mocked | `/api/v1/dev/**` | ephemeral RS256 key pair generated on start; H2 console at `/h2-console` |
| `qa` | PostgreSQL (`SPRING_DATASOURCE_*`) | real | off | refuses to start without `EST_JWT_PRIVATE_KEY`/`EST_JWT_PUBLIC_KEY`/`EST_CRYPTO_KEY` |
| `prod` | PostgreSQL | real | off | placeholder; everything from env |

## Try it (local)

```bash
TOKEN=$(curl -s -X POST 'http://localhost:8080/api/v1/dev/token?email=alex@cognition.ai' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/catalog/sessionType | jq .
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/calendar/sync | jq .   # mock Google: 21 events
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/today | jq .                   # two Mercedes sessions
```

## Layout

```
com.cognition.est
├── EstApiApplication
├── app/config      EstProperties (all settings, env-bound)
├── app/security    SecurityConfig (stateless resource server, CORS, headers), JwtKeyConfig (RS256 keys)
├── app/web         ApiError, GlobalExceptionHandler
├── app/dev         LocalSeedRunner, DevController (local profile only: token, login-as, reset, scenario)
├── auth            Google ID-token verifier (+ mock), AuthService (allow-list, bootstrap admins, pending), TokenService (RS256 access + rotating refresh families), AuthController, cookies
├── audit           append-only audit events
├── ai              deterministic engine port (normalize -> redact -> classify -> extract); golden-tested against web/lib/ai
├── calendar        CalendarProvider, MockCalendarProvider (fixtures), EventFingerprint (hash/dedupe), SyncService
├── domain/model    records mirroring the OpenAPI schemas
├── domain/repo     JdbcClient repositories (no JPA: explicit SQL over the Flyway schema)
├── domain/service  SessionOps (state transitions from ops.ts), Views (today/inbox/timeline/detail), CatalogService
└── api             REST controllers
src/main/resources/db/migration/{common,h2,postgresql,local}
```

## Tests

```bash
./gradlew check                                        # H2 smoke + ArchUnit + spotless
EST_RUN_CONTAINER_TESTS=1 ./gradlew test --tests '*ContainersIT'   # real PostgreSQL via Testcontainers
```
