import { analyzeEvent, ITEM_FIELDS, tierFor, type ClientCreate, type EngineContext } from "@/lib/ai";
import { stableId } from "@/lib/domain/ids";
import type {
  CalendarEvent,
  CatalogItem,
  CatalogKind,
  Candidate,
  Client,
  EditableField,
  ID,
  Member,
  Person,
  ScenarioId,
  Session,
  SessionContact,
  Settings,
  Suggestion,
  SyncRun,
  Team,
  UseCase,
} from "@/lib/domain/types";
import { fetchCalendarPage } from "@/lib/mock/calendar";
import { anchorDate, buildHistoricalSessions, CATALOG, CLIENTS, DEFAULT_MEMBER_ID, MEMBERS, PEOPLE, TEAMS } from "@/lib/mock/seed";
import { candidateIdFor, findDuplicate, toCalendarEvent } from "@/lib/sync/sync";

export const SCHEMA_VERSION = 5;

export interface DataState {
  schemaVersion: number;
  anchorIso: string;
  currentMemberId: ID;
  settings: Settings;
  members: Member[];
  clients: Client[];
  teams: Team[];
  people: Person[];
  /** Admin-managed reference tables (formats, outcomes, technologies, use-case types). */
  catalog: CatalogItem[];
  events: CalendarEvent[];
  candidates: Candidate[];
  suggestions: Suggestion[];
  sessions: Session[];
  syncRuns: SyncRun[];
  cursors: Record<ID, number>;
}

export function initialState(scenario: ScenarioId = "messy", now = new Date(), currentMemberId = DEFAULT_MEMBER_ID): DataState {
  const anchor = anchorDate(now);
  return {
    schemaVersion: SCHEMA_VERSION,
    anchorIso: anchor.toISOString(),
    currentMemberId,
    settings: { showReasoning: false, scenario },
    members: MEMBERS,
    clients: CLIENTS,
    teams: TEAMS,
    people: PEOPLE,
    catalog: CATALOG,
    events: [],
    candidates: [],
    suggestions: [],
    sessions: buildHistoricalSessions(anchor),
    syncRuns: [],
    cursors: {},
  };
}

export function engineContext(state: DataState, ownerMemberId: ID): EngineContext {
  return {
    members: state.members,
    clients: state.clients,
    teams: state.teams,
    people: state.people,
    catalog: state.catalog,
    sessions: state.sessions.filter((s) => s.state !== "ignored" && s.state !== "cancelled"),
    ownerMemberId,
  };
}

function mergePeople(existing: Person[], incoming: Person[]): Person[] {
  const ids = new Set(existing.map((p) => p.id));
  return [...existing, ...incoming.filter((p) => !ids.has(p.id))];
}

/* ───────────────────────── Sync ───────────────────────── */

export interface SyncOutcome {
  state: DataState;
  run: SyncRun;
  newCandidateIds: ID[];
  updatedCandidateIds: ID[];
}

