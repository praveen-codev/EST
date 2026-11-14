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

The launcher reads Neon's generated `.env.local` by default. `DATABASE_URL` is used for normal
application traffic through the pooled endpoint, while `DATABASE_URL_UNPOOLED` is used by Flyway.
Set `EST_ENV_FILE` to load another env file, or provide `EST_DATABASE_URL` and
`EST_DATABASE_URL_UNPOOLED` directly.

The launcher generates persistent JWT and encryption keys under
`~/.local/share/est-devin-app`. Set `EST_STATE_DIR` to move that state.

This profile intentionally enables `/api/v1/dev/*`, including persona sign-in, scenario selection,
and demo reset. Use it only for the organization-internal dry run.
