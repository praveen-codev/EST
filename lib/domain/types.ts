/**
 * Domain model. Three layers are kept distinct on purpose (plan §14):
 *   1. Evidence      — CalendarEvent (owned by sync; never edited by humans)
 *   2. Proposals     — Suggestion   (owned by the AI engine; needs a human decision)
 *   3. Authoritative — Session      (owned by members; per-field provenance)
 */

export type ID = string;
export type ISODateTime = string;

export type MemberRole = "MEMBER" | "MANAGER" | "ADMIN";

export interface Member {
  id: ID;
  name: string;
  email: string;
  title: string;
  role: MemberRole;
  /** Tailwind-friendly hue (0-360) for avatar/accent colour. */
  hue: number;
}

export interface Client {
  id: ID;
  name: string;
  /** Email domains used to recognise attendees from this client. */
  domains: string[];
  industry: string | null;
  hue: number;
}

export interface Team {
  id: ID;
  clientId: ID;
  name: string;
  /** Keywords found in event titles/descriptions that identify this team. */
  keywords: string[];
}

export type ContactRole =
  | "productOwner"
  | "projectManager"
  | "architect"
  | "engineeringManager"
  | "executiveSponsor"
  | "engineer";

export interface Person {
  id: ID;
  clientId: ID | null;
  name: string;
  email: string | null;
  /** Last known role, learnt from prior sessions. */
  role: ContactRole | null;
}

/** Id of a row in the admin-managed session-formats catalogue. */
export type SessionType = string;

/** Admin-managed reference tables. Each kind is one table; rows share a shape. */
export type CatalogKind = "sessionType" | "sessionOutcome" | "technology" | "useCaseType";

export interface CatalogItem {
  id: ID;
  kind: CatalogKind;
  label: string;
  description: string;
  /** Lower-cased phrases that identify this row in an invite (used by the AI engine). */
  keywords: string[];
  /** Session formats only: typical length, a weak classification signal. */
  typicalMinutes: number | null;
  archived: boolean;
  sortOrder: number;
  updatedBy: ID | null;
  updatedAt: ISODateTime | null;
}

/** A session format row (kind = "sessionType"). Kept as a named alias for readability. */
export type SessionTypeDef = CatalogItem;

/** One thing the client team worked on during the session. Stored inline on the session. */
export interface UseCase {
  id: ID;
  title: string;
  description: string;
  useCaseTypeId: ID | null;
  technologyIds: ID[];
}

export type SessionState =
  | "candidate"
  | "planned"
  | "completed"
  | "incomplete"
  | "cancelled"
  | "ignored";


/* ───────────────────────── Evidence layer ───────────────────────── */

export interface CalendarAttendee {
  email: string;
  name: string | null;
  responseStatus: "accepted" | "declined" | "tentative" | "needsAction";
  organizer: boolean;
}

export interface CalendarEvent {
  id: ID;
  /** Provider's stable id; the same event re-synced keeps this id. */
  providerEventId: string;
  recurringEventId: string | null;
  /** The member whose linked calendar produced this event. */
  ownerMemberId: ID;
  title: string;
  description: string;
  location: string | null;
  conferencing: string | null;
  start: ISODateTime;
  end: ISODateTime;
  timeZone: string;
  organizerEmail: string;
  attendees: CalendarAttendee[];
  etag: string;
  /** Hash of the normalised content; used for duplicate detection. */
  contentHash: string;
  status: "confirmed" | "cancelled";
  firstSeenSyncRunId: ID;
  lastSeenSyncRunId: ID;
}

/* ───────────────────────── Proposal layer ───────────────────────── */

export type EvidenceSource =
  | "calendar.title"
  | "calendar.description"
  | "calendar.attendees"
  | "calendar.organizer"
  | "calendar.time"
  | "calendar.location"
  | "history.client"
  | "history.team"
  | "history.people"
  | "history.tags"
  | "history.sessions"
  | "taxonomy.alias";