export function runSync(input: DataState, now = new Date()): SyncOutcome {
  let state = input;
  const member = state.members.find((m) => m.id === state.currentMemberId)!;
  const cursor = state.cursors[member.id] ?? 0;
  const anchor = new Date(state.anchorIso);
  const page = fetchCalendarPage(member.email, state.settings.scenario, cursor, anchor, now);
  const runId = stableId("run", `${member.id}/${state.settings.scenario}/${cursor}`);
  const startedAt = now.toISOString();
  const nowIso = startedAt;

  let eventsNew = 0;
  let eventsUpdated = 0;
  let eventsUnchanged = 0;
  const newCandidateIds: ID[] = [];
  const updatedCandidateIds: ID[] = [];

  for (const p of page.events) {
    const incoming = toCalendarEvent(p, member.id, runId);
    const existing = state.events.find((e) => e.id === incoming.id);

    if (existing) {
      if (existing.etag === incoming.etag) {
        eventsUnchanged += 1;
        state = { ...state, events: state.events.map((e) => (e.id === existing.id ? { ...e, lastSeenSyncRunId: runId } : e)) };
        continue;
      }
      eventsUpdated += 1;
      const updatedEvent: CalendarEvent = { ...incoming, firstSeenSyncRunId: existing.firstSeenSyncRunId };
      state = { ...state, events: state.events.map((e) => (e.id === existing.id ? updatedEvent : e)) };
      const cand = state.candidates.find((c) => c.eventId === existing.id);
      if (cand) {
        state = reanalyzeCandidate(state, cand, updatedEvent, nowIso);
        updatedCandidateIds.push(cand.id);
      }
      continue;
    }

    eventsNew += 1;
    const candidateId = candidateIdFor(member.id, incoming.providerEventId);
    const result = analyzeEvent(incoming, candidateId, engineContext(state, member.id));
    const ownerEvents = state.events.filter((e) => e.ownerMemberId === member.id);
    const dup = incoming.status === "confirmed" ? findDuplicate(incoming, ownerEvents) : null;
    const dupCandidate = dup ? state.candidates.find((c) => c.eventId === dup.id && c.status !== "dismissed") ?? null : null;

    const candidate: Candidate = {
      id: candidateId,
      eventId: incoming.id,
      ownerMemberId: member.id,
      classification: result.classification,
      duplicateOfCandidateId: dupCandidate?.id ?? null,
      status: "open",
      sessionId: null,
      redactedText: result.redactedText,
      redactions: result.redactions,
      createdAt: nowIso,
      updatedAt: nowIso,
    };
    state = {
      ...state,
      events: [...state.events, incoming],
      people: mergePeople(state.people, result.newPeople),
      candidates: [...state.candidates, candidate],
      suggestions: [...state.suggestions, ...result.suggestions],
    };
    newCandidateIds.push(candidateId);
  }

  const run: SyncRun = {
    id: runId,
    memberId: member.id,
    startedAt,
    finishedAt: nowIso,
    eventsSeen: page.events.length,
    eventsNew,
    eventsUpdated,
    eventsUnchanged,
    candidatesCreated: newCandidateIds.length,
    cursor,
  };
  state = {
    ...state,
    syncRuns: [...state.syncRuns.filter((r) => r.id !== runId), run],
    cursors: { ...state.cursors, [member.id]: page.nextCursor },
  };
  return { state, run, newCandidateIds, updatedCandidateIds };
}

/**
 * Evidence changed: re-run the engine. Old proposals are superseded, new ones are
 * added, and a confirmed session only receives chips for fields a human hasn't
 * set manually and whose value actually differs. Nothing is overwritten.
 */
function reanalyzeCandidate(input: DataState, cand: Candidate, event: CalendarEvent, nowIso: string): DataState {
  let state = input;
  const result = analyzeEvent(event, cand.id, engineContext(state, cand.ownerMemberId));
  const session = cand.sessionId ? state.sessions.find((s) => s.id === cand.sessionId) ?? null : null;
  const previous = state.suggestions.filter((s) => s.candidateId === cand.id);
  const previousIds = new Set(previous.map((s) => s.id));
  const freshIds = new Set(result.suggestions.map((s) => s.id));

  const kept = state.suggestions.map((s) => {
    if (s.candidateId !== cand.id || s.status !== "proposed") return s;
    return freshIds.has(s.id) ? s : { ...s, status: "superseded" as const };
  });

  const additions = result.suggestions
    .filter((s) => !previousIds.has(s.id))
    .filter((s) => {
      if (!session) return true;
      if (session.provenance[s.field as EditableField] === "manual") return false;
      return !sessionAlreadyHas(session, s);
    })
    .map((s) => ({ ...s, sessionId: session?.id ?? null }));

  state = {
    ...state,
    people: mergePeople(state.people, result.newPeople),
    suggestions: [...kept, ...additions],
    candidates: state.candidates.map((c) =>
      c.id === cand.id
        ? { ...c, classification: result.classification, redactedText: result.redactedText, redactions: result.redactions, updatedAt: nowIso }
        : c,
    ),
  };
  return state;
}

