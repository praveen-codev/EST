import type { Classification, ClassificationBucket } from "@/lib/domain/types";
import type { NormalizedEvent } from "./normalize";

export const LIKELY_THRESHOLD = 0.7;
export const REVIEW_THRESHOLD = 0.4;

const STRONG = ["workshop", "enablement", "kickoff", "kick-off", "office hours", "hands-on", "hands on", "training", "onboarding"];
const PRODUCT = ["devin"];
const WEAK = ["follow-up", "follow up", "sync", "readout", "pilot", "demo"];
const SOFT_NEGATIVE = ["intro call", "intro:", "intro to", "introduction"];
const NEGATIVE = ["1:1", "1-1", "standup", "stand-up", "interview", "lunch", "focus time", "dentist", "ooo", "out of office", "retro", "all-hands", "all hands", "pto"];

export function bucketFor(score: number): ClassificationBucket {
  if (score >= LIKELY_THRESHOLD) return "likely";
  if (score >= REVIEW_THRESHOLD) return "review";
  return "unlikely";
}

export function classify(n: NormalizedEvent, knownClient: boolean): Classification {
  if (n.cancelled) {
    return { score: 0.05, bucket: "unlikely", reasons: ["Event was cancelled"] };
  }
  const reasons: string[] = [];
  let score = 0.2;

  const negatives = NEGATIVE.filter((k) => n.text.includes(k));
  if (negatives.length) {
    score -= 0.5;
    reasons.push(`Looks like "${negatives[0]}", not an enablement session`);
  }

  const strong = STRONG.filter((k) => n.text.includes(k));
  if (strong.length) {
    score += Math.min(0.4, 0.2 * strong.length);
    reasons.push(`Mentions ${strong.slice(0, 2).map((k) => `"${k}"`).join(" and ")}`);
  }
  if (PRODUCT.some((k) => n.text.includes(k))) {
    score += 0.1;
    reasons.push("Mentions Devin");
  }
  const weak = WEAK.filter((k) => n.text.includes(k));
  if (weak.length && !strong.length) {
    score += 0.1;
    reasons.push(`Mentions "${weak[0]}" (weak signal)`);
  }
  const soft = SOFT_NEGATIVE.find((k) => n.text.includes(k));
  if (soft) {
    score -= 0.2;
    reasons.push("Reads like an intro call rather than a session");
  }

  if (n.external.length > 0) {
    score += 0.2;
    reasons.push(`${n.external.length} external attendee${n.external.length > 1 ? "s" : ""} from ${n.externalDomains.join(", ")}`);
  } else {
    score -= 0.35;
    reasons.push(n.attendees.length <= 1 ? "Only you on the invite" : "Internal attendees only");
  }

  if (n.internal.length > 0 && n.external.length > 0) score += 0.05;

  if (n.durationMin >= 45 && n.durationMin <= 240) {
    score += 0.1;
    if (n.durationMin >= 90) score += 0.05;
    reasons.push(`${n.durationMin} min fits a session`);
  } else if (n.durationMin < 45) {
    score -= n.durationMin < 30 ? 0.25 : 0.1;
    reasons.push(`Only ${n.durationMin} min`);
  }

  if (n.attendees.length >= 3) score += 0.1;
  if (knownClient) {
    score += 0.1;
    reasons.push("Attendees match a client with prior sessions");
  }
  if (!n.description && n.title.split(/\s+/).length <= 2) {
    score -= 0.1;
    reasons.push("Sparse title and no description");
  }

  const clamped = Math.max(0, Math.min(1, Number(score.toFixed(2))));
  return { score: clamped, bucket: bucketFor(clamped), reasons };
}
