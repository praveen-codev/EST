# Enablement Session Tracker — rapid prototype

A clickable MVP whose core job is **entering enablement-session feedback with almost no typing**. The home
page lists only *today's* sessions for the signed-in Applied AI Engineer with one CTA — *Enter feedback* — that
opens a form already filled from the calendar invite **and** from sessions teammates logged with the same client.
Everything runs in the browser — no sign-in, no server, no API keys.

```bash
cd web && pnpm install && pnpm dev --port 3100      # web app (in-browser mock backend for now)
make api                                            # Spring Boot API, local profile (H2 + seed + mock Google) on :8080
make up-local                                       # or everything in Docker
```

> **Monorepo**: `web/` (Next.js), `backend/` (Spring Boot 4.1 / Java 25 / Gradle 9.8), `infra/`
> (Docker, compose, runbooks), `contracts/` (OpenAPI v1). Phase 0 of the backend plan is in place:
> schema, profiles, Docker, contract. The web app still runs on its in-browser store until
> Phase 2E moves it onto the API. See `AGENTS.md` for the verification commands.

The first visit auto-runs the initial calendar lookback for the default persona (Alex), whose calendar has two
Mercedes-Benz sessions *today*. Use **Sync calendar**
in the top bar to pull the next incremental batch, the avatar menu to switch persona, and the flask icon
(bottom-left) for scenarios, "show AI reasoning" and **Reset demo**.

## What to look at

| Surface | Why it matters |
|---|---|
| **Today** `/` (home) | Only the current member's sessions scheduled today — if you have two Mercedes sessions, you see two cards. Each shows what the AI already knows and an **Enter feedback** CTA (confirming the calendar event on the way if needed). A footer link counts earlier sessions still missing feedback. |
| **Session feedback** `/sessions/:id` | Four steps. **1 · Basics** (client, team, format, facilitator, contacts) prefilled or offered as one-click accept/reject chips with evidence. **2 · Participants** — a number, plus known names from the invite. **3 · Use cases** — *Add use case* appends a card with title, description, a **use-case type** dropdown and **technology** toggles; the AI drafts the first card from the invite's stated goal. **4 · Session outcome** — one choice from the admin list. Summary/notes/audit are folded under "more". |
| **Capture inbox** `/inbox` | Only the team's own formats count: an event must match a **session format** from the admin table (101 session, Pair programming, 201 session, Ask me anything, Sprint in a day) to be *Likely*; generic workshops/kickoffs with a client are capped at *Needs a look*. Grouped into *Likely* / *Needs a look* / *Possible duplicates* / *skipped*. Each card shows what the AI proposes, its confidence, and "N of 11 fields prefilled". **Confirm** (or `Enter`) creates the session with every high-confidence value already decided. **Confirm all likely** (`⇧↵`) does a whole week in one click. Hover a chip for the evidence. `J`/`K`/`X`/`E` for keyboard triage. |
| **Timeline** `/timeline` | Group by day / week / client / member; filter by client, facilitator, tag, state, mine/everyone; candidates appear as ghost rows you can confirm from a side peek. |
| **Calendar** `/calendar` | Month and week views; confirmed sessions solid in the client's colour, candidates dashed. |
| **Admin** `/admin` | Five reference tables, one tabbed editor: **Session formats** (101 session, Pair programming, 201 session, Ask me anything, Sprint in a day — the only meetings that count), **Session outcomes** (Analysis done, Devin session, PR raised, Playbook / automation created), **Use-case types** (Excel to dashboard, ETL tool migration, Language migrations, Platform migrations, Devops work) **Technologies** (Java, Python, Ab Initio, Excel, …) and **Contact roles**. Add, rename, reorder, archive; keywords drive the AI. ADMIN role (Dana) edits, others read. |
| **⌘K** | Jump to any session or client, confirm all likely, sync, switch persona. `?` lists shortcuts. |

### Things worth demonstrating

