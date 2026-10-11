import { hashString, stableId } from "@/lib/domain/ids";
import type { CalendarEvent, ID } from "@/lib/domain/types";
import type { ProviderEvent } from "@/lib/mock/calendar";
import { domainOf } from "@/lib/ai/normalize";
import { ORG_DOMAIN } from "@/lib/mock/seed";

/** Hash of the fields that identify "the same meeting" regardless of which calendar it came from. */
export function contentHash(p: Pick<ProviderEvent, "title" | "start" | "end" | "attendees">): string {
  const external = p.attendees
    .map((a) => a.email.toLowerCase())
    .filter((e) => domainOf(e) !== ORG_DOMAIN)
    .sort();
  const title = p.title.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
  return hashString(`${title}|${p.start.toISOString()}|${p.end.toISOString()}|${external.join(",")}`);
}

export function eventIdFor(ownerMemberId: ID, providerEventId: string): ID {
  return stableId("evt", `${ownerMemberId}/${providerEventId}`);
}

export function candidateIdFor(ownerMemberId: ID, providerEventId: string): ID {
  return stableId("cand", `${ownerMemberId}/${providerEventId}`);
}

export function toCalendarEvent(p: ProviderEvent, ownerMemberId: ID, syncRunId: ID): CalendarEvent {
  return {
    id: eventIdFor(ownerMemberId, p.providerEventId),
    providerEventId: p.providerEventId,
    recurringEventId: p.recurringEventId,
    ownerMemberId,
    title: p.title,
    description: p.description,
    location: p.location,
    conferencing: p.conferencing,
    start: p.start.toISOString(),
    end: p.end.toISOString(),
    timeZone: p.timeZone,
    organizerEmail: p.organizerEmail,
    attendees: p.attendees,
    etag: p.etag,
    contentHash: contentHash(p),
    status: p.status,
    firstSeenSyncRunId: syncRunId,
    lastSeenSyncRunId: syncRunId,
  };
}

function overlapRatio(a: CalendarEvent, b: CalendarEvent): number {
  const aStart = Date.parse(a.start);
  const aEnd = Date.parse(a.end);
  const bStart = Date.parse(b.start);
  const bEnd = Date.parse(b.end);
  const overlap = Math.max(0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
  const shortest = Math.max(1, Math.min(aEnd - aStart, bEnd - bStart));
  return overlap / shortest;
}

function externalEmails(e: CalendarEvent): Set<string> {
  return new Set(e.attendees.map((a) => a.email.toLowerCase()).filter((x) => domainOf(x) !== ORG_DOMAIN));
}

/**
 * Finds an earlier event that this one duplicates: same meeting imported twice
 * (forwarded invite, second calendar). Uses provider id, content hash, then a
 * time-overlap + shared-attendee heuristic.
 */
export function findDuplicate(event: CalendarEvent, existing: CalendarEvent[]): CalendarEvent | null {
  for (const other of existing) {
    if (other.id === event.id || other.status === "cancelled") continue;
    if (other.providerEventId === event.providerEventId) return other;
    if (other.contentHash === event.contentHash) return other;
    if (overlapRatio(event, other) < 0.8) continue;
    const mine = externalEmails(event);
    const theirs = externalEmails(other);
    const shared = [...mine].filter((x) => theirs.has(x)).length;
    if (shared >= 2 || (shared >= 1 && mine.size <= 2 && theirs.size <= 2)) return other;
  }
  return null;
}
