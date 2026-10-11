import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { analyzeEvent } from "@/lib/ai";
import { engineContext, initialState, runSync } from "@/lib/domain/ops";
import type { Suggestion } from "@/lib/domain/types";

/**
 * Writes contracts/ai-fixtures/alex-messy.json: every calendar event the TS engine sees for Alex
 * (messy scenario, initial lookback) together with the engine's output. The Java port replays the
 * same events against the same seed and must produce an identical signature per suggestion.
 * Ids are deliberately excluded (the two seeds use different id schemes); labels, confidences,
 * evidence and scalar values are compared.
 */
const NOW = new Date("2026-10-11T12:00:00Z");

function signature(state: ReturnType<typeof initialState>, s: Suggestion) {
  const scalarFields = new Set(["title", "sessionType", "participantsEstimated", "summary", "technologyIds"]);
  let value: unknown = null;
  if (scalarFields.has(s.field)) value = s.value;
  if (s.field === "useCases") {
    const uc = s.value as { title: string; useCaseTypeId: string | null; technologyIds: string[] };
    value = { title: uc.title, useCaseTypeId: uc.useCaseTypeId, technologyIds: [...uc.technologyIds].sort() };
  }
  if (s.field === "participantIds") value = (s.value as string[]).map((id) => state.people.find((p) => p.id === id)?.email ?? id).sort();
  return { field: s.field, label: s.label, confidence: s.confidence, value, evidence: s.evidence };
}

describe("ai golden fixtures", () => {
  it("exports the engine's output for the Java port", () => {
    const base = initialState("messy", NOW);
    const synced = runSync(base, NOW).state;
    const ctx = engineContext(synced, synced.currentMemberId);
    const cases = synced.events.map((event) => {
      const cand = synced.candidates.find((c) => c.eventId === event.id)!;
      const result = analyzeEvent(event, cand.id, ctx);
      return {
        providerEventId: event.providerEventId,
        event: {
          title: event.title,
          description: event.description,
          location: event.location,
          conferencing: event.conferencing,
          start: event.start,
          end: event.end,
          organizerEmail: event.organizerEmail,
          attendees: event.attendees,
          status: event.status,
          recurringEventId: event.recurringEventId,
        },
        expected: {
          classification: result.classification,
          redactions: result.redactions,
          suggestions: result.suggestions
            .map((s) => signature(synced, s))
            .sort((a, b) => `${a.field}|${a.label}`.localeCompare(`${b.field}|${b.label}`)),
        },
      };
    });
    expect(cases.length).toBeGreaterThan(15);
    const dir = resolve(__dirname, "../../../contracts/ai-fixtures");
    mkdirSync(dir, { recursive: true });
    writeFileSync(resolve(dir, "alex-messy.json"), JSON.stringify({ generatedBy: "web/tests/fixtures/export-ai-fixtures.test.ts", now: NOW.toISOString(), cases }, null, 2) + "\n");
  });
});
