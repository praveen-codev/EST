"use client";

import { del, get, set } from "idb-keyval";
import { create } from "zustand";
import { createJSONStorage, persist, type StateStorage } from "zustand/middleware";
import { freshId } from "./ids";
import type { ID, ScenarioId } from "./types";
import {
  acceptAllForSession,
  confirmCandidate,
  decideSuggestion,
  dismissCandidate,
  initialState,
  mergeDuplicate,
  moveSessionType,
  openCandidatesFor,
  rerunAI,
  restoreCandidate,
  runSync,
  SCHEMA_VERSION,
  updateSession,
  upsertSessionType,
  type DataState,
  type SessionPatch,
  type SessionTypePatch,
  type SyncOutcome,
} from "./ops";
import { CHIP_THRESHOLD } from "@/lib/ai";

export const STORAGE_KEY = "enablement-session-tracker";

const idbStorage: StateStorage = {
  getItem: async (name) => (await get<string>(name)) ?? null,
  setItem: async (name, value) => set(name, value),
  removeItem: async (name) => del(name),
};

export interface UiState {
  hydrated: boolean;
  syncing: boolean;
  lastSync: SyncOutcome | null;
}

export interface Actions {
  switchMember: (id: ID) => void;
  syncNow: () => Promise<SyncOutcome | null>;
  confirmCandidate: (id: ID) => void;
  confirmMany: (ids: ID[]) => void;
  dismissCandidate: (id: ID) => void;
  restoreCandidate: (id: ID) => void;
  mergeDuplicate: (id: ID) => void;
  acceptSuggestion: (id: ID) => void;
  rejectSuggestion: (id: ID) => void;
  acceptAllForSession: (sessionId: ID) => void;
  updateSession: (sessionId: ID, patch: SessionPatch) => void;
  rerunAI: (sessionId: ID) => void;
  upsertSessionType: (id: ID | null, patch: SessionTypePatch) => void;
  moveSessionType: (id: ID, delta: -1 | 1) => void;
  createPerson: (name: string, clientId: ID | null) => ID;
  createTag: (label: string) => ID;
  createClient: (name: string) => ID;
  setScenario: (scenario: ScenarioId) => void;
  resetDemo: () => void;
  toggleReasoning: () => void;
  setHydrated: (v: boolean) => void;
}

export type Store = DataState & UiState & Actions;

const dataKeys: Array<keyof DataState> = [
  "schemaVersion", "anchorIso", "currentMemberId", "settings", "members", "clients", "teams", "people", "tags", "sessionTypes",
  "events", "candidates", "suggestions", "sessions", "syncRuns", "cursors",
];

function pickData(s: Store): DataState {
  const out = {} as Record<string, unknown>;
  for (const k of dataKeys) out[k] = s[k];
  return out as unknown as DataState;
}

export const useStore = create<Store>()(
  persist(
    (setState, getState) => ({
      ...initialState(),
      hydrated: false,
      syncing: false,
      lastSync: null,

      setHydrated: (v) => setState({ hydrated: v }),
      switchMember: (id) => setState({ currentMemberId: id, lastSync: null }),

      syncNow: async () => {
        if (getState().syncing) return null;
        setState({ syncing: true });
        // Simulated network latency so the UI can show the sync in flight.
        await new Promise((r) => setTimeout(r, 900));
        const outcome = runSync(pickData(getState()));
        setState({ ...outcome.state, syncing: false, lastSync: outcome });
        return outcome;
      },

      confirmCandidate: (id) => setState((s) => confirmCandidate(pickData(s), id, s.currentMemberId)),
      confirmMany: (ids) =>
        setState((s) => {
          let data = pickData(s);
          for (const id of ids) data = confirmCandidate(data, id, s.currentMemberId);
          return data;
        }),
      dismissCandidate: (id) => setState((s) => dismissCandidate(pickData(s), id)),
      restoreCandidate: (id) => setState((s) => restoreCandidate(pickData(s), id)),
      mergeDuplicate: (id) => setState((s) => mergeDuplicate(pickData(s), id)),
      acceptSuggestion: (id) => setState((s) => decideSuggestion(pickData(s), id, "accept", s.currentMemberId)),
      rejectSuggestion: (id) => setState((s) => decideSuggestion(pickData(s), id, "reject", s.currentMemberId)),
      acceptAllForSession: (sessionId) => setState((s) => acceptAllForSession(pickData(s), sessionId, s.currentMemberId, CHIP_THRESHOLD)),
      updateSession: (sessionId, patch) => setState((s) => updateSession(pickData(s), sessionId, patch, s.currentMemberId)),
      rerunAI: (sessionId) => setState((s) => rerunAI(pickData(s), sessionId)),

      upsertSessionType: (id, patch) => setState((s) => upsertSessionType(pickData(s), id, patch, s.currentMemberId)),
      moveSessionType: (id, delta) => setState((s) => moveSessionType(pickData(s), id, delta)),
      createPerson: (name, clientId) => {
        const id = freshId("per");
        setState((s) => ({ people: [...s.people, { id, name, clientId, email: null, role: null }] }));
        return id;
      },
      createTag: (label) => {
        const id = freshId("tag");
        setState((s) => ({ tags: [...s.tags, { id, label, category: "useCase", aliases: [label.toLowerCase()] }] }));
        return id;
      },
      createClient: (name) => {
        const id = freshId("cli");
        setState((s) => ({ clients: [...s.clients, { id, name, domains: [], industry: null, hue: (name.length * 53) % 360 }] }));
        return id;
      },

      setScenario: (scenario) => setState((s) => ({ ...initialState(scenario, new Date(), s.currentMemberId), lastSync: null })),
      resetDemo: () => setState((s) => ({ ...initialState(s.settings.scenario, new Date(), s.currentMemberId), lastSync: null })),
      toggleReasoning: () => setState((s) => ({ settings: { ...s.settings, showReasoning: !s.settings.showReasoning } })),
    }),
    {
      name: STORAGE_KEY,
      version: SCHEMA_VERSION,
      storage: createJSONStorage(() => idbStorage),
      partialize: (s) => pickData(s),
      migrate: (persisted, version) => (version === SCHEMA_VERSION ? (persisted as DataState) : initialState()),
      onRehydrateStorage: () => (state) => state?.setHydrated(true),
    },
  ),
);

/* ───────────────────────── Convenience hooks ───────────────────────── */

/** False on the server and on the first client render; true once IndexedDB state is loaded. */
export const useHydrated = () => useStore((s) => s.hydrated);
export const useCurrentMember = () => useStore((s) => s.members.find((m) => m.id === s.currentMemberId)!);
export const useOpenCandidateCount = () =>
  useStore((s) => openCandidatesFor(s, s.currentMemberId).filter((c) => c.classification.bucket !== "unlikely").length);
