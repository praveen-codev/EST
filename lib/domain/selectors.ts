import { tierFor } from "@/lib/ai";
import type { DataState } from "./ops";
import { FIELD_LABELS, sessionTypeLabel } from "./labels";
import type {
  CalendarEvent,
  Candidate,
  Client,
  ID,
  Member,
  Person,
  Session,
  SessionContact,
  SessionType,
  Suggestion,
  SuggestionField,
  Tag,
  Team,
} from "./types";

/** Fields the inbox counts toward "N of M fields prefilled". */
export const KEY_FIELDS: SuggestionField[] = [
  "title", "clientId", "teamId", "sessionType", "facilitatorId", "contacts",
  "participantIds", "participantsEstimated", "devinAsk", "tagIds", "summary",
];

export interface CandidateVM {
  candidate: Candidate;
  event: CalendarEvent;
  suggestions: Suggestion[];
  prefill: Suggestion[];
  chips: Suggestion[];
  folded: Suggestion[];
  client: Client | null;
  clientLabel: string | null;
  team: Team | null;
  sessionType: SessionType | null;
  sessionTypeLabel: string | null;
  tags: Tag[];
  facilitator: Member | null;
  coverage: { filled: number; total: number };
  externalAttendees: CalendarEvent["attendees"];
  duplicateOf: CandidateVM | null;
  recurringSiblings: number;
}

export function candidateVM(state: DataState, candidate: Candidate, depth = 0): CandidateVM {
  const event = state.events.find((e) => e.id === candidate.eventId)!;
  const suggestions = state.suggestions.filter((s) => s.candidateId === candidate.id && s.status === "proposed");
  const prefill = suggestions.filter((s) => tierFor(s.confidence) === "prefill");
  const chips = suggestions.filter((s) => tierFor(s.confidence) === "chip");
  const folded = suggestions.filter((s) => tierFor(s.confidence) === "folded");
  const best = (field: SuggestionField) => suggestions.filter((s) => s.field === field).sort((a, b) => b.confidence - a.confidence)[0] ?? null;
  const clientSug = best("clientId");
  const client = clientSug && typeof clientSug.value === "string" ? state.clients.find((c) => c.id === clientSug.value) ?? null : null;
  const teamSug = best("teamId");
  const typeSug = best("sessionType");
  const facSug = best("facilitatorId");
  const tagIds = suggestions.filter((s) => s.field === "tagIds" && tierFor(s.confidence) !== "folded").map((s) => s.value as ID);
  const filledFields = new Set(prefill.map((s) => s.field).filter((f) => KEY_FIELDS.includes(f)));
  const dup = candidate.duplicateOfCandidateId && depth === 0 ? state.candidates.find((c) => c.id === candidate.duplicateOfCandidateId) ?? null : null;
  const internalDomain = "cognition.ai";
  return {
    candidate,
    event,
    suggestions,
    prefill,
    chips,
    folded,
    client,
    clientLabel: client?.name ?? clientSug?.label ?? null,
    team: teamSug ? state.teams.find((t) => t.id === teamSug.value) ?? null : null,
    sessionType: (typeSug?.value as SessionType | undefined) ?? null,
    sessionTypeLabel: sessionTypeLabel(state.sessionTypes, (typeSug?.value as SessionType | undefined) ?? null),
    tags: tagIds.map((id) => state.tags.find((t) => t.id === id)!).filter(Boolean),
    facilitator: facSug ? state.members.find((m) => m.id === facSug.value) ?? null : null,
    coverage: { filled: filledFields.size, total: KEY_FIELDS.length },
    externalAttendees: event.attendees.filter((a) => !a.email.toLowerCase().endsWith(`@${internalDomain}`)),
    duplicateOf: dup ? candidateVM(state, dup, depth + 1) : null,
    recurringSiblings: event.recurringEventId
      ? state.events.filter((e) => e.recurringEventId === event.recurringEventId && e.ownerMemberId === event.ownerMemberId).length
      : 0,
  };
}

export interface InboxGroups {
  likely: CandidateVM[];
  review: CandidateVM[];
  duplicates: CandidateVM[];
  unlikely: CandidateVM[];
  dismissed: CandidateVM[];
}

export function inboxGroups(state: DataState, memberId: ID): InboxGroups {
  const mine = state.candidates.filter((c) => c.ownerMemberId === memberId);
  const byStart = (a: CandidateVM, b: CandidateVM) => Date.parse(a.event.start) - Date.parse(b.event.start);
  const open = mine.filter((c) => c.status === "open").map((c) => candidateVM(state, c));
  const groups: InboxGroups = { likely: [], review: [], duplicates: [], unlikely: [], dismissed: [] };
  for (const vm of open) {
    if (vm.candidate.duplicateOfCandidateId && vm.duplicateOf && vm.duplicateOf.candidate.status !== "dismissed") groups.duplicates.push(vm);
    else groups[vm.candidate.classification.bucket].push(vm);
  }
  groups.dismissed = mine.filter((c) => c.status === "dismissed" || c.status === "merged").map((c) => candidateVM(state, c));
  for (const k of Object.keys(groups) as Array<keyof InboxGroups>) groups[k].sort(byStart);
  // Collapse recurring noise to its first occurrence.
  const seenRecurring = new Set<string>();
  groups.unlikely = groups.unlikely.filter((vm) => {
    const r = vm.event.recurringEventId;
    if (!r) return true;
    if (seenRecurring.has(r)) return false;
    seenRecurring.add(r);
    return true;
  });
  return groups;
}

