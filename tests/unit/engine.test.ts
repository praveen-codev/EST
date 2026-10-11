import { describe, expect, it } from "vitest";
import { analyzeEvent, tierFor } from "@/lib/ai";
import { redact } from "@/lib/ai/redact";
import { engineContext, initialState, runSync } from "@/lib/domain/ops";
import { clientByName, personByEmail, tagByLabel, teamByName } from "@/lib/mock/seed";

const NOW = new Date("2026-10-11T12:00:00Z");

function syncedState() {
  return runSync(initialState("messy", NOW), NOW).state;
}

function candidateByProviderId(state: ReturnType<typeof syncedState>, providerEventId: string) {
  const event = state.events.find((e) => e.providerEventId === providerEventId)!;
  const cand = state.candidates.find((c) => c.eventId === event.id)!;
  const suggestions = state.suggestions.filter((s) => s.candidateId === cand.id);
  return { event, cand, suggestions };
}

describe("redaction", () => {
  it("strips links, passcodes, PINs and phone numbers", () => {
    const r = redact("Join: https://meet.google.com/abc-defg-hij\nPasscode: 4421\nDial-in: +1 555 010 2233 PIN 889 221");
    expect(r.text).not.toMatch(/meet\.google/);
    expect(r.text).not.toMatch(/4421/);
    expect(r.text).not.toMatch(/555 010/);
    expect(r.text).not.toMatch(/889 221/);
    expect(r.redactions.length).toBeGreaterThanOrEqual(3);
  });
});

describe("classification", () => {
  const state = syncedState();

  it("classifies real enablement events as likely", () => {
    for (const id of ["evt-acme-oh-1", "evt-vantage-d1", "evt-vantage-d2", "evt-northwind-fu", "evt-meridian-training", "evt-acme-pay-review"]) {
      expect(candidateByProviderId(state, id).cand.classification.bucket, id).toBe("likely");
    }
  });

  it("never classifies internal noise as a session (deliberate false positives)", () => {
    for (const id of ["evt-1on1", "evt-standup-1", "evt-interview", "evt-focus", "evt-dentist"]) {
      const { cand, suggestions } = candidateByProviderId(state, id);
      expect(cand.classification.bucket, id).toBe("unlikely");
      expect(suggestions, id).toHaveLength(0);
    }
  });

  it("treats cancelled events as unlikely with an explicit reason", () => {
    const { cand } = candidateByProviderId(state, "evt-orbital-cancelled");
    expect(cand.classification.bucket).toBe("unlikely");
    expect(cand.classification.reasons).toContain("Event was cancelled");
  });

  it("sends ambiguous events to review rather than deciding", () => {
    for (const id of ["evt-brightline-intro", "evt-helios-sync"]) {
      expect(candidateByProviderId(state, id).cand.classification.bucket, id).toBe("review");
    }
  });

  it("keeps a sparse but plausible workshop as likely while leaving gaps for the member to fill", () => {
    const { cand, suggestions } = candidateByProviderId(state, "evt-incomplete-workshop");
    expect(cand.classification.bucket).toBe("likely");
    expect(suggestions.some((s) => s.field === "contacts")).toBe(false);
    expect(suggestions.some((s) => s.field === "participantsEstimated")).toBe(false);
  });
});

describe("extraction", () => {
  const state = syncedState();

  it("prefills client, team, type and facilitator from calendar + history", () => {
    const { suggestions } = candidateByProviderId(state, "evt-acme-oh-1");
    const byField = (f: string) => suggestions.filter((s) => s.field === f);
    expect(byField("clientId")[0]!.value).toBe(clientByName("Acme Financial").id);
    expect(tierFor(byField("clientId")[0]!.confidence)).toBe("prefill");
    expect(byField("teamId")[0]!.value).toBe(teamByName("Acme Financial", "Platform Engineering").id);
    expect(byField("sessionType")[0]!.value).toBe("officeHours");
    expect(byField("facilitatorId")[0]!.label).toBe("Alex Rivera");
  });

  it("extracts contacts from description patterns and from teammates' history", () => {
    const { suggestions } = candidateByProviderId(state, "evt-acme-oh-1");
    const contacts = suggestions.filter((s) => s.field === "contacts");
    const jane = contacts.find((c) => (c.value as { personId: string }).personId === personByEmail("jane.doe@acmefinancial.com")!.id)!;
    expect(jane).toBeDefined();
    expect(jane.evidence.some((e) => e.source === "calendar.description")).toBe(true);
    const history = contacts.flatMap((c) => c.evidence).filter((e) => e.source === "history.people");
    expect(history.length).toBeGreaterThan(0);
    expect(history[0]!.attribution).toMatch(/Priya/);
  });

  it("recommends collaborative tags used by other engineers on the same client", () => {
    const { suggestions } = candidateByProviderId(state, "evt-acme-oh-1");
    const tags = suggestions.filter((s) => s.field === "tagIds");
    expect(tags.map((t) => t.value)).toContain(tagByLabel("Jenkins").id);
    expect(tags.map((t) => t.value)).toContain(tagByLabel("Java 8 → 21 upgrade").id);
    const collaborative = tags.filter((t) => t.evidence.some((e) => e.source === "history.tags"));
    expect(collaborative.length).toBeGreaterThan(0);
    expect(collaborative.every((t) => tierFor(t.confidence) !== "prefill")).toBe(true);
  });

  it("reads Day 1/Day 2, estimated participants and creates unknown attendees as people", () => {
    const { suggestions } = candidateByProviderId(state, "evt-vantage-d1");
    expect(suggestions.find((s) => s.field === "sessionType")!.value).toBe("day1of2");
    expect(suggestions.find((s) => s.field === "participantsEstimated")!.value).toBe(20);
    expect(state.people.some((p) => p.email === "priyanka.rao@vantage-ins.com")).toBe(true);
    const participants = suggestions.find((s) => s.field === "participantIds")!.value as string[];
    expect(participants.length).toBe(6);
  });

  it("proposes a new client when the domain is unknown", () => {
    const { suggestions } = candidateByProviderId(state, "evt-brightline-intro");
    const client = suggestions.find((s) => s.field === "clientId")!;
    expect(client.value).toMatchObject({ create: { domain: "brightlinesw.com" } });
    expect(tierFor(client.confidence)).toBe("chip");
  });

  it("is deterministic: same event and context produce identical suggestions", () => {
    const { event, cand } = candidateByProviderId(state, "evt-vantage-d1");
    const a = analyzeEvent(event, cand.id, engineContext(state, cand.ownerMemberId));
    const b = analyzeEvent(event, cand.id, engineContext(state, cand.ownerMemberId));
    expect(JSON.stringify(a)).toBe(JSON.stringify(b));
  });
});
