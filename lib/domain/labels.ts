import type { CatalogItem, CatalogKind, ContactRole, ID, SessionState } from "./types";

/** Rows of one catalogue, active first by sort order. Archived rows are kept only when `includeArchived`. */
export function catalogOf(catalog: CatalogItem[], kind: CatalogKind, includeArchived = false): CatalogItem[] {
  return catalog.filter((c) => c.kind === kind && (includeArchived || !c.archived)).sort((a, b) => a.sortOrder - b.sortOrder);
}

/** Label for a catalogue row id, falling back to the raw id for archived/unknown rows. */
export function catalogLabel(catalog: CatalogItem[], id: ID | null): string | null {
  if (!id) return null;
  return catalog.find((c) => c.id === id)?.label ?? id;
}

export const sessionTypeLabel = catalogLabel;

export const CATALOG_KIND_META: Record<CatalogKind, { title: string; singular: string; hint: string; hasKeywords: boolean; hasMinutes: boolean }> = {
  sessionType: { title: "Session formats", singular: "format", hint: "The only meetings that count as enablement sessions. Keywords are matched against calendar invites.", hasKeywords: true, hasMinutes: true },
  sessionOutcome: { title: "Session outcomes", singular: "outcome", hint: "What a session produced. Picked once per session on the feedback form.", hasKeywords: false, hasMinutes: false },
  technology: { title: "Technologies", singular: "technology", hint: "Languages, tools and platforms. Keywords let the AI suggest them from invite text.", hasKeywords: true, hasMinutes: false },
  useCaseType: { title: "Use-case types", singular: "use-case type", hint: "How use cases are classified for reporting. Keywords let the AI pre-select a type.", hasKeywords: true, hasMinutes: false },
};

export const SESSION_STATE_LABELS: Record<SessionState, string> = {
  candidate: "Candidate",
  planned: "Planned",
  completed: "Completed",
  incomplete: "Incomplete",
  cancelled: "Cancelled",
  ignored: "Ignored",
};

export const CONTACT_ROLE_LABELS: Record<ContactRole, string> = {
  productOwner: "Product owner",
  projectManager: "Project manager",
  architect: "Architect",
  engineeringManager: "Engineering manager",
  executiveSponsor: "Executive sponsor",
  engineer: "Engineer",
};

export const CONTACT_ROLES = Object.keys(CONTACT_ROLE_LABELS) as ContactRole[];

export const FIELD_LABELS: Record<string, string> = {
  title: "Title",
  clientId: "Client",
  teamId: "Team",
  sessionType: "Session type",
  facilitatorId: "Facilitator",
  coFacilitatorIds: "Co-facilitators",
  contacts: "Contacts",
  participantIds: "Participants",
  participantsEstimated: "Participants",
  technologyIds: "Technologies",
  useCases: "Use cases",
  outcomeId: "Session outcome",
  summary: "Summary",
  notes: "Notes",
  state: "State",
};

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join("");
}
