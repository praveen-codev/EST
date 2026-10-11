import { stableId } from "@/lib/domain/ids";
import type { CalendarEvent, Classification, Person, Suggestion } from "@/lib/domain/types";
import { classify } from "./classify";
import { extract, matchSessionType, PROMPT_VERSION, type EngineContext } from "./extract";
import { normalizeEvent } from "./normalize";
import { redact } from "./redact";

export { PROMPT_VERSION, ITEM_FIELDS, type EngineContext, type ClientCreate } from "./extract";
export { LIKELY_THRESHOLD, REVIEW_THRESHOLD, NO_TYPE_CAP, bucketFor } from "./classify";

/** Confidence at or above which a suggestion is written straight into the draft (still revertible). */
export const PREFILL_THRESHOLD = 0.85;
/** Confidence at or above which a suggestion is shown inline as a chip; below it is folded away. */
export const CHIP_THRESHOLD = 0.5;

export type SuggestionTier = "prefill" | "chip" | "folded";

export function tierFor(confidence: number): SuggestionTier {
  if (confidence >= PREFILL_THRESHOLD) return "prefill";
  if (confidence >= CHIP_THRESHOLD) return "chip";
  return "folded";
}

export interface EngineResult {
  classification: Classification;
  suggestions: Suggestion[];
  redactedText: string;
  redactions: string[];
  newPeople: Person[];
}

/**
 * The whole deterministic pipeline for one calendar event:
 * normalise → redact → classify → extract/match → validated suggestions.
 * Same event + same context ⇒ identical output (ids included).
 */
export function analyzeEvent(event: CalendarEvent, candidateId: string, ctx: EngineContext): EngineResult {
  const redacted = redact(event.description);
  const safeEvent: CalendarEvent = { ...event, description: redacted.text };
  const n = normalizeEvent(safeEvent);

  const extraction = n.cancelled ? { suggestions: [], newPeople: [], matchedClient: null } : extract(n, ctx);
  const typeMatch = n.cancelled ? null : matchSessionType(n, ctx.sessionTypes);
  const classification = classify(n, extraction.matchedClient !== null, typeMatch ? { label: typeMatch.type.label, keyword: typeMatch.keyword, typicalMinutes: typeMatch.type.typicalMinutes } : null);

  const suggestions: Suggestion[] = classification.bucket === "unlikely"
    ? []
    : extraction.suggestions.map((d) => ({
        id: stableId("sug", `${event.id}|${d.field}|${JSON.stringify(d.value)}`),
        candidateId,
        sessionId: null,
        field: d.field,
        value: d.value,
        label: d.label,
        confidence: d.confidence,
        evidence: d.evidence,
        status: "proposed",
        decidedBy: null,
        decidedAt: null,
        promptVersion: PROMPT_VERSION,
      }));

  return {
    classification,
    suggestions,
    redactedText: `${event.title}\n\n${redacted.text}`.trim(),
    redactions: redacted.redactions,
    newPeople: classification.bucket === "unlikely" ? [] : extraction.newPeople,
  };
}