function sessionAlreadyHas(session: Session, s: Suggestion): boolean {
  switch (s.field) {
    case "technologyIds":
      return session.technologyIds.includes(s.value as ID);
    case "useCases":
      return session.useCases.some((u) => u.id === (s.value as UseCase).id || u.title.toLowerCase() === (s.value as UseCase).title.toLowerCase());
    case "coFacilitatorIds":
      return session.coFacilitatorIds.includes(s.value as ID);
    case "contacts": {
      const v = s.value as SessionContact;
      return session.contacts.some((c) => c.personId === v.personId && c.role === v.role);
    }
    case "participantIds":
      return JSON.stringify([...session.participantIds].sort()) === JSON.stringify([...(s.value as ID[])].sort());
    case "clientId":
      return typeof s.value === "string" && session.clientId === s.value;
    default:
      return JSON.stringify((session as unknown as Record<string, unknown>)[s.field]) === JSON.stringify(s.value);
  }
}

/* ───────────────────────── Candidate decisions ───────────────────────── */

/** A confirmed candidate's session id is derived from its event, so links can be built before confirming. */
export function sessionIdForEvent(eventId: ID): ID {
  return stableId("ses", eventId);
}

function blankSession(event: CalendarEvent, memberId: ID, nowIso: string, now: Date): Session {
  return {
    id: sessionIdForEvent(event.id),
    state: Date.parse(event.end) < now.getTime() ? "completed" : "planned",
    title: event.title,
    clientId: null,
    teamId: null,
    facilitatorId: null,
    coFacilitatorIds: [],
    contacts: [],
    scheduledStart: event.start,
    scheduledEnd: event.end,
    actualStart: null,
    actualEnd: null,
    participantIds: [],
    participantsEstimated: null,
    sessionType: null,
    outcomeId: null,
    technologyIds: [],
    useCases: [],
    summary: "",
    notes: "",
    sourceEventIds: [event.id],
    provenance: {},
    createdBy: memberId,
    createdAt: nowIso,
    updatedBy: memberId,
    updatedAt: nowIso,
    version: 1,
  };
}

function materializeClient(state: DataState, value: unknown): { state: DataState; clientId: ID | null } {
  if (typeof value === "string") return { state, clientId: value };
  const create = (value as ClientCreate | null)?.create;
  if (!create) return { state, clientId: null };
  const id = stableId("cli", create.name);
  if (state.clients.some((c) => c.id === id)) return { state, clientId: id };
  const client: Client = { id, name: create.name, domains: [create.domain], industry: null, hue: (create.name.length * 47) % 360 };
  const people = state.people.map((p) =>
    p.clientId === null && p.email && p.email.toLowerCase().endsWith(`@${create.domain}`) ? { ...p, clientId: id } : p,
  );
  return { state: { ...state, clients: [...state.clients, client], people }, clientId: id };
}

function applyToSession(state: DataState, session: Session, s: Suggestion, provenance: "prefilled" | "accepted"): { state: DataState; session: Session } {
  const next: Session = { ...session, provenance: { ...session.provenance } };
  const field = s.field as EditableField;
  switch (s.field) {
    case "clientId": {
      const m = materializeClient(state, s.value);
      state = m.state;
      next.clientId = m.clientId;
      break;
    }
    case "technologyIds":
      if (!next.technologyIds.includes(s.value as ID)) next.technologyIds = [...next.technologyIds, s.value as ID];
      break;
    case "useCases": {
      const uc = s.value as UseCase;
      if (!next.useCases.some((u) => u.id === uc.id)) {
        next.useCases = [...next.useCases, { ...uc, technologyIds: [...uc.technologyIds] }];
        next.technologyIds = [...new Set([...next.technologyIds, ...uc.technologyIds])];
      }
      break;
    }
    case "coFacilitatorIds":
      if (!next.coFacilitatorIds.includes(s.value as ID)) next.coFacilitatorIds = [...next.coFacilitatorIds, s.value as ID];
      break;
    case "contacts": {
      const v = s.value as SessionContact;
      if (!next.contacts.some((c) => c.personId === v.personId && c.role === v.role)) next.contacts = [...next.contacts, v];
      break;
    }
    case "participantIds":
      next.participantIds = [...(s.value as ID[])];
      break;
    case "participantsEstimated":
      next.participantsEstimated = s.value as number;
      break;
    case "title":
    case "summary":
      next[s.field] = s.value as string;
      break;
    case "teamId":
    case "facilitatorId":
      next[s.field] = s.value as ID;
      break;
    case "sessionType":
      next.sessionType = s.value as Session["sessionType"];
      break;
  }
  if (next.provenance[field] !== "manual") next.provenance[field] = provenance;
  return { state, session: next };
}

