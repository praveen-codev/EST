"use client";

import { create } from "zustand";
import type { Member } from "@/lib/domain/types";
import { api, ApiError, currentCsrf, refreshAccessToken, rememberCsrf, setAccessToken, type TokenResponse } from "./client";

export type AuthStatus = "unknown" | "signed-out" | "signed-in" | "pending" | "rejected";

interface AuthState {
  status: AuthStatus;
  member: Member | null;
  error: string | null;
  /** Try the refresh cookie once on app load. */
  bootstrap: () => Promise<void>;
  signInWithGoogle: (idToken: string) => Promise<void>;
  /** Local profile only: sign in as a seeded persona. */
  devLogin: (email: string) => Promise<void>;
  signOut: () => Promise<void>;
}

async function finish(set: (s: Partial<AuthState>) => void, body: TokenResponse) {
  rememberCsrf(body.csrf);
  setAccessToken(body.accessToken);
  set({ status: "signed-in", member: (body.member as Member) ?? null, error: null });
}

export const useAuth = create<AuthState>()((set) => ({
  status: "unknown",
  member: null,
  error: null,
  bootstrap: async () => {
    const ok = await refreshAccessToken();
    if (!ok) {
      set({ status: "signed-out", member: null });
      return;
    }
    try {
      const me = await api<{ member: Member }>("/api/v1/me");
      set({ status: "signed-in", member: me.member, error: null });
    } catch {
      set({ status: "signed-out", member: null });
    }
  },
  signInWithGoogle: async (idToken) => {
    try {
      await finish(set, await api<TokenResponse>("/api/v1/auth/google", { method: "POST", body: { idToken }, anonymous: true }));
    } catch (e) {
      const err = e as ApiError;
      const pending = err.status === 403 && /waiting for an admin/i.test(err.detail ?? "");
      set({ status: pending ? "pending" : "rejected", member: null, error: err.detail ?? err.message });
    }
  },
  devLogin: async (email) => {
    try {
      await finish(set, await api<TokenResponse>(`/api/v1/dev/login-as?email=${encodeURIComponent(email)}`, { method: "POST", anonymous: true }));
    } catch (e) {
      set({ status: "rejected", error: (e as Error).message });
    }
  },
  signOut: async () => {
    const csrf = currentCsrf();
    try {
      await api("/api/v1/auth/logout", { method: "POST", anonymous: true, headers: csrf ? { "X-CSRF": csrf } : {} });
    } catch {
      /* already signed out */
    }
    setAccessToken(null);
    set({ status: "signed-out", member: null, error: null });
  },
}));
