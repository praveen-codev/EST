import type {
  CalendarEvent,
  Candidate,
  CatalogItem,
  Client,
  ID,
  Member,
  Person,
  Session,
  SessionContact,
  Settings,
  Suggestion,
  SyncRun,
  Team,
  UseCase,
} from "./types";

/** The client-side model: the API's /snapshot mapped onto the shapes the selectors already understand. */
export interface DataState {
  currentMemberId: ID;
  settings: Settings;
  features: Features;
  members: Member[];
  clients: Client[];
  teams: Team[];
  people: Person[];
  catalog: CatalogItem[];
  events: CalendarEvent[];
  candidates: Candidate[];
  suggestions: Suggestion[];
  sessions: Session[];
  syncRuns: SyncRun[];
  identities: Identity[];
}

export interface Features {
  devMode: boolean;
  mockGoogle: boolean;
  scenario: string | null;
  /** Mock provider only: fixture pages still to sync for the current member. */
  remainingPages: number;
}

export interface Identity {
  id: ID;
  memberId: ID;
  provider: "google" | "mock";
  email: string;
  displayName: string | null;
  scopes: string[];
  status: "ACTIVE" | "NEEDS_REAUTH" | "DISABLED";
  lastSyncAt: string | null;
  lastError: string | null;
}

/* ───────────────────────── wire → model mapping ───────────────────────── */

type WireContact = { personId: ID; roleId: string };
type WireSession = Omit<Session, "contacts"> & { contacts: WireContact[] };
type WirePerson = Omit<Person, "role"> & { roleId: string | null };
type WireSuggestion = Omit<Suggestion, "value"> & { value: unknown };
type WireSyncRun = { id: ID; identityId: ID; kind: string; startedAt: string; finishedAt: string | null; status: string; eventsSeen: number; eventsNew: number; eventsUpdated: number; eventsUnchanged: number; candidatesCreated: number; error: string | null };

export interface Snapshot {
  me: Member;
  features: Features;
  members: Member[];
  clients: Client[];
  teams: Team[];
  people: WirePerson[];
  catalog: CatalogItem[];
  sessions: WireSession[];
  candidates: Candidate[];
  suggestions: WireSuggestion[];
  events: Array<Omit<CalendarEvent, "firstSeenSyncRunId" | "lastSeenSyncRunId">>;
  syncRuns: WireSyncRun[];
  identities: Identity[];
}

const contact = (c: WireContact): SessionContact => ({ personId: c.personId, role: c.roleId });

function suggestionValue(s: WireSuggestion): Suggestion["value"] {
  if (s.field === "contacts") return contact(s.value as WireContact);
  if (s.field === "clientId" && s.value && typeof s.value === "object") {
    const v = s.value as { name: string; domain: string };
    return { create: { name: v.name, domain: v.domain } };
  }
  if (s.field === "useCases") return s.value as UseCase;
  return s.value as Suggestion["value"];
}

export function fromSnapshot(snap: Snapshot, settings: Settings): DataState {
  return {
    currentMemberId: snap.me.id,
    settings: { ...settings, scenario: (snap.features.scenario as Settings["scenario"]) ?? settings.scenario },
    features: snap.features,
    members: snap.members,
    clients: snap.clients,
    teams: snap.teams,
    people: snap.people.map((p) => ({ id: p.id, clientId: p.clientId, name: p.name, email: p.email, role: p.roleId })),
    catalog: snap.catalog,
    events: snap.events.map((e) => ({ ...e, firstSeenSyncRunId: "", lastSeenSyncRunId: "" })),
    candidates: snap.candidates,
    suggestions: snap.suggestions.map((s) => ({ ...s, value: suggestionValue(s) }) as Suggestion),
    sessions: snap.sessions.map((s) => ({ ...s, contacts: s.contacts.map(contact) })),
    syncRuns: snap.syncRuns.map((r) => ({
      id: r.id, memberId: snap.me.id, startedAt: r.startedAt, finishedAt: r.finishedAt ?? r.startedAt, eventsSeen: r.eventsSeen, eventsNew: r.eventsNew,
      eventsUpdated: r.eventsUpdated, eventsUnchanged: r.eventsUnchanged, candidatesCreated: r.candidatesCreated, cursor: 0,
    })),
    identities: snap.identities,
  };
}

export const EMPTY_STATE: DataState = {
  currentMemberId: "",
  settings: { showReasoning: false, scenario: "messy" },
  features: { devMode: false, mockGoogle: false, scenario: null, remainingPages: 0 },
  members: [], clients: [], teams: [], people: [], catalog: [], events: [], candidates: [], suggestions: [], sessions: [], syncRuns: [], identities: [],
};
