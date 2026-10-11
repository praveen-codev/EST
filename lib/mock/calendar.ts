import { addMinutes } from "date-fns";
import type { CalendarAttendee, ScenarioId } from "@/lib/domain/types";
import { MEMBERS, PEOPLE, at } from "./seed";

/** What a calendar provider hands back before normalisation. */
export interface ProviderEvent {
  providerEventId: string;
  recurringEventId: string | null;
  title: string;
  description: string;
  location: string | null;
  conferencing: string | null;
  start: Date;
  end: Date;
  timeZone: string;
  organizerEmail: string;
  attendees: CalendarAttendee[];
  etag: string;
  status: "confirmed" | "cancelled";
}

interface RawSpec {
  id: string;
  title: string;
  desc?: string;
  day: number;
  hour: number;
  minute?: number;
  duration: number;
  organizer: string;
  attendees: string[];
  recurring?: string;
  location?: string;
  conferencing?: string;
  status?: "confirmed" | "cancelled";
  etag?: string;
}

const TZ = "America/New_York";

function nameFor(email: string): string | null {
  const known =
    PEOPLE.find((p) => p.email?.toLowerCase() === email.toLowerCase())?.name ??
    MEMBERS.find((m) => m.email.toLowerCase() === email.toLowerCase())?.name;
  if (known) return known;
  const local = email.split("@")[0] ?? "";
  if (!local.includes(".")) return null;
  return local
    .split(".")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

function toProviderEvent(anchor: Date, s: RawSpec): ProviderEvent {
  const start = at(anchor, s.day, s.hour, s.minute ?? 0);
  return {
    providerEventId: s.id,
    recurringEventId: s.recurring ?? null,
    title: s.title,
    description: s.desc ?? "",
    location: s.location ?? null,
    conferencing: s.conferencing ?? null,
    start,
    end: addMinutes(start, s.duration),
    timeZone: TZ,
    organizerEmail: s.organizer,
    attendees: s.attendees.map((email) => ({
      email,
      name: nameFor(email),
      responseStatus: "accepted",
      organizer: email === s.organizer,
    })),
    etag: s.etag ?? `etag-${s.id}-1`,
    status: s.status ?? "confirmed",
  };
}

const ALEX = "alex@cognition.ai";
const PRIYA = "priya@cognition.ai";
const MARCUS = "marcus@cognition.ai";
const SOFIA = "sofia@cognition.ai";
const DANA = "dana@cognition.ai";

const MEET = "https://meet.google.com/abc-defg-hij";

/* ───────────────────────── Alex (default persona) ───────────────────────── */

const alexLikely: RawSpec[] = [
  {
    id: "evt-acme-oh-1",
    title: "Acme Platform — Devin office hours",
    desc: "Weekly office hours for the Platform team. Topics: Java 21 upgrade stragglers, Jenkins pipeline credentials. PO: Jane Doe.\n\nJoin: " + MEET + "\nPasscode: 4421",
    day: -3, hour: 10, duration: 60, organizer: ALEX,
    attendees: [ALEX, "jane.doe@acmefinancial.com", "lena.fischer@acmefinancial.com", "omar.haddad@acmefinancial.com"],
    conferencing: MEET,
  },
  {
    id: "evt-vantage-d1",
    title: "Vantage Claims — Hands-on Workshop Day 1",
    desc: "Day 1 of 2. ~20 engineers from the claims modernization team. Focus: COBOL claims engine documentation and Java unit tests with Devin. Sponsor: Nadia Rahman. Architect: Felix Brandt.\n\nDial-in: +1 555 010 2233 PIN 889 221",
    day: 1, hour: 9, duration: 150, organizer: "zoe.martin@vantage-ins.com",
    attendees: [ALEX, MARCUS, "zoe.martin@vantage-ins.com", "felix.brandt@vantage-ins.com", "nadia.rahman@vantage-ins.com", "priyanka.rao@vantage-ins.com", "ahmed.saleh@vantage-ins.com", "julia.moreau@vantage-ins.com"],
    location: "Vantage HQ, 12th floor",
  },
  {
    id: "evt-vantage-d2",
    title: "Vantage Claims — Hands-on Workshop Day 2",
    desc: "Day 2 of 2. Continue COBOL documentation; start Java unit test generation on the claims-rules service. Architect: Felix Brandt.",
    day: 2, hour: 9, duration: 150, organizer: "zoe.martin@vantage-ins.com",
    attendees: [ALEX, MARCUS, "zoe.martin@vantage-ins.com", "felix.brandt@vantage-ins.com", "priyanka.rao@vantage-ins.com", "ahmed.saleh@vantage-ins.com", "julia.moreau@vantage-ins.com"],
    location: "Vantage HQ, 12th floor",
  },
  {
    id: "evt-northwind-fu",
    title: "Northwind — Devin follow-up: microservices decomposition",
    desc: "Follow-up on the WMS decomposition plan. Review Devin's proposed bounded-context split and the GitLab CI changes. PO: Maya Okafor.",
    day: 3, hour: 14, duration: 60, organizer: ALEX,
    attendees: [ALEX, SOFIA, "maya.okafor@northwind.io", "ben.carter@northwind.io"],
    conferencing: MEET,
  },
  {
    id: "evt-meridian-training",
    title: "Meridian Data Platform — Devin training for new joiners",
    desc: "Training for 8 new data engineers on Devin and the Jira bug-triage workflow for the ingestion pipelines (Python / Postgres / GCP). EM: Victor Mensah.",
    day: 4, hour: 11, duration: 90, organizer: "victor.mensah@meridianhealth.org",
    attendees: [ALEX, "victor.mensah@meridianhealth.org", "luis.ortega@meridianhealth.org", "amara.osei@meridianhealth.org", "ken.watanabe@meridianhealth.org", "sara.lindqvist@meridianhealth.org"],
    conferencing: MEET,
  },
  {
    id: "evt-acme-pay-review",
    title: "Acme Payments — Devin Review rollout workshop",
    desc: "Rolling out Devin Review to all Kafka consumer repos (Kotlin). Architect: Raj Patel. ~10 engineers from Payments.",
    day: 9, hour: 14, duration: 90, organizer: MARCUS,
    attendees: [ALEX, MARCUS, "chris.lowe@acmefinancial.com", "yuki.tanaka@acmefinancial.com", "raj.patel@acmefinancial.com"],
    conferencing: MEET,
  },
];

const alexReview: RawSpec[] = [
  {
    id: "evt-brightline-intro",
    title: "Intro call: Brightline Software <> Cognition",
    desc: "Intro to Devin for Brightline's platform team. They run Go services on GitHub Actions and want help with dependency upgrades.",
    day: 8, hour: 15, duration: 45, organizer: DANA,
    attendees: [ALEX, DANA, "kate.morgan@brightlinesw.com", "ravi.shah@brightlinesw.com"],
    conferencing: MEET,
  },
  {
    id: "evt-helios-sync",
    title: "Helios Grid — enablement sync",
    day: -1, hour: 13, duration: 30, organizer: ALEX,
    attendees: [ALEX, "sam.whitaker@helios-energy.com"],
    conferencing: MEET,
  },
];

const alexMessyExtras: RawSpec[] = [
  {
    id: "evt-vantage-d1-copy",
    title: "Vantage Claims Workshop (Day 1)",
    desc: "Forwarded from Zoe's calendar.",
    day: 1, hour: 9, duration: 150, organizer: "zoe.martin@vantage-ins.com",
    attendees: [ALEX, "zoe.martin@vantage-ins.com", "felix.brandt@vantage-ins.com", "nadia.rahman@vantage-ins.com"],
    location: "Vantage HQ, 12th floor",
  },
  {
    id: "evt-incomplete-workshop",
    title: "Workshop",
    day: 10, hour: 10, duration: 90, organizer: "ingrid.solberg@northwind.io",
    attendees: [ALEX, "ingrid.solberg@northwind.io"],
  },
  {
    id: "evt-orbital-cancelled",
    title: "Orbital Storefront — office hours",
    desc: "Cancelled: Pavel out this week.",
    day: -2, hour: 10, duration: 60, organizer: SOFIA,
    attendees: [ALEX, SOFIA, "pavel.novak@orbitalretail.com"],
    status: "cancelled",
  },
];

const alexNoise: RawSpec[] = [
  { id: "evt-1on1", title: "Alex / Dana 1:1", day: -4, hour: 16, duration: 30, organizer: DANA, attendees: [ALEX, DANA] },
  { id: "evt-standup-1", recurring: "rec-standup", title: "Enablement team standup", day: -3, hour: 9, minute: 15, duration: 15, organizer: DANA, attendees: [ALEX, PRIYA, MARCUS, SOFIA, DANA] },
  { id: "evt-standup-2", recurring: "rec-standup", title: "Enablement team standup", day: 0, hour: 9, minute: 15, duration: 15, organizer: DANA, attendees: [ALEX, PRIYA, MARCUS, SOFIA, DANA] },
  { id: "evt-standup-3", recurring: "rec-standup", title: "Enablement team standup", day: 3, hour: 9, minute: 15, duration: 15, organizer: DANA, attendees: [ALEX, PRIYA, MARCUS, SOFIA, DANA] },
  { id: "evt-interview", title: "Interview — Applied AI Engineer candidate", desc: "Panel interview, system design round.", day: 5, hour: 15, duration: 60, organizer: "recruiting@cognition.ai", attendees: [ALEX, SOFIA, "recruiting@cognition.ai"] },
  { id: "evt-focus", title: "Focus time", day: 0, hour: 12, duration: 60, organizer: ALEX, attendees: [ALEX] },
  { id: "evt-dentist", title: "Dentist", day: 6, hour: 8, duration: 60, organizer: ALEX, attendees: [ALEX] },
];

const alexBatch1 = (updatedDesc: boolean): RawSpec[] => [
  {
    ...alexLikely[0]!,
    desc: alexLikely[0]!.desc + (updatedDesc ? "\n\nUpdate: Architect Raj Patel joining to cover the Jenkins pipeline migration." : ""),
    etag: updatedDesc ? "etag-evt-acme-oh-1-2" : undefined,
  },
  { ...alexLikely[3]! },
  {
    id: "evt-helios-vdi-ws",
    title: "Helios Grid — Hands-on Workshop (VDI environment)",
    desc: "Second attempt at hands-on in the restricted network. Egress allow-list approved. Python services on Kubernetes; goal is unit test generation. PM: Sam Whitaker. Architect: Aisha Bello.",
    day: 11, hour: 13, duration: 120, organizer: "sam.whitaker@helios-energy.com",
    attendees: [ALEX, "sam.whitaker@helios-energy.com", "aisha.bello@helios-energy.com", "noah.kim@helios-energy.com", "li.wei@helios-energy.com"],
    location: "Helios Operations Center",
  },
  {
    id: "evt-meridian-clinical-fu",
    title: "Meridian Clinical — Angular migration follow-up",
    desc: "Finish the remaining 5 components now that Bitbucket SSO is sorted. PO: Grace Liu.",
    day: 12, hour: 11, duration: 75, organizer: PRIYA,
    attendees: [ALEX, PRIYA, "grace.liu@meridianhealth.org", "hannah.weiss@meridianhealth.org"],
    conferencing: MEET,
  },
];

const alexBatch2: RawSpec[] = [
  {
    id: "evt-orbital-q4-kickoff",
    title: "Orbital Storefront — Q4 pilot kickoff",
    desc: "Pilot approved. Kick off with the wider storefront team (~12 engineers). React / TypeScript / Node; Slack-triggered feature work. PO: Pavel Novak.",
    day: 15, hour: 10, duration: 90, organizer: SOFIA,
    attendees: [ALEX, SOFIA, "pavel.novak@orbitalretail.com", "emma.clarke@orbitalretail.com", "jon.park@orbitalretail.com", "mia.rossi@orbitalretail.com"],
    conferencing: MEET,
  },
  {
    id: "evt-northwind-readout",
    title: "Northwind — exec readout",
    desc: "Readout to Northwind leadership on pilot results.",
    day: 16, hour: 16, duration: 30, organizer: SOFIA,
    attendees: [ALEX, SOFIA, DANA, "maya.okafor@northwind.io"],
    conferencing: MEET,
  },
];

/* ───────────────────────── Other personas ───────────────────────── */

const priyaBatch0: RawSpec[] = [
  {
    id: "evt-p-meridian-clinical-d2",
    title: "Meridian Clinical — Angular migration hands-on (part 2)",
    desc: "Continue the AngularJS → Angular migration of the scheduling app. PO: Grace Liu. ~11 engineers.",
    day: 2, hour: 11, duration: 75, organizer: PRIYA,
    attendees: [PRIYA, "grace.liu@meridianhealth.org", "hannah.weiss@meridianhealth.org", "luis.ortega@meridianhealth.org"],
    conferencing: MEET,
  },
  {
    id: "evt-p-acme-oh",
    title: "Acme Platform — office hours",
    desc: "Weekly. Java 21 stragglers. PO: Jane Doe.",
    day: 4, hour: 10, duration: 60, organizer: PRIYA,
    attendees: [PRIYA, "jane.doe@acmefinancial.com", "lena.fischer@acmefinancial.com"],
    conferencing: MEET,
  },
  { id: "evt-p-standup", recurring: "rec-standup", title: "Enablement team standup", day: 0, hour: 9, minute: 15, duration: 15, organizer: DANA, attendees: [ALEX, PRIYA, MARCUS, SOFIA, DANA] },
  { id: "evt-p-1on1", title: "Priya / Dana 1:1", day: 1, hour: 16, duration: 30, organizer: DANA, attendees: [PRIYA, DANA] },
];

const marcusBatch0: RawSpec[] = [
  { ...alexLikely[5]!, id: "evt-m-acme-pay-review" },
  {
    id: "evt-m-helios-oh",
    title: "Helios Grid — office hours",
    desc: "Open Q&A after the blocked hands-on. Architect: Aisha Bello.",
    day: 5, hour: 13, duration: 60, organizer: MARCUS,
    attendees: [MARCUS, "aisha.bello@helios-energy.com", "noah.kim@helios-energy.com"],
    conferencing: MEET,
  },
  { id: "evt-m-standup", recurring: "rec-standup", title: "Enablement team standup", day: 0, hour: 9, minute: 15, duration: 15, organizer: DANA, attendees: [ALEX, PRIYA, MARCUS, SOFIA, DANA] },
];

const sofiaBatch0: RawSpec[] = [
  { ...alexBatch2[0]!, id: "evt-s-orbital-q4-kickoff" },
  {
    id: "evt-s-northwind-ws",
    title: "Northwind WMS — hands-on: service extraction",
    desc: "Extract the first bounded context (inventory) with Devin. .NET / Azure / GitLab. Architect: Ben Carter. ~10 engineers.",
    day: 8, hour: 9, duration: 120, organizer: SOFIA,
    attendees: [SOFIA, "maya.okafor@northwind.io", "ben.carter@northwind.io", "ingrid.solberg@northwind.io", "diego.ruiz@northwind.io"],
    location: "Northwind Rotterdam office",
  },
  { id: "evt-s-standup", recurring: "rec-standup", title: "Enablement team standup", day: 0, hour: 9, minute: 15, duration: 15, organizer: DANA, attendees: [ALEX, PRIYA, MARCUS, SOFIA, DANA] },
];

const danaBatch0: RawSpec[] = [
  { id: "evt-d-standup", recurring: "rec-standup", title: "Enablement team standup", day: 0, hour: 9, minute: 15, duration: 15, organizer: DANA, attendees: [ALEX, PRIYA, MARCUS, SOFIA, DANA] },
  { ...alexReview[0]!, id: "evt-d-brightline", attendees: [DANA, ALEX, "kate.morgan@brightlinesw.com", "ravi.shah@brightlinesw.com"] },
];

/* ───────────────────────── Scenario assembly ───────────────────────── */

type Batches = Record<string, RawSpec[][]>;

function scenarioBatches(scenario: ScenarioId): Batches {
  if (scenario === "empty") return {};
  const messy = scenario === "messy";
  return {
    [ALEX]: [
      [...alexLikely, ...alexReview, ...(messy ? alexMessyExtras : []), ...alexNoise],
      alexBatch1(messy),
      alexBatch2,
    ],
    [PRIYA]: [priyaBatch0],
    [MARCUS]: [marcusBatch0],
    [SOFIA]: [sofiaBatch0],
    [DANA]: [danaBatch0],
  };
}

export interface ProviderPage {
  events: ProviderEvent[];
  nextCursor: number;
  hasMore: boolean;
}

/**
 * Mock Google Calendar: returns the next incremental page for a member.
 * `cursor` 0 is the initial bounded lookback; later cursors are incremental syncs.
 */
export function fetchCalendarPage(
  memberEmail: string,
  scenario: ScenarioId,
  cursor: number,
  anchor: Date,
): ProviderPage {
  const batches = scenarioBatches(scenario)[memberEmail] ?? [];
  const page = batches[cursor] ?? [];
  return {
    events: page.map((s) => toProviderEvent(anchor, s)),
    nextCursor: Math.min(cursor + 1, batches.length),
    hasMore: cursor + 1 < batches.length,
  };
}

export function remainingPages(memberEmail: string, scenario: ScenarioId, cursor: number): number {
  const batches = scenarioBatches(scenario)[memberEmail] ?? [];
  return Math.max(0, batches.length - cursor);
}
