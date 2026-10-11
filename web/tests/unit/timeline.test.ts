import { describe, expect, it } from "vitest";
import { allItems, applyFilters, DEFAULT_FILTERS, groupItems } from "@/lib/domain/timeline";
import { initialState, runSync } from "@/lib/domain/ops";
import { clientByName, DEFAULT_MEMBER_ID } from "@/lib/mock/seed";

const NOW = new Date("2026-10-11T12:00:00Z");

describe("timeline selectors", () => {
  const state = runSync(initialState("messy", NOW), NOW).state;

  it("mixes confirmed sessions with open candidates and never includes noise", () => {
    const items = allItems(state, DEFAULT_MEMBER_ID, true);
    expect(items.some((i) => i.kind === "session")).toBe(true);
    expect(items.some((i) => i.kind === "candidate")).toBe(true);
    expect(items.some((i) => i.title.includes("standup"))).toBe(false);
    expect(items.some((i) => i.title.includes("1:1"))).toBe(false);
  });

  it("filters by client and groups by client with the busiest first", () => {
    const items = applyFilters(allItems(state, DEFAULT_MEMBER_ID, true), { ...DEFAULT_FILTERS, clientId: clientByName("Acme Financial").id }, DEFAULT_MEMBER_ID, NOW);
    expect(items.every((i) => i.client?.name === "Acme Financial")).toBe(true);
    const groups = groupItems(allItems(state, DEFAULT_MEMBER_ID, true), "client", NOW);
    expect(groups[0]!.items.length).toBeGreaterThanOrEqual(groups[groups.length - 1]!.items.length);
  });

  it("groups by day with newest day first and items in time order", () => {
    const groups = groupItems(allItems(state, DEFAULT_MEMBER_ID, true), "day", NOW);
    for (let i = 1; i < groups.length; i++) {
      expect(Date.parse(groups[i - 1]!.items[0]!.start)).toBeGreaterThanOrEqual(Date.parse(groups[i]!.items[0]!.start));
    }
  });
});
