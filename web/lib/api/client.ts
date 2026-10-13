/**
 * Minimal API client. The access token lives only in memory (never localStorage); on a 401 the
 * client tries one silent refresh via the httpOnly cookie + double-submit CSRF header, then
 * retries the request once. Base URL comes from NEXT_PUBLIC_API_URL.
 */
/** Empty string = same origin (single-host deployments proxy /api/* to the API); default = local dev API. */
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "");

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
const listeners = new Set<(token: string | null) => void>();

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(token: string | null) {
  accessToken = token;
  listeners.forEach((l) => l(token));
}

export function onTokenChange(listener: (token: string | null) => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export class ApiError extends Error {
  constructor(public status: number, public title: string, public detail: string | undefined, public body: unknown) {
    super(detail || title || `HTTP ${status}`);
  }
}

function csrfCookie(): string | null {
  if (typeof document === "undefined") return null;
  const m = document.cookie.match(/(?:^|;\s*)est_csrf=([^;]+)/);
  return m ? decodeURIComponent(m[1]!) : null;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresAt: string;
  member?: unknown;
  /** Double-submit nonce; also set as a cookie on the auth path. Not a secret — it only proves same-origin JS. */
  csrf?: string;
}

const CSRF_KEY = "est.csrf";

export function rememberCsrf(csrf: string | undefined) {
  if (typeof window === "undefined" || !csrf) return;
  window.localStorage.setItem(CSRF_KEY, csrf);
}

export function currentCsrf(): string | null {
  return csrfCookie() ?? (typeof window === "undefined" ? null : window.localStorage.getItem(CSRF_KEY));
}

/** Rotate the refresh token. Resolves false when there is no valid session. */
export async function refreshAccessToken(): Promise<boolean> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    try {
      const csrf = currentCsrf();
      if (!csrf) return false;
      const res = await fetch(`${API_URL}/api/v1/auth/refresh`, { method: "POST", credentials: "include", headers: { "X-CSRF": csrf } });
      if (!res.ok) return false;
      const body = (await res.json()) as TokenResponse;
      rememberCsrf(body.csrf);
      setAccessToken(body.accessToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  /** Skip the bearer header (auth endpoints). */
  anonymous?: boolean;
  retry?: boolean;
}

export async function api<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json", ...opts.headers };
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (!opts.anonymous && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const res = await fetch(`${API_URL}${path}`, {
    method: opts.method ?? "GET",
    headers,
    credentials: "include",
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
  if (res.status === 401 && !opts.anonymous && opts.retry !== false) {
    if (await refreshAccessToken()) return api<T>(path, { ...opts, retry: false });
    setAccessToken(null);
  }
  if (!res.ok) {
    let body: unknown = null;
    try {
      body = await res.json();
    } catch {
      /* no body */
    }
    const b = (body ?? {}) as { title?: string; detail?: string };
    throw new ApiError(res.status, b.title ?? res.statusText, b.detail, body);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const get = <T>(path: string) => api<T>(path);
export const post = <T>(path: string, body?: unknown) => api<T>(path, { method: "POST", body });
export const patch = <T>(path: string, body?: unknown, headers?: Record<string, string>) => api<T>(path, { method: "PATCH", body, headers });
export const del = <T>(path: string) => api<T>(path, { method: "DELETE" });