- **Feedback in under a minute.** Home → *Enter feedback* on the Mercedes 101 → client, team, format, contacts and "12 participants" are already there; accept the drafted *Ab Initio → PySpark* use case (type *ETL tool migration*, technologies attached), add one more, tap *PR raised*. Back on Home the card reads *Feedback complete*.
- **Cross-user suggestions.** Alex has never run a session with Acme, yet the form proposes *Jane Doe · Product owner* and *Spring Boot* — evidence reads "Recorded on 3 Acme sessions by Priya". Switch to Priya to see her own day; the history is shared.
- **Sync never duplicates.** Press *Sync calendar* repeatedly: changed events refresh evidence and add new chips, unchanged ones are reported as such, and the forwarded copy of the Vantage workshop is caught as a duplicate.
- **Manual edits win.** Type a title in a session, sync again: the title stays, and the AI only adds suggestions for fields you haven't set by hand.
- **Dialled down on purpose.** "Helios Grid — enablement workshop" has a known client and external attendees, yet it lands in *Needs a look* with the reason "No recognised session format". Archive *Ask me anything* in Admin and re-run AI: AMA events stop matching.
- **Honest uncertainty.** The Brightline intro call and a 30-minute "sync" land in *Needs a look*; standups, 1:1s, interviews and a cancelled event are skipped with a stated reason and are one click from being restored.
- **"Other…" everywhere.** Every dropdown on the form — client, team, format, contact role, use-case type, technology, outcome — ends with *Other…*: type, press Enter (or the check), and the value is saved to the reference table, selected, and the dropdown closes. Admin sees the new row immediately; same-label entries are reused, never duplicated.
- **Redaction.** Meeting links, passcodes, PINs and phone numbers are stripped before text reaches the "model"; the session side panel shows the redacted view.

## Architecture (what is real vs. mocked)

```
lib/
  domain/types.ts      three layers kept distinct: CalendarEvent (evidence) · Suggestion (proposal) · Session (authoritative, per-field provenance)
  domain/ops.ts        pure state transitions: runSync, confirmCandidate, decideSuggestion, updateSession, rerunAI …  (unit-tested)
  domain/store.ts      zustand store persisted to IndexedDB (idb-keyval); versioned, resettable
  domain/selectors.ts  view models for inbox cards and the session workspace
  domain/timeline.ts   unified items + grouping/filtering for timeline and calendar
  domain/today.ts      the home board: today's sessions for the member + overdue feedback
  ai/                  deterministic engine: normalize → redact → classify (format match required) → extract/match (client, team, people,
                       technologies, a drafted use case with type) → Suggestion[] with confidence + evidence
  sync/sync.ts         provider event → evidence record, content hash, duplicate detection
  mock/seed.ts         org, 5 personas, 7 clients, teams, people, the four catalogues, 16 historical sessions (with use cases) by other engineers
  mock/calendar.ts     "Google Calendar" fixture batches per persona and scenario (initial lookback + 2 incremental syncs)
components/            shell (rail, top bar, persona switcher, sync pill, ⌘K), inbox, session, timeline, calendar, common
app/                   Next.js 16 App Router pages (thin; all state lives client-side)
```

- **Real:** the data model, the three-layer rule (sync writes evidence, AI only proposes, members own the record), provenance preservation, dedupe, the `Suggestion` schema (field, value, confidence, evidence, status, prompt version, decision audit), redaction, keyboard-first UX.
- **Simulated:** Google sign-in (persona switcher), Google Calendar (fixtures relative to the current week), the AI (keyword/alias dictionaries + history statistics, deterministic). The engine sits behind `lib/ai/index.ts#analyzeEvent`; a Devin API provider can replace it without UI changes because the output schema is identical.
- **Deferred:** Reports centre, the rest of Admin (members, connectors, taxonomy merge), documents, real OAuth, Spring Boot backend. Nav entries are shown disabled so the information architecture is visible.

Prefill policy: confidence ≥ 0.85 is written into the draft (revertible), 0.5–0.85 shows as a chip, below 0.5 is folded away. Confirming a candidate records every prefilled value as an *accepted* decision in one action.

## Scripts

```bash
pnpm dev          # dev server
pnpm build        # production build (Cache Components / PPR enabled)
pnpm lint         # eslint (next core-web-vitals + typescript)
pnpm typecheck    # tsc --noEmit
pnpm test         # vitest: AI engine, sync/dedupe, provenance, timeline selectors
pnpm e2e          # playwright: sync → confirm all → timeline → re-sync (no dupes) → manual edit survives; noise; persona switch
                  # first time: pnpm exec playwright install chromium
node scripts/screenshot.mjs   # capture /tmp/est-shots/*.png from a running dev server on :3100
```

Data lives in IndexedDB under `enablement-session-tracker`; **Reset demo** or a fresh browser profile clears it.

## Design

Dark by default: deep navy / black surfaces, ivory type, a single sapphire accent reserved for AI proposals, emerald for confirmed, amber for "needs a look". Light mode is navy ink on cool paper. Client and person marks derive a low-chroma colour from a stable hue via `light-dark()`, so they stay rich in both schemes. Tokens live at the bottom of `app/globals.css`.
