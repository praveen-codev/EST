import { endOfMonth, endOfWeek, format, isAfter, isBefore, startOfDay, startOfMonth, startOfWeek, subDays } from "date-fns";
import type { DataState } from "./ops";
import { sessionTypeLabel } from "./labels";
import { candidateVM } from "./selectors";
import type { Candidate, Client, ID, Member, Session, SessionState, SessionType, Tag } from "./types";

export type TimelineItemKind = "session" | "candidate";

export interface TimelineItem {
  kind: TimelineItemKind;
  id: ID;
  href: string | null;
  start: string;
  end: string;
  title: string;
  client: Client | null;
  clientLabel: string | null;
  facilitator: Member | null;
  sessionType: SessionType | null;
  sessionTypeLabel: string | null;
  tags: Tag[];
  state: SessionState;
  success: Session["successState"] | null;
  outcome: Session["automationOutcome"] | null;
  pending: number;
  participants: number;
  session: Session | null;
  candidate: Candidate | null;
  bucket: "likely" | "review" | null;
}

export type GroupBy = "day" | "week" | "client" | "member";
export type RangePreset = "upcoming" | "30d" | "month" | "all";

export interface TimelineFilters {
  groupBy: GroupBy;
  range: RangePreset;
  clientId: ID | null;
  facilitatorId: ID | null;
  tagId: ID | null;
  state: SessionState | "all";
  scope: "mine" | "everyone";
  includeCandidates: boolean;
}

export const DEFAULT_FILTERS: TimelineFilters = {
  groupBy: "day",
  range: "all",
  clientId: null,
  facilitatorId: null,
  tagId: null,
  state: "all",
  scope: "everyone",
  includeCandidates: true,
};

export function sessionItem(state: DataState, s: Session): TimelineItem {
  const client = state.clients.find((c) => c.id === s.clientId) ?? null;
  return {
    kind: "session",
    id: s.id,
    href: `/sessions/${s.id}`,
    start: s.scheduledStart,
    end: s.scheduledEnd,
    title: s.title,
    client,
    clientLabel: client?.name ?? null,
    facilitator: state.members.find((m) => m.id === s.facilitatorId) ?? null,
    sessionType: s.sessionType,
    sessionTypeLabel: sessionTypeLabel(state.sessionTypes, s.sessionType),
    tags: s.tagIds.map((id) => state.tags.find((t) => t.id === id)!).filter(Boolean),
    state: s.state,
    success: s.successState,
    outcome: s.automationOutcome,
    pending: state.suggestions.filter((x) => x.sessionId === s.id && x.status === "proposed").length,
    participants: s.participantIds.length || s.participantsEstimated || 0,
    session: s,
    candidate: null,
    bucket: null,
  };
}

export function candidateItem(state: DataState, c: Candidate): TimelineItem {
  const vm = candidateVM(state, c);
  return {
    kind: "candidate",
    id: c.id,
    href: null,
    start: vm.event.start,
    end: vm.event.end,
    title: vm.event.title,
    client: vm.client,
    clientLabel: vm.clientLabel,
    facilitator: vm.facilitator,
    sessionType: vm.sessionType,
    sessionTypeLabel: vm.sessionTypeLabel,
    tags: vm.tags,
    state: "candidate",
    success: null,
    outcome: null,
    pending: vm.suggestions.length,
    participants: vm.externalAttendees.length,
    session: null,
    candidate: c,
    bucket: c.classification.bucket === "unlikely" ? null : c.classification.bucket,
  };
}

export function allItems(state: DataState, memberId: ID, includeCandidates: boolean): TimelineItem[] {
  const sessions = state.sessions.filter((s) => s.state !== "ignored").map((s) => sessionItem(state, s));
  const candidates = includeCandidates
    ? state.candidates
        .filter((c) => c.ownerMemberId === memberId && c.status === "open" && c.classification.bucket !== "unlikely" && !c.duplicateOfCandidateId)
        .map((c) => candidateItem(state, c))
    : [];
  return [...sessions, ...candidates].sort((a, b) => Date.parse(a.start) - Date.parse(b.start));
}

export function applyFilters(items: TimelineItem[], f: TimelineFilters, memberId: ID, now = new Date()): TimelineItem[] {
  const [from, to] = rangeBounds(f.range, now);
  return items.filter((it) => {
    const start = new Date(it.start);
    if (from && isBefore(start, from)) return false;
    if (to && isAfter(start, to)) return false;
    if (f.clientId && it.client?.id !== f.clientId) return false;
    if (f.facilitatorId && it.facilitator?.id !== f.facilitatorId) return false;
    if (f.tagId && !it.tags.some((t) => t.id === f.tagId)) return false;
    if (f.state !== "all" && it.state !== f.state) return false;
    if (f.scope === "mine" && it.facilitator?.id !== memberId && it.session?.createdBy !== memberId && it.candidate?.ownerMemberId !== memberId) return false;
    return true;
  });
}

export function rangeBounds(range: RangePreset, now: Date): [Date | null, Date | null] {
  switch (range) {
    case "upcoming": return [startOfDay(now), null];
    case "30d": return [subDays(startOfDay(now), 30), null];
    case "month": return [startOfMonth(now), endOfMonth(now)];
    default: return [null, null];
  }
}

export interface TimelineGroup {
  key: string;
  label: string;
  sublabel: string | null;
  items: TimelineItem[];
}

export function groupItems(items: TimelineItem[], groupBy: GroupBy, now = new Date()): TimelineGroup[] {
  const groups = new Map<string, TimelineGroup>();
  const push = (key: string, label: string, sublabel: string | null, it: TimelineItem) => {
    const g = groups.get(key) ?? { key, label, sublabel, items: [] };
    g.items.push(it);
    groups.set(key, g);
  };
  for (const it of items) {
    const d = new Date(it.start);
    switch (groupBy) {
      case "day":
        push(format(d, "yyyy-MM-dd"), format(d, "EEEE d MMMM"), relativeDayHint(d, now), it);
        break;
      case "week": {
        const ws = startOfWeek(d, { weekStartsOn: 1 });
        push(format(ws, "yyyy-'W'II"), `Week of ${format(ws, "d MMM")}`, `${format(ws, "d MMM")} – ${format(endOfWeek(d, { weekStartsOn: 1 }), "d MMM")}`, it);
        break;
      }
      case "client":
        push(it.client?.id ?? "none", it.clientLabel ?? "No client yet", it.client?.industry ?? null, it);
        break;
      case "member":
        push(it.facilitator?.id ?? "none", it.facilitator?.name ?? "Unassigned", it.facilitator?.title ?? null, it);
        break;
    }
  }
  const out = [...groups.values()];
  if (groupBy === "day" || groupBy === "week") out.sort((a, b) => Date.parse(b.items[0]!.start) - Date.parse(a.items[0]!.start));
  else out.sort((a, b) => b.items.length - a.items.length);
  for (const g of out) g.items.sort((a, b) => (groupBy === "day" || groupBy === "week" ? Date.parse(a.start) - Date.parse(b.start) : Date.parse(b.start) - Date.parse(a.start)));
  return out;
}

function relativeDayHint(d: Date, now: Date): string | null {
  const today = startOfDay(now).getTime();
  const day = startOfDay(d).getTime();
  const diff = Math.round((day - today) / 86_400_000);
  if (diff === 0) return "Today";
  if (diff === 1) return "Tomorrow";
  if (diff === -1) return "Yesterday";
  return null;
}