export function confirmCandidate(input: DataState, candidateId: ID, memberId: ID, now = new Date()): DataState {
  let state = input;
  const cand = state.candidates.find((c) => c.id === candidateId);
  if (!cand || cand.status !== "open") return state;
  const event = state.events.find((e) => e.id === cand.eventId)!;
  const nowIso = now.toISOString();
  let session = blankSession(event, memberId, nowIso, now);
  const proposals = state.suggestions.filter((s) => s.candidateId === candidateId && s.status === "proposed");
  const decided = new Set<ID>();

  for (const s of proposals) {
    if (tierFor(s.confidence) !== "prefill") continue;
    const r = applyToSession(state, session, s, "prefilled");
    state = r.state;
    session = r.session;
    decided.add(s.id);
  }

  state = {
    ...state,
    sessions: [...state.sessions, session],
    suggestions: state.suggestions.map((s) =>
      s.candidateId !== candidateId
        ? s
        : decided.has(s.id)
          ? { ...s, sessionId: session.id, status: "accepted", decidedBy: memberId, decidedAt: nowIso }
          : { ...s, sessionId: session.id },
    ),
    candidates: state.candidates.map((c) => (c.id === candidateId ? { ...c, status: "confirmed", sessionId: session.id, updatedAt: nowIso } : c)),
  };
  return state;
}

export function dismissCandidate(state: DataState, candidateId: ID, now = new Date()): DataState {
  const nowIso = now.toISOString();
  return {
    ...state,
    candidates: state.candidates.map((c) => (c.id === candidateId && c.status === "open" ? { ...c, status: "dismissed", updatedAt: nowIso } : c)),
  };
}

export function restoreCandidate(state: DataState, candidateId: ID, now = new Date()): DataState {
  const nowIso = now.toISOString();
  return {
    ...state,
    candidates: state.candidates.map((c) =>
      c.id === candidateId && (c.status === "dismissed" || c.status === "merged") ? { ...c, status: "open", updatedAt: nowIso } : c,
    ),
  };
}

/** Treats the duplicate's event as another source for the original's session. */
export function mergeDuplicate(state: DataState, candidateId: ID, now = new Date()): DataState {
  const cand = state.candidates.find((c) => c.id === candidateId);
  if (!cand?.duplicateOfCandidateId) return state;
  const original = state.candidates.find((c) => c.id === cand.duplicateOfCandidateId);
  const nowIso = now.toISOString();
  const sessions = original?.sessionId
    ? state.sessions.map((s) =>
        s.id === original.sessionId && !s.sourceEventIds.includes(cand.eventId) ? { ...s, sourceEventIds: [...s.sourceEventIds, cand.eventId] } : s,
      )
    : state.sessions;
  return {
    ...state,
    sessions,
    candidates: state.candidates.map((c) => (c.id === candidateId ? { ...c, status: "merged", updatedAt: nowIso } : c)),
  };
}

/* ───────────────────────── Suggestion decisions on sessions ───────────────────────── */

