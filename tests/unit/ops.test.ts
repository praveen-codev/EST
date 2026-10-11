import { describe, expect, it } from "vitest";
import {
  acceptAllForSession,
  confirmCandidate,
  decideSuggestion,
  initialState,
  mergeDuplicate,
  moveSessionType,
  runSync,
  updateSession,
  upsertSessionType,
  type DataState,
} from "@/lib/domain/ops";
import { clientByName, DEFAULT_MEMBER_ID } from "@/lib/mock/seed";

const NOW = new Date("2026-10-11T12:00:00Z");

function find(state: DataState, providerEventId: string) {
  const event = state.events.find((e) => e.providerEventId === providerEventId)!;
  const cand = state.candidates.find((c) => c.eventId === event.id)!;
  return { event, cand };
}

describe("sync + dedupe", () => {
  it("initial sync creates candidates, never sessions", () => {
    const before = initialState("messy", NOW);
    const { state, run } = runSync(before, NOW);
    expect(run.eventsNew).toBeGreaterThan(10);
    expect(state.candidates.length).toBe(run.eventsNew);
    expect(state.sessions.length).toBe(before.sessions.length);
    expect(state.candidates.every((c) => c.status === "open")).toBe(true);
  });

  it("flags a forwarded copy of the same meeting as a duplicate", () => {
    const { state } = runSync(initialState("messy", NOW), NOW);
    const original = find(state, "evt-vantage-d1").cand;
    const copy = find(state, "evt-vantage-d1-copy").cand;
    expect(copy.duplicateOfCandidateId).toBe(original.id);
    expect(original.duplicateOfCandidateId).toBeNull();
  });

  it("re-syncing an unchanged event creates no new candidate or session", () => {
    const first = runSync(initialState("messy", NOW), NOW);
    const second = runSync(first.state, NOW);
    expect(second.run.eventsUnchanged).toBeGreaterThan(0);
    const northwind = second.state.candidates.filter((c) => c.eventId === find(second.state, "evt-northwind-fu").event.id);
    expect(northwind).toHaveLength(1);
    expect(second.state.sessions.length).toBe(first.state.sessions.length);
  });

  it("an updated event refreshes evidence and supersedes stale proposals", () => {
    const first = runSync(initialState("messy", NOW), NOW);
    const { cand } = find(first.state, "evt-acme-oh-1");
    const second = runSync(first.state, NOW);
    expect(second.run.eventsUpdated).toBe(1);
    const event = find(second.state, "evt-acme-oh-1").event;
    expect(event.description).toMatch(/Raj Patel/);
    const contacts = second.state.suggestions.filter((s) => s.candidateId === cand.id && s.field === "contacts" && s.status === "proposed");
    expect(contacts.some((c) => c.label.startsWith("Raj Patel"))).toBe(true);
  });
});

