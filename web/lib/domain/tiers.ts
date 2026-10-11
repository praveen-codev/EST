import { stableId } from "./ids";

/** Confidence tiers shared by the UI (mirrors SuggestionEngine on the backend). */
export const PREFILL_THRESHOLD = 0.85;
export const CHIP_THRESHOLD = 0.5;

export type SuggestionTier = "prefill" | "chip" | "folded";

export function tierFor(confidence: number): SuggestionTier {
  if (confidence >= PREFILL_THRESHOLD) return "prefill";
  if (confidence >= CHIP_THRESHOLD) return "chip";
  return "folded";
}

/** A confirmed candidate's session id is derived from its event (same FNV hash as the backend), so links can be built before confirming. */
export function sessionIdForEvent(eventId: string): string {
  return stableId("ses", eventId);
}