export function decideSuggestion(input: DataState, suggestionId: ID, decision: "accept" | "reject", memberId: ID, now = new Date()): DataState {
  let state = input;
  const s = state.suggestions.find((x) => x.id === suggestionId);
  if (!s || s.status !== "proposed" || !s.sessionId) return state;
  const nowIso = now.toISOString();
  let sessions = state.sessions;
  if (decision === "accept") {
    const session = state.sessions.find((x) => x.id === s.sessionId);
    if (!session) return state;
    const r = applyToSession(state, session, s, "accepted");
    state = r.state;
    const updated: Session = { ...r.session, updatedBy: memberId, updatedAt: nowIso, version: session.version + 1 };
    sessions = state.sessions.map((x) => (x.id === session.id ? updated : x));
  }
  const supersedeSingle = decision === "accept" && !ITEM_FIELDS.has(s.field);
  return {
    ...state,
    sessions,
    suggestions: state.suggestions.map((x) => {
      if (x.id === s.id) return { ...x, status: decision === "accept" ? "accepted" : "rejected", decidedBy: memberId, decidedAt: nowIso };
      if (supersedeSingle && x.sessionId === s.sessionId && x.field === s.field && x.status === "proposed") return { ...x, status: "superseded" };
      return x;
    }),
  };
}

export function acceptAllForSession(input: DataState, sessionId: ID, memberId: ID, minConfidence: number, now = new Date()): DataState {
  let state = input;
  const ids = state.suggestions
    .filter((s) => s.sessionId === sessionId && s.status === "proposed" && s.confidence >= minConfidence)
    .sort((a, b) => b.confidence - a.confidence)
    .map((s) => s.id);
  for (const id of ids) state = decideSuggestion(state, id, "accept", memberId, now);
  return state;
}

/* ───────────────────────── Manual edits ───────────────────────── */

export type SessionPatch = Partial<Pick<Session, EditableField>>;

export function updateSession(state: DataState, sessionId: ID, patch: SessionPatch, memberId: ID, now = new Date()): DataState {
  const session = state.sessions.find((s) => s.id === sessionId);
  if (!session) return state;
  const nowIso = now.toISOString();
  const provenance = { ...session.provenance };
  for (const key of Object.keys(patch) as EditableField[]) provenance[key] = "manual";
  const updated: Session = { ...session, ...patch, provenance, updatedBy: memberId, updatedAt: nowIso, version: session.version + 1 };
  const touchedSingle = new Set((Object.keys(patch) as EditableField[]).filter((k) => !ITEM_FIELDS.has(k as Suggestion["field"])));
  return {
    ...state,
    sessions: state.sessions.map((s) => (s.id === sessionId ? updated : s)),
    suggestions: state.suggestions.map((s) =>
      s.sessionId === sessionId && s.status === "proposed" && touchedSingle.has(s.field as EditableField) ? { ...s, status: "superseded" } : s,
    ),
  };
}

/** Re-run the engine for a confirmed session using today's history; never overwrites. */
export function rerunAI(state: DataState, sessionId: ID, now = new Date()): DataState {
  const cand = state.candidates.find((c) => c.sessionId === sessionId);
  if (!cand) return state;
  const event = state.events.find((e) => e.id === cand.eventId);
  if (!event) return state;
  return reanalyzeCandidate(state, cand, event, now.toISOString());
}

/* ───────────────────────── Admin: catalogue tables ───────────────────────── */

export type CatalogPatch = Partial<Pick<CatalogItem, "label" | "description" | "keywords" | "typicalMinutes" | "archived">>;

export function slugifyId(label: string): string {
  const base = label.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim().split(" ").map((w, i) => (i ? w[0]!.toUpperCase() + w.slice(1) : w)).join("");
  return base || "item";
}

/** The id a new catalogue row with this label will receive (stable so callers can select it right away). */
export function newCatalogId(state: DataState, label: string): ID {
  let id = slugifyId(label);
  while (state.catalog.some((t) => t.id === id)) id = `${id}2`;
  return id;
}

/** Existing active row of this kind with the same label (case-insensitive), if any — avoids duplicates from "Other…". */
export function findCatalogByLabel(state: DataState, kind: CatalogKind, label: string): CatalogItem | null {
  const l = label.trim().toLowerCase();
  return state.catalog.find((c) => c.kind === kind && c.label.trim().toLowerCase() === l) ?? null;
}

