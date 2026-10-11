/**
 * Layered redaction applied before any text is "sent to the model".
 * Conferencing links, passcodes, PINs and phone numbers never leave this boundary.
 */

export interface RedactionResult {
  text: string;
  redactions: string[];
}

const RULES: Array<{ label: string; pattern: RegExp; replacement: string }> = [
  { label: "conferencing link", pattern: /https?:\/\/\S+/gi, replacement: "[link]" },
  { label: "passcode", pattern: /\b(passcode|password|pwd)\s*[:#]?\s*[A-Za-z0-9-]{3,}/gi, replacement: "$1: [redacted]" },
  { label: "PIN", pattern: /\bpin\s*[:#]?\s*[\d ]{3,}/gi, replacement: "PIN: [redacted]" },
  { label: "phone number", pattern: /\+?\d[\d\s().-]{7,}\d/g, replacement: "[phone]" },
];

export function redact(input: string): RedactionResult {
  let text = input;
  const redactions: string[] = [];
  for (const rule of RULES) {
    const matches = text.match(rule.pattern);
    if (matches?.length) {
      redactions.push(`${matches.length} ${rule.label}${matches.length > 1 ? "s" : ""}`);
      text = text.replace(rule.pattern, rule.replacement);
    }
  }
  return { text, redactions };
}
