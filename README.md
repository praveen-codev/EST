# Enablement Session Tracker — rapid prototype

A clickable MVP of the capture workflow from `enablement-session-tracker-plan.md`: calendar events become
candidate sessions, a (simulated) AI prefills almost every field from the invite **and** from sessions that
teammates already logged, and the member confirms in one click. Everything runs in the browser — no sign-in,
no server, no API keys.

```bash
pnpm install
pnpm dev            # http://localhost:3000 → redirects to /inbox
```

The first visit auto-runs the initial calendar lookback for the default persona (Alex). Use **Sync calendar**
in the top bar to pull the next incremental batch, the avatar menu to switch persona, and the flask icon
(bottom-left) for scenarios, "show AI reasoning" and **Reset demo**.

## What to look at

| Surface | Why it matters |
|---|---|
| **Capture inbox** `/inbox` | Only the team's own formats count: an event must match a **session format** from the admin table (101 session, Pair programming, 201 session, Ask me anything, Sprint in a day) to be *Likely*; generic workshops/kickoffs with a client are capped at *Needs a look*. Grouped into *Likely* / *Needs a look* / *Possible duplicates* / *skipped*. Each card shows what the AI proposes, its confidence, and "N of 11 fields prefilled". **Confirm** (or `Enter`) creates the session with every high-confidence value already decided. **Confirm all likely** (`⇧↵`) does a whole week in one click. Hover a chip for the evidence. `J`/`K`/`X`/`E` for keyboard triage. |
| **Session workspace** `/sessions/:id` | Progressive disclosure: Essentials → People → Tags → Summary → Advanced. Every field has a provenance dot (prefilled / accepted / manual) and inline suggestion chips with accept ✓ / reject ✕; `Tab` to a chip, `Enter` accepts, `Backspace` rejects. "Still missing" links jump to gaps. The right panel shows **what the AI saw** (post-redaction) and the **team's history** with this client. |
| **Timeline** `/timeline` | Group by day / week / client / member; filter by client, facilitator, tag, state, mine/everyone; candidates appear as ghost rows you can confirm from a side peek. |
| **Calendar** `/calendar` | Month and week views; confirmed sessions solid in the client's colour, candidates dashed. |
| **Admin** `/admin` | The `session_types` table: label, keywords that identify the format in an invite, typical length, archive/restore, reorder. Editable by the ADMIN role (Dana); members see it read-only. Archiving keeps old sessions valid but stops new matches. |
| **⌘K** | Jump to any session or client, confirm all likely, sync, switch persona. `?` lists shortcuts. |

### Things worth demonstrating

- **Cross-user suggestions.** Alex has never run an Acme office hours, yet the card proposes *Jane Doe · Product owner* and the *Jenkins* / *Java 8 → 21 upgrade* tags — evidence reads "Recorded on 3 Acme sessions by Priya". Switch to Priya to see her own inbox; the history is shared.
- **Sync never duplicates.** Press *Sync calendar* repeatedly: changed events refresh evidence and add new chips, unchanged ones are reported as such, and the forwarded copy of the Vantage workshop is caught as a duplicate.
- **Manual edits win.** Type a title in a session, sync again: the title stays, and the AI only adds suggestions for fields you haven't set by hand.
- **Dialled down on purpose.** "Helios Grid — enablement workshop" has a known client and external attendees, yet it lands in *Needs a look* with the reason "No recognised session format". Archive *Ask me anything* in Admin and re-run AI: AMA events stop matching.
- **Honest uncertainty.** The Brightline intro call and a 30-minute "sync" land in *Needs a look*; standups, 1:1s, interviews and a cancelled event are skipped with a stated reason and are one click from being restored.
- **Redaction.** Meeting links, passcodes, PINs and phone numbers are stripped before text reaches the "model"; the session side panel shows the redacted view.

## Architecture (what is real vs. mocked)

```
lib/
  domain/types.ts      three layers kept distinct: CalendarEvent (evidence) · Suggestion (proposal) · Session (authoritative, per-field provenance)
  domain/ops.ts        pure state transitions: runSync, confirmCandidate, decideSuggestion, updateSession, rerunAI …  (unit-tested)
  domain/store.ts      zustand store persisted to IndexedDB (idb-keyval); versioned, resettable
  domain/selectors.ts  view models for inbox cards and the session workspace
  domain/timeline.ts   unified items + grouping/filtering for timeline and calendar
  ai/                  deterministic engine: normalize → redact → classify → extract/match → Suggestion[] with confidence + evidence
  sync/sync.ts         provider event → evidence record, content hash, duplicate detection
  mock/seed.ts         org, 5 personas, 6 clients, teams, people, 42 canonical tags with aliases, the 5 session formats, 15 historical sessions by other engineers
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
