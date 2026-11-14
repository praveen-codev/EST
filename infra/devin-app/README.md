# Devin App dry run

This profile runs EST as one organization-visible Devin App:

- Next.js serves the App and proxies `/api/*` to Spring Boot on localhost.
- Neon PostgreSQL stores the durable demo data.
- Mock Google and seeded personas replace Google OAuth for the dry run.
- Unattended calendar sync runs outside the App through the separate job described in the root README.

## Build

```bash
infra/devin-app/build.sh
```

## Start

```bash
EST_WEB_ORIGINS='https://your-app-url' \
PORT=3000 \
infra/devin-app/start.sh
```

The launcher reads `.env.sandbox.local` (the `Codev-EST-sandbox` project) by default; only
`SPRING_PROFILES_ACTIVE=prod` defaults to the production `.env.local`. `DATABASE_URL` is used for
normal application traffic through the pooled endpoint, while `DATABASE_URL_UNPOOLED` is used by
Flyway. Set `EST_ENV_FILE` to load another env file, or provide `EST_DATABASE_URL` and
`EST_DATABASE_URL_UNPOOLED` directly.

The default Spring profile is `devin-app` (sandbox track, mock Google, `/api/v1/dev/*` persona
sign-in, scenario selection and demo reset). It can never reach production: the launcher refuses
any non-prod profile whose database host matches `.env.local` (`EST_PRODUCTION_ENV_FILE`), the API
refuses dev endpoints or mock Google on the prod track, and every database records the track that
first opened it in `deployment_track` and rejects any other track afterwards.

A real-account sandbox uses:

```bash
SPRING_PROFILES_ACTIVE=sandbox \
infra/devin-app/start.sh
```

Production uses `SPRING_PROFILES_ACTIVE=prod` (reads `.env.local`).

The launcher generates persistent JWT and encryption keys under
`~/.local/share/est-<profile>`. Set `EST_STATE_DIR` to move that state.
