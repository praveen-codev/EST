import type {
  AutomationOutcome,
  ContactRole,
  DevinAsk,
  SessionState,
  SessionType,
  SessionTypeDef,
  SuccessState,
  TagCategory,
} from "./types";

/** Label for a session type id, falling back to the raw id for archived/unknown rows. */
export function sessionTypeLabel(types: SessionTypeDef[], id: SessionType | null): string | null {
  if (!id) return null;
  return types.find((t) => t.id === id)?.label ?? id;
}

export const SESSION_STATE_LABELS: Record<SessionState, string> = {
  candidate: "Candidate",
  planned: "Planned",
  completed: "Completed",
  incomplete: "Incomplete",
  cancelled: "Cancelled",
  ignored: "Ignored",
};

export const DEVIN_ASK_LABELS: Record<DevinAsk, string> = {
  plan: "Plan",
  supportRequest: "Support request",
  implementation: "Implementation",
  investigation: "Investigation",
};

export const AUTOMATION_OUTCOME_LABELS: Record<AutomationOutcome, string> = {
  completed: "Completed",
  partial: "Partial",
  blocked: "Blocked",
  notApplicable: "Not applicable",
};

export const SUCCESS_STATE_LABELS: Record<SuccessState, string> = {
  successful: "Successful",
  partial: "Partial",
  unsuccessful: "Unsuccessful",
  unknown: "Not yet rated",
};

export const CONTACT_ROLE_LABELS: Record<ContactRole, string> = {
  productOwner: "Product owner",
  projectManager: "Project manager",
  architect: "Architect",
  engineeringManager: "Engineering manager",
  executiveSponsor: "Executive sponsor",
  engineer: "Engineer",
};

export const TAG_CATEGORY_LABELS: Record<TagCategory, string> = {
  technology: "Technology",
  migration: "Migration",
  useCase: "Use case",
  workflow: "Workflow",
  blocker: "Blocker",
  outcome: "Outcome",
};

export const DEVIN_ASKS = Object.keys(DEVIN_ASK_LABELS) as DevinAsk[];
export const AUTOMATION_OUTCOMES = Object.keys(AUTOMATION_OUTCOME_LABELS) as AutomationOutcome[];
export const SUCCESS_STATES = Object.keys(SUCCESS_STATE_LABELS) as SuccessState[];
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
  participantsEstimated: "Estimated participants",
  devinAsk: "Devin ask",
  tagIds: "Tags",
  summary: "Summary",
  notes: "Notes",
  successState: "Success",
  automationOutcome: "Automation outcome",
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
