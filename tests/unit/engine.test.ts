import { describe, expect, it } from "vitest";
import { analyzeEvent, tierFor } from "@/lib/ai";
import { redact } from "@/lib/ai/redact";
import { engineContext, initialState, runSync } from "@/lib/domain/ops";
import { clientByName, personByEmail, techByLabel, teamByName } from "@/lib/mock/seed";

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
    for (const id of ["evt-mb-101-today", "evt-mb-pair-today", "evt-acme-oh-1", "evt-vantage-d1", "evt-vantage-d2", "evt-northwind-fu", "evt-meridian-training", "evt-acme-pay-review"]) {
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

  it("only counts the team's own formats: generic workshops with a client are capped at 'needs a look'", () => {
    for (const id of ["evt-helios-generic-ws", "evt-incomplete-workshop"]) {
      const { cand, suggestions } = candidateByProviderId(state, id);
      expect(cand.classification.bucket, id).toBe("review");
      expect(cand.classification.reasons[0]).toMatch(/No recognised session format/);
      expect(suggestions.some((s) => s.field === "sessionType"), id).toBe(false);
    }
  });

  it("matches session formats from the admin-managed table, longest keyword first", () => {
    expect(candidateByProviderId(state, "evt-vantage-d2").suggestions.find((s) => s.field === "sessionType")!.value).toBe("201");
    expect(candidateByProviderId(state, "evt-acme-pay-review").suggestions.find((s) => s.field === "sessionType")!.value).toBe("ama");
    expect(candidateByProviderId(state, "evt-northwind-fu").suggestions.find((s) => s.field === "sessionType")!.value).toBe("pairProgramming");
  });

  it("an archived format stops matching", () => {
    const archived = { ...state, catalog: state.catalog.map((t) => (t.id === "ama" ? { ...t, archived: true } : t)) };
    const { event, cand } = candidateByProviderId(state, "evt-acme-pay-review");
    const result = analyzeEvent(event, cand.id, engineContext(archived, cand.ownerMemberId));
    expect(result.classification.bucket).toBe("review");
    expect(result.suggestions.some((s) => s.field === "sessionType")).toBe(false);
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
    expect(byField("sessionType")[0]!.value).toBe("101");
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

  it("recommends technologies from the catalogue and from what teammates recorded for the client", () => {
    const { suggestions } = candidateByProviderId(state, "evt-acme-oh-1");
    const tech = suggestions.filter((s) => s.field === "technologyIds");
    expect(tech.map((t) => t.value)).toContain(techByLabel("Jenkins").id);
    expect(tech.map((t) => t.value)).toContain(techByLabel("Java").id);
    const collaborative = tech.filter((t) => t.evidence.some((e) => e.source === "history.tags"));
    expect(collaborative.map((t) => t.value)).toContain(techByLabel("Spring Boot").id);
    expect(collaborative.every((t) => tierFor(t.confidence) !== "prefill")).toBe(true);
  });

  it("drafts a use case from the invite's stated goal with a type and technologies", () => {
    const { suggestions } = candidateByProviderId(state, "evt-mb-101-today");
    const draft = suggestions.find((s) => s.field === "useCases")!;
    expect(draft).toBeDefined();
    const uc = draft.value as { title: string; useCaseTypeId: string | null; technologyIds: string[] };
    expect(uc.title).toMatch(/Ab Initio/i);
    expect(uc.useCaseTypeId).toBe("etlMigration");
    expect(uc.technologyIds).toContain(techByLabel("Ab Initio").id);
    expect(uc.technologyIds).toContain(techByLabel("Databricks").id);
    expect(tierFor(draft.confidence)).toBe("chip");
    const pair = candidateByProviderId(state, "evt-mb-pair-today").suggestions.find((s) => s.field === "useCases")!;
    expect((pair.value as { useCaseTypeId: string }).useCaseTypeId).toBe("excelToDashboard");
  });

  it("reads Day 1/Day 2, estimated participants and creates unknown attendees as people", () => {
    const { suggestions } = candidateByProviderId(state, "evt-vantage-d1");
    expect(suggestions.find((s) => s.field === "sessionType")!.value).toBe("sprintInADay");
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