export interface Evidence {
  source: EvidenceSource;
  /** Short quote or fact the suggestion rests on; shown verbatim to the member. */
  excerpt: string;
  /** Optional attribution, e.g. "Priya Nair · 3 sessions". */
  attribution?: string;
}

export type SuggestionField =
  | "title"
  | "clientId"
  | "teamId"
  | "sessionType"
  | "facilitatorId"
  | "coFacilitatorIds"
  | "contacts"
  | "participantIds"
  | "participantsEstimated"
  | "technologyIds"
  | "useCases"
  | "summary";

export type SuggestionStatus = "proposed" | "accepted" | "rejected" | "edited" | "superseded";

export interface Suggestion {
  id: ID;
  candidateId: ID;
  /** Set once the candidate is confirmed into a session. */
  sessionId: ID | null;
  field: SuggestionField;
  /** The proposed value, typed per field (see FieldValue). */
  value: unknown;
  /** Human-readable rendering of the value. */
  label: string;
  confidence: number;
  evidence: Evidence[];
  status: SuggestionStatus;
  decidedBy: ID | null;
  decidedAt: ISODateTime | null;
  promptVersion: string;
}

export type ClassificationBucket = "likely" | "review" | "unlikely";

export interface Classification {
  score: number;
  bucket: ClassificationBucket;
  reasons: string[];
}

export interface Candidate {
  id: ID;
  eventId: ID;
  ownerMemberId: ID;
  classification: Classification;
  /** Points to the earlier candidate this one appears to duplicate. */
  duplicateOfCandidateId: ID | null;
  status: "open" | "confirmed" | "dismissed" | "merged";
  sessionId: ID | null;
  /** What the engine actually saw after redaction. */
  redactedText: string;
  redactions: string[];
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

/* ───────────────────────── Authoritative layer ───────────────────────── */

export type Provenance = "prefilled" | "accepted" | "manual";

export interface SessionContact {
  personId: ID;
  role: ContactRole;
}

export interface Session {
  id: ID;
  state: SessionState;
  title: string;
  clientId: ID | null;
  teamId: ID | null;
  facilitatorId: ID | null;
  coFacilitatorIds: ID[];
  contacts: SessionContact[];
  scheduledStart: ISODateTime;
  scheduledEnd: ISODateTime;
  actualStart: ISODateTime | null;
  actualEnd: ISODateTime | null;
  participantIds: ID[];
  participantsEstimated: number | null;
  sessionType: SessionType | null;
  /** Session outcome, a row of the "sessionOutcome" catalogue. */
  outcomeId: ID | null;
  /** Technologies in play across the session; use cases pick a subset. */
  technologyIds: ID[];
  useCases: UseCase[];
  summary: string;
  notes: string;
  sourceEventIds: ID[];
  /** Which layer last wrote each editable field. Missing = never set. */
  provenance: Partial<Record<EditableField, Provenance>>;
  createdBy: ID;
  createdAt: ISODateTime;
  updatedBy: ID;
  updatedAt: ISODateTime;
  version: number;
}

export type EditableField =
  | "title"
  | "clientId"
  | "teamId"
  | "facilitatorId"
  | "coFacilitatorIds"
  | "contacts"
  | "participantIds"
  | "participantsEstimated"
  | "sessionType"
  | "outcomeId"
  | "technologyIds"
  | "useCases"
  | "summary"
  | "notes"
  | "state";

export interface SyncRun {
  id: ID;
  memberId: ID;
  startedAt: ISODateTime;
  finishedAt: ISODateTime;
  eventsSeen: number;
  eventsNew: number;
  eventsUpdated: number;
  eventsUnchanged: number;
  candidatesCreated: number;
  cursor: number;
}

export type ScenarioId = "clean" | "messy" | "empty";

export interface Settings {
  showReasoning: boolean;
  scenario: ScenarioId;
}
