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
EST_DATABASE_URL='postgresql://…' \
EST_WEB_ORIGINS='https://your-app-url' \
PORT=3000 \
infra/devin-app/start.sh
```

The launcher generates persistent JWT and encryption keys under
`~/.local/share/est-devin-app`. Set `EST_STATE_DIR` to move that state.

This profile intentionally enables `/api/v1/dev/*`, including persona sign-in, scenario selection,
and demo reset. Use it only for the organization-internal dry run.
