"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AlertTriangle, CalendarPlus, CheckCircle2, Link2Off, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { del, get, post } from "@/lib/api/client";
import { useHydrated, useStore } from "@/lib/domain/store";
import type { Identity } from "@/lib/domain/state";
import { fmtAgo } from "@/lib/format";

/**
 * Settings → Linked calendars: every Google account ("partner account") whose calendar feeds this
 * member's inbox. Linking runs the authorization-code + PKCE flow server-side; the browser only
 * ever sees the redirect. Tokens are never shown — the API never returns them.
 */
export function LinkedCalendars() {
  const hydrated = useHydrated();
  const params = useSearchParams();
  const identities = useStore((s) => s.identities);
  const load = useStore((s) => s.load);
  const syncNow = useStore((s) => s.syncNow);
  const syncing = useStore((s) => s.syncing);
  const mock = useStore((s) => s.features.mockGoogle);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    const linked = params.get("linked");
    const error = params.get("error");
    if (linked) {
      toast.success("Calendar linked", { id: `linked-${linked}`, description: "The first import has run; new events are in your inbox." });
      void load();
    }
    if (error) toast.error("Linking failed", { id: "link-error", description: error });
  }, [params, load]);

  const startLink = async () => {
    setBusy("link");
    try {
      const { authorizationUrl } = await post<{ authorizationUrl: string }>("/api/v1/calendar/identities/link/start");
      window.location.assign(authorizationUrl);
    } catch (e) {
      toast.error("Could not start linking", { description: (e as Error).message });
      setBusy(null);
    }
  };

  const unlink = async (idn: Identity) => {
    if (!window.confirm(`Unlink ${idn.email}? Imported events stay; nothing new will be read from this calendar.`)) return;
    setBusy(idn.id);
    try {
      await del(`/api/v1/calendar/identities/${idn.id}`);
      await load();
      toast.success(`Unlinked ${idn.email}`);
    } catch (e) {
      toast.error("Could not unlink", { description: (e as Error).message });
    } finally {
      setBusy(null);
    }
  };

  if (!hydrated) return null;
  const active = identities.filter((i) => i.status !== "DISABLED");
  const disabled = identities.filter((i) => i.status === "DISABLED");

  return (
    <div className="mx-auto max-w-3xl px-6 py-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold">Linked calendars</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Each Google account you link is read for enablement sessions. Candidates land in your inbox; nothing is confirmed automatically, and nothing is ever written back.
          </p>
        </div>
        <Button onClick={startLink} disabled={busy === "link"} className="gap-2 bg-ai text-ai-foreground hover:bg-ai/90">
          <CalendarPlus className="size-4" /> Add Google account
        </Button>
      </div>

      <ul className="mt-6 space-y-3">
        {active.map((idn) => (
          <li key={idn.id} className="flex items-center justify-between gap-4 rounded-2xl border bg-card p-4">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="truncate font-medium">{idn.displayName ?? idn.email}</span>
                <Badge variant="outline" className="text-[10px]">{idn.provider === "mock" ? "mock Google" : "Google"}</Badge>
                {idn.status === "ACTIVE" ? (
                  <Badge variant="outline" className="gap-1 border-success/40 text-[10px] text-success"><CheckCircle2 className="size-3" /> active</Badge>
                ) : (
                  <Badge variant="outline" className="gap-1 border-warning/40 text-[10px] text-warning"><AlertTriangle className="size-3" /> needs re-link</Badge>
                )}
              </div>
              <div className="mt-0.5 truncate text-xs text-muted-foreground">
                {idn.email} · {idn.lastSyncAt ? `last sync ${fmtAgo(idn.lastSyncAt)}` : "never synced"}
                {idn.lastError && <span className="text-warning"> · {idn.lastError}</span>}
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <Button variant="outline" size="sm" onClick={() => void syncNow()} disabled={syncing} className="gap-1.5"><RefreshCw className={syncing ? "size-3.5 animate-spin" : "size-3.5"} /> Sync</Button>
              <Button variant="ghost" size="sm" onClick={() => void unlink(idn)} disabled={busy === idn.id} className="gap-1.5 text-muted-foreground hover:text-destructive" aria-label={`Unlink ${idn.email}`}>
                <Link2Off className="size-3.5" /> Unlink
              </Button>
            </div>
          </li>
        ))}
        {active.length === 0 && (
          <li className="rounded-2xl border border-dashed p-8 text-center text-sm text-muted-foreground">No calendar linked yet. Add your Google account to start capturing sessions.</li>
        )}
      </ul>

      {disabled.length > 0 && (
        <div className="mt-8">
          <h2 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Unlinked</h2>
          <ul className="mt-2 space-y-1 text-sm text-muted-foreground">
            {disabled.map((idn) => <li key={idn.id}>{idn.email} — events already imported were kept</li>)}
          </ul>
        </div>
      )}

      <p className="mt-8 text-[11px] text-muted-foreground">
        {mock ? "Local profile: “Google” is simulated; linking creates a partner account instantly." : "Scopes requested: calendar.readonly, calendar.events.readonly, openid email profile. Refresh tokens are encrypted at rest and revoked at Google when you unlink."}
      </p>
    </div>
  );
}

export async function fetchIdentities() {
  return get<Identity[]>("/api/v1/calendar/identities");
}
