"use client";

import { create } from "zustand";
import { del, get, patch, post } from "@/lib/api/client";
import { EMPTY_STATE, fromSnapshot, type DataState, type Snapshot } from "./state";
import type { CatalogItem, CatalogKind, ID, ScenarioId, Session, Settings, SyncRun, UseCase } from "./types";

/**
 * Client-side model backed by the API. Reads come from one /snapshot; every write calls the
 * corresponding endpoint and then reloads the snapshot, so the pure selectors (today board, inbox
 * grouping, timeline, session view-model) keep working unchanged on authoritative data.
 */

export type SessionPatch = Partial<Pick<Session, "title" | "state" | "clientId" | "teamId" | "facilitatorId" | "coFacilitatorIds" | "contacts" | "participantIds" | "participantsEstimated" | "sessionType" | "outcomeId" | "technologyIds" | "summary" | "notes">>;
export type CatalogPatch = Partial<Pick<CatalogItem, "label" | "description" | "keywords" | "typicalMinutes" | "archived">>;

export interface SyncOutcome {
  runs: SyncRun[];
  newCandidateIds: ID[];
  updatedCandidateIds: ID[];
}

export interface UiState {
  hydrated: boolean;
  loading: boolean;
  syncing: boolean;
  lastSync: SyncOutcome | null;
  error: string | null;
}

export interface Actions {
  load: () => Promise<void>;
  reset: () => void;
  syncNow: () => Promise<SyncOutcome | null>;
  confirmCandidate: (id: ID) => Promise<void>;
  confirmMany: (ids: ID[]) => Promise<void>;
  dismissCandidate: (id: ID) => Promise<void>;
  restoreCandidate: (id: ID) => Promise<void>;
  mergeDuplicate: (id: ID) => Promise<void>;
  acceptSuggestion: (id: ID) => Promise<void>;
  rejectSuggestion: (id: ID) => Promise<void>;
  acceptAllForSession: (sessionId: ID) => Promise<void>;
  updateSession: (sessionId: ID, patch: SessionPatch) => Promise<void>;
  rerunAI: (sessionId: ID) => Promise<void>;
  upsertCatalogItem: (kind: CatalogKind, id: ID | null, patch: CatalogPatch) => Promise<void>;
  moveCatalogItem: (kind: CatalogKind, id: ID, delta: -1 | 1) => Promise<void>;
  addUseCase: (sessionId: ID, draft?: Partial<UseCase>) => Promise<void>;
  updateUseCase: (sessionId: ID, useCaseId: ID, patch: Partial<Omit<UseCase, "id">>) => Promise<void>;
  removeUseCase: (sessionId: ID, useCaseId: ID) => Promise<void>;
  /** Ad-hoc "Other…" entry: saved to the reference table (same-label rows reused) and returns the id to select. */
  createCatalogItem: (kind: CatalogKind, label: string) => Promise<ID>;
  createPerson: (name: string, clientId: ID | null) => Promise<ID>;
  createClient: (name: string) => Promise<ID>;
  createTeam: (name: string, clientId: ID) => Promise<ID>;
  setScenario: (scenario: ScenarioId) => Promise<void>;
  resetDemo: () => Promise<void>;
  toggleReasoning: () => void;
}

export type Store = DataState & UiState & Actions;

const SETTINGS_KEY = "est.settings";

function loadSettings(): Settings {
  if (typeof window === "undefined") return EMPTY_STATE.settings;
  try {
    return { ...EMPTY_STATE.settings, ...(JSON.parse(window.localStorage.getItem(SETTINGS_KEY) ?? "{}") as Partial<Settings>) };
  } catch {
    return EMPTY_STATE.settings;
  }
}

/** The session's contacts travel as {personId, roleId} on the wire. */
const wirePatch = (p: SessionPatch) => (p.contacts ? { ...p, contacts: p.contacts.map((c) => ({ personId: c.personId, roleId: c.role })) } : p);

