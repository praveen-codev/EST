import { describe, expect, it } from "vitest";
import { EMPTY_STATE, fromSnapshot, type Snapshot } from "@/lib/domain/state";
import { allItems, applyFilters, DEFAULT_FILTERS, groupItems } from "@/lib/domain/timeline";
import { todayBoard } from "@/lib/domain/today";
import { sessionIdForEvent } from "@/lib/domain/tiers";

const NOW = new Date("2026-10-11T12:00:00Z");
const me = { id: "mem_alex", name: "Alex Rivera", email: "alex@cognition.ai", title: "AAE", role: "MEMBER" as const, hue: 262 };

function snapshot(): Snapshot {
  return {
    me,
    features: { devMode: true, mockGoogle: true, scenario: "messy", remainingPages: 2 },
    members: [me],
    clients: [{ id: "cli_acme", name: "Acme Financial", domains: ["acmefinancial.com"], industry: null, hue: 220 }],
    teams: [],
    people: [{ id: "per_jane", clientId: "cli_acme", name: "Jane Doe", email: "jane.doe@acmefinancial.com", roleId: "productOwner" }],
    catalog: [{ id: "101", kind: "sessionType", label: "101 session", description: "", keywords: ["101"], typicalMinutes: 90, archived: false, sortOrder: 0, updatedBy: null, updatedAt: null }],
    sessions: [
      {
        id: "ses_1", state: "completed", title: "Acme 101", clientId: "cli_acme", teamId: null, facilitatorId: "mem_alex", coFacilitatorIds: [], contacts: [{ personId: "per_jane", roleId: "productOwner" }],
        scheduledStart: "2026-10-11T08:00:00Z", scheduledEnd: "2026-10-11T09:30:00Z", actualStart: null, actualEnd: null, participantIds: [], participantsEstimated: 10, sessionType: "101", outcomeId: null,
        technologyIds: [], useCases: [], summary: "", notes: "", sourceEventIds: ["evt_1"], provenance: { clientId: "prefilled" }, createdBy: "mem_alex", createdAt: "2026-10-11T09:40:00Z", updatedBy: "mem_alex", updatedAt: "2026-10-11T09:40:00Z", version: 1,
      },
      {
        id: "ses_old", state: "completed", title: "Acme kickoff", clientId: "cli_acme", teamId: null, facilitatorId: "mem_alex", coFacilitatorIds: [], contacts: [],
        scheduledStart: "2026-10-05T08:00:00Z", scheduledEnd: "2026-10-05T09:30:00Z", actualStart: null, actualEnd: null, participantIds: [], participantsEstimated: null, sessionType: "101", outcomeId: null,
        technologyIds: [], useCases: [], summary: "", notes: "", sourceEventIds: [], provenance: {}, createdBy: "mem_alex", createdAt: "2026-10-05T09:40:00Z", updatedBy: "mem_alex", updatedAt: "2026-10-05T09:40:00Z", version: 1,
      },
    ],
    candidates: [
      { id: "cand_2", eventId: "evt_2", ownerMemberId: "mem_alex", classification: { score: 0.9, bucket: "likely", reasons: [] }, duplicateOfCandidateId: null, status: "open", sessionId: null, redactedText: "", redactions: [], createdAt: "2026-10-11T07:00:00Z", updatedAt: "2026-10-11T07:00:00Z" },
    ],
    suggestions: [
      { id: "sug_1", candidateId: "cand_2", sessionId: null, field: "contacts", value: { personId: "per_jane", roleId: "productOwner" }, label: "Jane Doe · Product owner", confidence: 0.92, evidence: [], status: "proposed", promptVersion: "x", decidedBy: null, decidedAt: null },
      { id: "sug_2", candidateId: "cand_2", sessionId: null, field: "clientId", value: { name: "Globex", domain: "globex.com" }, label: "New client: Globex", confidence: 0.62, evidence: [], status: "proposed", promptVersion: "x", decidedBy: null, decidedAt: null },
    ],
    events: [
      { id: "evt_2", providerEventId: "p2", recurringEventId: null, ownerMemberId: "mem_alex", title: "Acme Payments — Ask me anything", description: "", location: null, conferencing: null, start: "2026-10-11T14:00:00Z", end: "2026-10-11T14:45:00Z", timeZone: "UTC", organizerEmail: "alex@cognition.ai", attendees: [{ email: "alex@cognition.ai", name: "Alex", responseStatus: "accepted", organizer: true }], etag: "e", contentHash: "h", status: "confirmed" },
    ],
    syncRuns: [{ id: "run_1", identityId: "idn_1", kind: "initial", startedAt: "2026-10-11T06:00:00Z", finishedAt: "2026-10-11T06:00:01Z", status: "ok", eventsSeen: 1, eventsNew: 1, eventsUpdated: 0, eventsUnchanged: 0, candidatesCreated: 1, error: null }],
    identities: [{ id: "idn_1", memberId: "mem_alex", provider: "mock", email: "alex@cognition.ai", displayName: null, scopes: [], status: "ACTIVE", lastSyncAt: null, lastError: null }],
  } as unknown as Snapshot;
}

describe("snapshot mapping", () => {
  const state = fromSnapshot(snapshot(), EMPTY_STATE.settings);

  it("maps wire shapes onto the model the selectors expect", () => {
    expect(state.currentMemberId).toBe("mem_alex");
    expect(state.people[0]!.role).toBe("productOwner");
    expect(state.sessions[0]!.contacts[0]).toEqual({ personId: "per_jane", role: "productOwner" });
    expect(state.suggestions[0]!.value).toEqual({ personId: "per_jane", role: "productOwner" });
    expect(state.suggestions[1]!.value).toEqual({ create: { name: "Globex", domain: "globex.com" } });
    expect(state.syncRuns[0]!.memberId).toBe("mem_alex");
    expect(state.settings.scenario).toBe("messy");
    expect(state.features.remainingPages).toBe(2);
  });

  it("derives the same session id the backend will assign to a confirmed event", () => {
    expect(sessionIdForEvent("evt_abc")).toMatch(/^ses_[0-9a-f]{16}$/);
    expect(sessionIdForEvent("evt_abc")).toBe(sessionIdForEvent("evt_abc"));
  });

  it("today shows only my items scheduled today and counts earlier incomplete sessions as overdue", () => {
    const board = todayBoard(state, "mem_alex", NOW);
    expect(board.today.map((i) => i.title)).toEqual(["Acme 101", "Acme Payments — Ask me anything"]);
    expect(board.overdue.map((i) => i.title)).toEqual(["Acme kickoff"]);
  });

  it("timeline mixes sessions and open candidates, filters by client, groups by day newest first", () => {
    const items = allItems(state, "mem_alex", true);
    expect(items.filter((i) => i.kind === "candidate")).toHaveLength(1);
    const acme = applyFilters(items, { ...DEFAULT_FILTERS, clientId: "cli_acme" }, "mem_alex", NOW);
    expect(acme.every((i) => i.client?.id === "cli_acme")).toBe(true);
    const groups = groupItems(items, "day", NOW);
    for (let i = 1; i < groups.length; i++) expect(Date.parse(groups[i - 1]!.items[0]!.start)).toBeGreaterThanOrEqual(Date.parse(groups[i]!.items[0]!.start));
  });
});
