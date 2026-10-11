"use client";

import { CalendarCheck2, RefreshCw } from "lucide-react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { fmtAgo } from "@/lib/format";
import { useStore } from "@/lib/domain/store";
import { cn } from "@/lib/utils";

export function SyncPill() {
  const router = useRouter();
  const syncing = useStore((s) => s.syncing);
  const syncNow = useStore((s) => s.syncNow);
  const lastRun = useStore((s) => s.syncRuns[0] ?? null);
  const mock = useStore((s) => s.features.mockGoogle);
  const remaining = useStore((s) => s.features.remainingPages);
  const hasIdentity = useStore((s) => s.identities.some((i) => i.status === "ACTIVE"));
  const upToDate = mock ? remaining === 0 : false;

  const run = async () => {
    const outcome = await syncNow();
    if (!outcome) return;
    const { runs, newCandidateIds, updatedCandidateIds } = outcome;
    if (!runs.length) return toast.info("No calendar linked yet", { description: "Add a Google account under Settings → Linked calendars.", action: { label: "Settings", onClick: () => router.push("/settings/calendars") } });
    const r = { eventsSeen: runs.reduce((n, x) => n + x.eventsSeen, 0), eventsUpdated: runs.reduce((n, x) => n + x.eventsUpdated, 0), eventsUnchanged: runs.reduce((n, x) => n + x.eventsUnchanged, 0) };
    if (r.eventsSeen === 0) return toast.info("Calendar is up to date", { description: "No new or changed events." });
    toast.success(`Synced ${r.eventsSeen} event${r.eventsSeen === 1 ? "" : "s"}`, {
      description: [
        newCandidateIds.length && `${newCandidateIds.length} new candidate${newCandidateIds.length === 1 ? "" : "s"}`,
        r.eventsUpdated && `${r.eventsUpdated} updated (evidence refreshed, your edits kept)`,
        r.eventsUnchanged && `${r.eventsUnchanged} unchanged — no duplicates created`,
      ]
        .filter(Boolean)
        .join(" · "),
      action: newCandidateIds.length || updatedCandidateIds.length ? { label: "Open inbox", onClick: () => router.push("/inbox") } : undefined,
    });
  };

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button variant="outline" size="sm" onClick={run} disabled={syncing || !hasIdentity} className={cn("gap-2 tabular-nums", syncing && "text-ai")} aria-live="polite">
          {syncing ? <RefreshCw className="size-3.5 animate-spin" /> : upToDate ? <CalendarCheck2 className="size-3.5 text-success" /> : <RefreshCw className="size-3.5" />}
          <span className="hidden sm:inline">
            {syncing ? "Syncing calendar…" : lastRun ? `Synced ${fmtAgo(lastRun.finishedAt)}` : "Sync calendar"}
          </span>
          {!syncing && !upToDate && lastRun && <span className="rounded-full bg-ai/10 px-1.5 text-[10px] font-semibold text-ai">new</span>}
        </Button>
      </TooltipTrigger>
      <TooltipContent>{!hasIdentity ? "Link a Google Calendar first (Settings)" : upToDate ? "Re-sync: safe to repeat, never duplicates" : mock ? "Pull the next incremental batch from Google Calendar (mock)" : "Incremental sync of every linked calendar"}</TooltipContent>
    </Tooltip>
  );
}