export interface InboxStats {
  newEvents: number;
  likely: number;
  review: number;
  duplicates: number;
  prefillRatio: number;
  lastRun: DataState["syncRuns"][number] | null;
}

export function inboxStats(state: DataState, groups: InboxGroups, memberId: ID): InboxStats {
  const actionable = [...groups.likely, ...groups.review];
  const filled = actionable.reduce((n, vm) => n + vm.coverage.filled, 0);
  const total = actionable.reduce((n, vm) => n + vm.coverage.total, 0);
  const runs = state.syncRuns.filter((r) => r.memberId === memberId);
  return {
    newEvents: state.candidates.filter((c) => c.ownerMemberId === memberId).length,
    likely: groups.likely.length,
    review: groups.review.length,
    duplicates: groups.duplicates.length,
    prefillRatio: total ? filled / total : 0,
    lastRun: runs[runs.length - 1] ?? null,
  };
}

/* ───────────────────────── Sessions ───────────────────────── */

export interface ContactVM extends SessionContact {
  person: Person;
}

export interface SessionVM {
  session: Session;
  client: Client | null;
  team: Team | null;
  facilitator: Member | null;
  coFacilitators: Member[];
  contacts: ContactVM[];
  participants: Person[];
  tags: Tag[];
  sessionTypeLabel: string | null;
  pending: Suggestion[];
  pendingByField: Map<SuggestionField, Suggestion[]>;
  sourceEvents: CalendarEvent[];
  missing: Array<{ field: SuggestionField | "successState" | "automationOutcome"; label: string }>;
  completeness: number;
  history: Session[];
}

const COMPLETENESS_FIELDS: Array<SessionVM["missing"][number]["field"]> = [
  "clientId", "teamId", "sessionType", "facilitatorId", "contacts", "participantIds", "devinAsk", "tagIds", "summary", "successState", "automationOutcome",
];

export function sessionVM(state: DataState, session: Session): SessionVM {
  const pending = state.suggestions.filter((s) => s.sessionId === session.id && s.status === "proposed").sort((a, b) => b.confidence - a.confidence);
  const pendingByField = new Map<SuggestionField, Suggestion[]>();
  for (const s of pending) pendingByField.set(s.field, [...(pendingByField.get(s.field) ?? []), s]);
  const isEmpty = (field: SessionVM["missing"][number]["field"]) => {
    switch (field) {
      case "contacts": return session.contacts.length === 0;
      case "participantIds": return session.participantIds.length === 0 && session.participantsEstimated === null;
      case "tagIds": return session.tagIds.length === 0;
      case "summary": return session.summary.trim() === "";
      case "successState": return session.state === "completed" && session.successState === "unknown";
      case "automationOutcome": return session.state === "completed" && session.automationOutcome === null;
      default: return (session as unknown as Record<string, unknown>)[field] == null;
    }
  };
  const missing = COMPLETENESS_FIELDS.filter(isEmpty).map((field) => ({ field, label: FIELD_LABELS[field] ?? field }));
  const applicable = COMPLETENESS_FIELDS.filter((f) => (f === "successState" || f === "automationOutcome" ? session.state === "completed" : true));
  return {
    session,
    client: state.clients.find((c) => c.id === session.clientId) ?? null,
    team: state.teams.find((t) => t.id === session.teamId) ?? null,
    facilitator: state.members.find((m) => m.id === session.facilitatorId) ?? null,
    coFacilitators: session.coFacilitatorIds.map((id) => state.members.find((m) => m.id === id)!).filter(Boolean),
    contacts: session.contacts.map((c) => ({ ...c, person: state.people.find((p) => p.id === c.personId)! })).filter((c) => c.person),
    participants: session.participantIds.map((id) => state.people.find((p) => p.id === id)!).filter(Boolean),
    tags: session.tagIds.map((id) => state.tags.find((t) => t.id === id)!).filter(Boolean),
    sessionTypeLabel: sessionTypeLabel(state.sessionTypes, session.sessionType),
    pending,
    pendingByField,
    sourceEvents: session.sourceEventIds.map((id) => state.events.find((e) => e.id === id)!).filter(Boolean),
    missing,
    completeness: applicable.length ? 1 - missing.length / applicable.length : 1,
    history: session.clientId
      ? state.sessions.filter((s) => s.clientId === session.clientId && s.id !== session.id && s.state !== "ignored").sort((a, b) => Date.parse(b.scheduledStart) - Date.parse(a.scheduledStart))
      : [],
  };
}

export function describeSuggestionValue(state: DataState, s: Suggestion): string {
  return s.label;
}

export function memberById(state: DataState, id: ID | null): Member | null {
  return id ? state.members.find((m) => m.id === id) ?? null : null;
}