/** Insert (id = null) or update one row of a catalogue. Keywords are normalised to lower case. */
export function upsertCatalogItem(state: DataState, kind: CatalogKind, id: ID | null, patch: CatalogPatch, memberId: ID, now = new Date()): DataState {
  const nowIso = now.toISOString();
  const keywords = (patch.keywords ?? []).map((k) => k.trim().toLowerCase()).filter(Boolean);
  if (id) {
    return {
      ...state,
      catalog: state.catalog.map((t) =>
        t.id === id && t.kind === kind
          ? { ...t, ...patch, keywords: patch.keywords ? [...new Set(keywords)] : t.keywords, updatedBy: memberId, updatedAt: nowIso }
          : t,
      ),
    };
  }
  const label = (patch.label ?? "").trim();
  if (!label) return state;
  const newId = newCatalogId(state, label);
  const siblings = state.catalog.filter((t) => t.kind === kind);
  const row: CatalogItem = {
    id: newId,
    kind,
    label,
    description: patch.description ?? "",
    keywords: [...new Set([label.toLowerCase(), ...keywords])],
    typicalMinutes: kind === "sessionType" ? (patch.typicalMinutes ?? 60) : null,
    archived: false,
    sortOrder: siblings.length,
    updatedBy: memberId,
    updatedAt: nowIso,
  };
  return { ...state, catalog: [...state.catalog, row] };
}

export function moveCatalogItem(state: DataState, kind: CatalogKind, id: ID, delta: -1 | 1): DataState {
  const ordered = state.catalog.filter((t) => t.kind === kind).sort((a, b) => a.sortOrder - b.sortOrder);
  const i = ordered.findIndex((t) => t.id === id);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= ordered.length) return state;
  [ordered[i], ordered[j]] = [ordered[j]!, ordered[i]!];
  const order = new Map(ordered.map((t, k) => [t.id, k]));
  return { ...state, catalog: state.catalog.map((t) => (order.has(t.id) ? { ...t, sortOrder: order.get(t.id)! } : t)) };
}

/* ───────────────────────── Use cases (inline on the session) ───────────────────────── */

export function addUseCase(state: DataState, sessionId: ID, memberId: ID, draft: Partial<UseCase> = {}, now = new Date()): DataState {
  const session = state.sessions.find((s) => s.id === sessionId);
  if (!session) return state;
  const uc: UseCase = {
    id: draft.id ?? stableId("uc", `${sessionId}/${session.useCases.length}/${now.toISOString()}`),
    title: draft.title ?? "",
    description: draft.description ?? "",
    useCaseTypeId: draft.useCaseTypeId ?? null,
    technologyIds: draft.technologyIds ?? [],
  };
  return updateSession(state, sessionId, { useCases: [...session.useCases, uc] }, memberId, now);
}

export function updateUseCase(state: DataState, sessionId: ID, useCaseId: ID, patch: Partial<Omit<UseCase, "id">>, memberId: ID, now = new Date()): DataState {
  const session = state.sessions.find((s) => s.id === sessionId);
  if (!session) return state;
  const useCases = session.useCases.map((u) => (u.id === useCaseId ? { ...u, ...patch } : u));
  const technologyIds = [...new Set([...session.technologyIds, ...useCases.flatMap((u) => u.technologyIds)])];
  return updateSession(state, sessionId, { useCases, technologyIds }, memberId, now);
}

export function removeUseCase(state: DataState, sessionId: ID, useCaseId: ID, memberId: ID, now = new Date()): DataState {
  const session = state.sessions.find((s) => s.id === sessionId);
  if (!session) return state;
  return updateSession(state, sessionId, { useCases: session.useCases.filter((u) => u.id !== useCaseId) }, memberId, now);
}

/* ───────────────────────── Selectors shared by UI and tests ───────────────────────── */

export function openCandidatesFor(state: DataState, memberId: ID): Candidate[] {
  return state.candidates.filter((c) => c.ownerMemberId === memberId && c.status === "open");
}

export function proposalsFor(state: DataState, candidateId: ID): Suggestion[] {
  return state.suggestions.filter((s) => s.candidateId === candidateId && s.status === "proposed");
}

export function sessionSuggestions(state: DataState, sessionId: ID): Suggestion[] {
  return state.suggestions.filter((s) => s.sessionId === sessionId && s.status === "proposed");
}