export const useStore = create<Store>()((set, getState) => {
  const reload = async () => {
    const snap = await get<Snapshot>("/api/v1/snapshot");
    set({ ...fromSnapshot(snap, getState().settings), hydrated: true, loading: false, error: null });
  };
  /** Run a write, then refresh the model. Errors surface in `error` and are rethrown for callers that care. */
  const mutate = async <T>(fn: () => Promise<T>): Promise<T> => {
    try {
      const out = await fn();
      await reload();
      return out;
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  };

  return {
    ...EMPTY_STATE,
    settings: loadSettings(),
    hydrated: false,
    loading: false,
    syncing: false,
    lastSync: null,
    error: null,

    load: async () => {
      set({ loading: true });
      try {
        await reload();
      } catch (e) {
        set({ loading: false, error: (e as Error).message });
      }
    },
    reset: () => set({ ...EMPTY_STATE, settings: getState().settings, hydrated: false, loading: false, syncing: false, lastSync: null, error: null }),

    syncNow: async () => {
      if (getState().syncing) return null;
      set({ syncing: true });
      try {
        const before = new Set(getState().candidates.map((c) => c.id));
        const beforeUpdated = new Map(getState().candidates.map((c) => [c.id, c.updatedAt] as const));
        const runs = await post<SyncRun[]>("/api/v1/calendar/sync");
        await reload();
        const after = getState().candidates;
        const outcome: SyncOutcome = {
          runs,
          newCandidateIds: after.filter((c) => !before.has(c.id)).map((c) => c.id),
          updatedCandidateIds: after.filter((c) => before.has(c.id) && beforeUpdated.get(c.id) !== c.updatedAt).map((c) => c.id),
        };
        set({ syncing: false, lastSync: outcome });
        return outcome;
      } catch (e) {
        set({ syncing: false, error: (e as Error).message });
        return null;
      }
    },

    confirmCandidate: (id) => mutate(() => post(`/api/v1/candidates/${id}/confirm`)).then(() => undefined),
    confirmMany: (ids) => mutate(async () => { for (const id of ids) await post(`/api/v1/candidates/${id}/confirm`); }),
    dismissCandidate: (id) => mutate(() => post(`/api/v1/candidates/${id}/dismiss`)),
    restoreCandidate: (id) => mutate(() => post(`/api/v1/candidates/${id}/restore`)),
    mergeDuplicate: (id) => mutate(() => post(`/api/v1/candidates/${id}/merge`)).then(() => undefined),
    acceptSuggestion: (id) => mutate(() => post(`/api/v1/suggestions/${id}/accept`)).then(() => undefined),
    rejectSuggestion: (id) => mutate(() => post(`/api/v1/suggestions/${id}/reject`)).then(() => undefined),
    acceptAllForSession: (sessionId) => mutate(() => post(`/api/v1/sessions/${sessionId}/suggestions/accept-all`)).then(() => undefined),
    updateSession: (sessionId, p) => mutate(() => patch(`/api/v1/sessions/${sessionId}`, wirePatch(p))).then(() => undefined),
    rerunAI: (sessionId) => mutate(() => post(`/api/v1/sessions/${sessionId}/rerun-ai`)).then(() => undefined),

    upsertCatalogItem: (kind, id, p) => mutate(() => (id ? patch(`/api/v1/catalog/${kind}/${id}`, p) : post(`/api/v1/catalog/${kind}`, p))).then(() => undefined),
    moveCatalogItem: (kind, id, delta) =>
      mutate(async () => {
        const rows = getState().catalog.filter((c) => c.kind === kind).sort((a, b) => a.sortOrder - b.sortOrder).map((c) => c.id);
        const i = rows.indexOf(id);
        const j = i + delta;
        if (i < 0 || j < 0 || j >= rows.length) return;
        [rows[i], rows[j]] = [rows[j]!, rows[i]!];
        await post(`/api/v1/catalog/${kind}/reorder`, { ids: rows });
      }),
    addUseCase: (sessionId, draft) => mutate(() => post(`/api/v1/sessions/${sessionId}/use-cases`, draft ?? {})).then(() => undefined),
    updateUseCase: (sessionId, useCaseId, p) => mutate(() => patch(`/api/v1/sessions/${sessionId}/use-cases/${useCaseId}`, p)).then(() => undefined),
    removeUseCase: (sessionId, useCaseId) => mutate(() => del(`/api/v1/sessions/${sessionId}/use-cases/${useCaseId}`)).then(() => undefined),

    createCatalogItem: (kind, label) => mutate(async () => (await post<CatalogItem>(`/api/v1/catalog/${kind}?adhoc=true`, { label })).id),
    createPerson: (name, clientId) => mutate(async () => (await post<{ id: ID }>("/api/v1/people", { name, clientId })).id),
    createClient: (name) => mutate(async () => (await post<{ id: ID }>("/api/v1/clients", { name })).id),
    createTeam: (name, clientId) => mutate(async () => (await post<{ id: ID }>(`/api/v1/clients/${clientId}/teams`, { name })).id),

    setScenario: (scenario) => mutate(() => post(`/api/v1/dev/scenario/${scenario}`)).then(() => set({ lastSync: null })),
    resetDemo: () => mutate(() => post("/api/v1/dev/reset")).then(() => set({ lastSync: null })),
    toggleReasoning: () =>
      set((s) => {
        const settings = { ...s.settings, showReasoning: !s.settings.showReasoning };
        window.localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
        return { settings };
      }),
  };
});

export const useHydrated = () => useStore((s) => s.hydrated);
export const useCurrentMember = () => useStore((s) => s.members.find((m) => m.id === s.currentMemberId) ?? null);
export const useOpenCandidateCount = () =>
  useStore((s) => s.candidates.filter((c) => c.ownerMemberId === s.currentMemberId && c.status === "open" && c.classification.bucket !== "unlikely").length);
