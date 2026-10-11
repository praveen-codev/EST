import { differenceInMinutes } from "date-fns";
import type { CalendarEvent } from "@/lib/domain/types";
import { ORG_DOMAIN } from "@/lib/mock/seed";

export interface NormalizedAttendee {
  email: string;
  name: string | null;
  domain: string;
  isInternal: boolean;
  organizer: boolean;
}

export interface NormalizedEvent {
  title: string;
  description: string;
  /** Lower-cased title + description used for keyword scans. */
  text: string;
  durationMin: number;
  start: Date;
  end: Date;
  attendees: NormalizedAttendee[];
  internal: NormalizedAttendee[];
  external: NormalizedAttendee[];
  externalDomains: string[];
  organizer: NormalizedAttendee | null;
  cancelled: boolean;
  recurring: boolean;
}

export function domainOf(email: string): string {
  return email.split("@")[1]?.toLowerCase() ?? "";
}

export function normalizeEvent(event: CalendarEvent): NormalizedEvent {
  const attendees: NormalizedAttendee[] = event.attendees.map((a) => {
    const domain = domainOf(a.email);
    return {
      email: a.email.toLowerCase(),
      name: a.name,
      domain,
      isInternal: domain === ORG_DOMAIN,
      organizer: a.organizer || a.email.toLowerCase() === event.organizerEmail.toLowerCase(),
    };
  });
  const internal = attendees.filter((a) => a.isInternal);
  const external = attendees.filter((a) => !a.isInternal);
  const start = new Date(event.start);
  const end = new Date(event.end);
  return {
    title: event.title.trim(),
    description: event.description.trim(),
    text: `${event.title}\n${event.description}`.toLowerCase(),
    durationMin: Math.max(0, differenceInMinutes(end, start)),
    start,
    end,
    attendees,
    internal,
    external,
    externalDomains: [...new Set(external.map((a) => a.domain))].sort(),
    organizer: attendees.find((a) => a.organizer) ?? null,
    cancelled: event.status === "cancelled",
    recurring: event.recurringEventId !== null,
  };
}
