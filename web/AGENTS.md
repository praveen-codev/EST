<!-- BEGIN:nextjs-agent-rules -->

## This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

## Project notes

Enablement Session Tracker prototype — Next.js 16 (App Router, Cache Components), React 19, Tailwind 4,
shadcn/ui (radix-nova preset, `cn` package), `motion`, zustand + idb-keyval, pnpm. See `README.md` for the
architecture and what is mocked.

### Verify before finishing a change

```bash
pnpm lint && pnpm typecheck && pnpm test && pnpm build
pnpm e2e        # needs `pnpm exec playwright install chromium` once; starts its own dev server on :3100
```

### Conventions that matter here

- Three layers never mix: `CalendarEvent` (evidence, written only by sync), `Suggestion` (AI proposal, needs a
  decision), `Session` (authoritative, per-field `provenance`). AI/sync may never overwrite a `manual` field.
- All state transitions are pure functions in `lib/domain/ops.ts`; the zustand store only wraps them. Add logic
  there and cover it in `tests/unit`.
- The AI engine (`lib/ai`) is deterministic: same event + context ⇒ identical suggestions and ids. Keep it that
  way so tests and the "re-run AI" action stay reproducible. Redact before extracting.
- Pages are thin server components; views are `"use client"` and return `null` until `useHydrated()` is true.
  Wrap anything using `useParams`/`useSearchParams` in `<Suspense>` (Next 16 prerender validation).
- Seed data lives in `lib/mock/seed.ts` and `lib/mock/calendar.ts`; fixtures are relative to Monday of the
  current week. Bump `SCHEMA_VERSION` in `ops.ts` when the persisted shape changes so stale IndexedDB data resets.
- Reference data lives in one `state.catalog` array of `CatalogItem { kind, … }` (kinds: sessionType, sessionOutcome,
  technology, useCaseType, contactRole), seeded from `CATALOG` in `lib/mock/seed.ts` and edited via `upsertCatalogItem`/`moveCatalogItem`.
  Never hard-code these lists in UI; use `catalogOf(state.catalog, kind)`. The classifier caps any event without a format
  match at `NO_TYPE_CAP` (review bucket); extraction never guesses a type.
- Entry dropdowns must offer "Other…": use `Combobox` (with `onCreate`), `SelectWithOther` or `ToggleGroupWithOther`
  from `components/common/other-option.tsx`; `onCreate` persists via `store.createCatalogItem/createClient/createTeam/
  createPerson` and returns the id to select. Required fields render `<RequiredMark />` (red, 1.5×) via `FieldRow required`.
- Use cases are inline on the session (`session.useCases: UseCase[]`); their technologies roll up into
  `session.technologyIds` via `updateUseCase`. The home page is `/` (Today); `/inbox` is secondary.
- Palette: no purple. Tokens (`--ai`, `--success`, `--warning`, `--surface`, shadcn base) are defined once in
  `app/globals.css`; entity colours come from `hueStyle()` in `components/common/marks.tsx` using `light-dark()`.
- Do not reintroduce `setState` directly inside effects — the Next ESLint config flags it; derive instead.
