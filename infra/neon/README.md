# Neon track registry

EST uses separate Neon projects so real production feedback cannot be changed by sandbox
experiments.

| Track | Project | Project ID | Default branch |
|---|---|---|---|
| Production | `Codev-EST-prod` | `fragrant-cloud-37068465` | `production` |
| Sandbox | `Codev-EST-sandbox` | `flat-breeze-15814484` | `sandbox` |
| Archive | `Codev-EST-archive` | `dry-cloud-96538248` | `archive` |

Project IDs are identifiers, not credentials. Connection strings stay in ignored local env files or
the deployment secret store.

## Local environment files

The current checkout uses:

```text
.env.local          production
.env.sandbox.local  sandbox
.env.archive.local  archive administration only
```

All three names are covered by the repository's `.env.*` ignore rule.

The Devin App launcher accepts the desired file:

```bash
EST_ENV_FILE="$PWD/.env.sandbox.local" \
EST_TRACK=sandbox \
EST_CALENDAR_SYNC_ENABLED=false \
npm start
```

The `sandbox` profile uses real Google sign-in, so its deployment still needs the normal
`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. Calendar linking remains disabled.

## Track rules

### Production

- Owns real Google calendar refresh tokens and sync cursors.
- Owns authoritative candidates, suggestions, sessions, feedback, catalogs, and reports.
- Is the only user-facing track allowed to link or directly synchronize calendars.

### Sandbox

- Uses real Google sign-in, but does not link calendars or store Google calendar refresh tokens.
- Receives canonical calendar evidence through the external distributor.
- Owns all experimental decisions, sessions, feedback, catalogs, hashtags, and reports.
- Never writes back to production.

### Archive

- Has no EST web/API deployment.
- Receives only versioned, redacted, checksum-verified evidence from the archive job.
- Is not in the normal request path.

## Calendar distributor contract

The external scheduled design fetches Google once using production credentials, then independently
delivers a versioned evidence envelope to production and sandbox. Each target records its own
outcome so one failure does not roll back the other.

Cross-track identity uses Google subject and normalized email. Database IDs remain local to each
track.

The distributor may copy:

- Organization and member identity keys needed to resolve the owner.
- Tokenless identity metadata.
- Calendar event evidence and attendees.
- Sync and replication metadata.
- A fresh candidate/suggestion baseline when sandbox has no local state for the event.

The current distributor deliberately omits encrypted tokens, sync cursors, provider error text, and
the raw provider JSON payload.

It must not copy:

- OAuth refresh or access tokens.
- Refresh-token families.
- Production sessions or feedback.
- Candidate or suggestion decisions.
- Catalog or hashtag edits.
- Audit records.

## Commands

Apply the archive-only schema:

```bash
infra/neon/migrate-archive.sh
```

Preview a production-to-sandbox replication:

```bash
infra/neon/replicate-calendar-evidence.sh
```

Apply it:

```bash
EST_REPLICATION_DRY_RUN=false infra/neon/replicate-calendar-evidence.sh
```

The default evidence window includes events ending in the previous 30 days or later. Override it
with `EST_REPLICATION_LOOKBACK_DAYS`.

## Migrations

- Production and sandbox use the normal EST Flyway migrations.
- Archive uses archive-only Flyway migrations.
- Application traffic uses pooled connections.
- Flyway, dumps, and replication use direct connections.
- Test every migration on an isolated Neon branch before applying it to production.

## Current state

- Production was renamed from `Codev-EST` to `Codev-EST-prod`.
- Sandbox and archive projects have been created in `aws-us-east-2` on PostgreSQL 18.
- The normal EST Flyway schema has been applied to sandbox.
- The archive-only Flyway schema has been applied.
- `replicate-calendar-evidence.sh` now performs an idempotent production-to-sandbox copy of the
  current evidence window, tokenless identities, reference rows, and untouched candidate baselines.
- The distributor has been run twice against the provisioned projects. The second run inserted no
  duplicate candidates, suggestions, or reference rows.
- Scheduling the distributor alongside the external Google sync and adding its replication ledger
  remain implementation phases in `PLAN.md`.
