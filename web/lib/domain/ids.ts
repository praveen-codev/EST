/** FNV-1a 32-bit hash, rendered as 8 hex chars. Deterministic across runs. */
export function hashString(input: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h.toString(16).padStart(8, "0");
}

/** Deterministic id derived from a namespace and a stable seed string. */
export function stableId(namespace: string, seed: string): string {
  return `${namespace}_${hashString(`${namespace}:${seed}`)}${hashString(seed.split("").reverse().join(""))}`;
}

let counter = 0;
/** Non-deterministic id for things created by user action at runtime. */
export function freshId(namespace: string): string {
  counter += 1;
  const rand =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID().slice(0, 12)
      : Math.random().toString(36).slice(2, 14);
  return `${namespace}_${rand}${counter.toString(36)}`;
}