describe("confirm + provenance", () => {
  it("one-click confirm creates a session with prefilled fields and keeps lower-confidence chips pending", () => {
    const { state } = runSync(initialState("messy", NOW), NOW);
    const { cand } = find(state, "evt-acme-oh-1");
    const next = confirmCandidate(state, cand.id, DEFAULT_MEMBER_ID, NOW);
    const session = next.sessions.find((s) => s.sourceEventIds.includes(cand.eventId))!;
    expect(session.clientId).toBe(clientByName("Acme Financial").id);
    expect(session.sessionType).toBe("101");
    expect(session.provenance.clientId).toBe("prefilled");
    expect(session.tagIds.length).toBeGreaterThan(0);
    const pending = next.suggestions.filter((s) => s.sessionId === session.id && s.status === "proposed");
    expect(pending.length).toBeGreaterThan(0);
    expect(pending.every((s) => s.confidence < 0.85)).toBe(true);
    expect(next.candidates.find((c) => c.id === cand.id)!.status).toBe("confirmed");
  });

  it("manual edits survive a later sync that changes the event", () => {
    const first = runSync(initialState("messy", NOW), NOW);
    const { cand } = find(first.state, "evt-acme-oh-1");
    let state = confirmCandidate(first.state, cand.id, DEFAULT_MEMBER_ID, NOW);
    const session = state.sessions.find((s) => s.sourceEventIds.includes(cand.eventId))!;
    state = updateSession(state, session.id, { title: "My own title", participantsEstimated: 42 }, DEFAULT_MEMBER_ID, NOW);
    const after = runSync(state, NOW).state;
    const updated = after.sessions.find((s) => s.id === session.id)!;
    expect(updated.title).toBe("My own title");
    expect(updated.participantsEstimated).toBe(42);
    expect(updated.provenance.title).toBe("manual");
    const titleChips = after.suggestions.filter((s) => s.sessionId === session.id && s.field === "title" && s.status === "proposed");
    expect(titleChips).toHaveLength(0);
  });

  it("accepting a chip applies it; rejecting records the decision; accept-all respects the chip threshold", () => {
    const { state } = runSync(initialState("messy", NOW), NOW);
    const { cand } = find(state, "evt-acme-oh-1");
    let next = confirmCandidate(state, cand.id, DEFAULT_MEMBER_ID, NOW);
    const session = next.sessions.find((s) => s.sourceEventIds.includes(cand.eventId))!;
    const chips = next.suggestions.filter((s) => s.sessionId === session.id && s.status === "proposed");
    const tagChip = chips.find((c) => c.field === "tagIds")!;
    next = decideSuggestion(next, tagChip.id, "accept", DEFAULT_MEMBER_ID, NOW);
    expect(next.sessions.find((s) => s.id === session.id)!.tagIds).toContain(tagChip.value);
    expect(next.suggestions.find((s) => s.id === tagChip.id)!.status).toBe("accepted");
    const other = next.suggestions.find((s) => s.sessionId === session.id && s.status === "proposed")!;
    next = decideSuggestion(next, other.id, "reject", DEFAULT_MEMBER_ID, NOW);
    expect(next.suggestions.find((s) => s.id === other.id)!.status).toBe("rejected");
    next = acceptAllForSession(next, session.id, DEFAULT_MEMBER_ID, 0.5, NOW);
    const remaining = next.suggestions.filter((s) => s.sessionId === session.id && s.status === "proposed");
    expect(remaining.every((s) => s.confidence < 0.5)).toBe(true);
  });

  it("confirming a new-client suggestion materialises the client and links its people", () => {
    const { state } = runSync(initialState("messy", NOW), NOW);
    const { cand } = find(state, "evt-brightline-intro");
    let next = confirmCandidate(state, cand.id, DEFAULT_MEMBER_ID, NOW);
    const session = next.sessions.find((s) => s.sourceEventIds.includes(cand.eventId))!;
    const clientChip = next.suggestions.find((s) => s.sessionId === session.id && s.field === "clientId")!;
    next = decideSuggestion(next, clientChip.id, "accept", DEFAULT_MEMBER_ID, NOW);
    const created = next.clients.find((c) => c.domains.includes("brightlinesw.com"))!;
    expect(created).toBeDefined();
    expect(next.sessions.find((s) => s.id === session.id)!.clientId).toBe(created.id);
    expect(next.people.filter((p) => p.clientId === created.id).length).toBe(2);
  });

  it("admins can add, edit, reorder and archive session formats", () => {
    let state = initialState("messy", NOW);
    state = upsertSessionType(state, null, { label: "Lunch & learn", keywords: ["lunch and learn", "L&L"], typicalMinutes: 60 }, DEFAULT_MEMBER_ID, NOW);
    const added = state.sessionTypes.find((t) => t.label === "Lunch & learn")!;
    expect(added.id).toBe("lunchLearn");
    expect(added.keywords).toEqual(["lunch & learn", "lunch and learn", "l&l"]);
    state = upsertSessionType(state, added.id, { label: "Lunch and learn", archived: true }, DEFAULT_MEMBER_ID, NOW);
    expect(state.sessionTypes.find((t) => t.id === added.id)!.archived).toBe(true);
    const before = [...state.sessionTypes].sort((a, b) => a.sortOrder - b.sortOrder).map((t) => t.id);
    state = moveSessionType(state, "ama", -1);
    const after = [...state.sessionTypes].sort((a, b) => a.sortOrder - b.sortOrder).map((t) => t.id);
    expect(after.indexOf("ama")).toBe(before.indexOf("ama") - 1);
  });

  it("merging a duplicate attaches its event to the original session", () => {
    const { state } = runSync(initialState("messy", NOW), NOW);
    const original = find(state, "evt-vantage-d1").cand;
    const copy = find(state, "evt-vantage-d1-copy").cand;
    let next = confirmCandidate(state, original.id, DEFAULT_MEMBER_ID, NOW);
    next = mergeDuplicate(next, copy.id, NOW);
    const session = next.sessions.find((s) => s.sourceEventIds.includes(original.eventId))!;
    expect(session.sourceEventIds).toContain(copy.eventId);
    expect(next.candidates.find((c) => c.id === copy.id)!.status).toBe("merged");
  });
});
