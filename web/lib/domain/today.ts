import { isSameDay, isBefore, startOfDay, subDays } from "date-fns";
import type { DataState } from "./ops";
import { candidateItem, sessionItem, type TimelineItem } from "./timeline";
import type { ID } from "./types";

/** Is this member running or co-running the item? Managers/admins see only their own too. */
function isMine(it: TimelineItem, memberId: ID): boolean {
  if (it.session) return it.session.facilitatorId === memberId || it.session.coFacilitatorIds.includes(memberId) || it.session.createdBy === memberId;
  return it.candidate?.ownerMemberId === memberId;
}

export interface TodayBoard {
  today: TimelineItem[];
  /** Earlier sessions of mine whose feedback is still incomplete (last 14 days). */
  overdue: TimelineItem[];
}

/**
 * The home page: only the current member's sessions scheduled today, plus a count of
 * earlier ones still missing feedback. Candidates (unconfirmed calendar events) are
 * included so the CTA can confirm and open them in one step.
 */
export function todayBoard(state: DataState, memberId: ID, now = new Date()): TodayBoard {
  const sessions = state.sessions.filter((s) => s.state !== "ignored" && s.state !== "cancelled").map((s) => sessionItem(state, s));
  const candidates = state.candidates
    .filter((c) => c.ownerMemberId === memberId && c.status === "open" && c.classification.bucket !== "unlikely" && !c.duplicateOfCandidateId)
    .map((c) => candidateItem(state, c));
  const mine = [...sessions, ...candidates].filter((it) => isMine(it, memberId));
  const byStart = (a: TimelineItem, b: TimelineItem) => Date.parse(a.start) - Date.parse(b.start);
  const today = mine.filter((it) => isSameDay(new Date(it.start), now)).sort(byStart);
  const floor = subDays(startOfDay(now), 14);
  const overdue = mine
    .filter((it) => it.kind === "session" && isBefore(new Date(it.start), startOfDay(now)) && !isBefore(new Date(it.start), floor) && it.completeness < 1)
    .sort((a, b) => byStart(b, a));
  return { today, overdue };
}
