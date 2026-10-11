"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { AnimatePresence, LayoutGroup, motion } from "motion/react";
import { Filter, SlidersHorizontal, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { Combobox } from "@/components/common/combobox";
import { ClientMark, MemberAvatar } from "@/components/common/marks";
import { Sparkle } from "@/components/common/sparkle";
import { catalogOf, SESSION_STATE_LABELS } from "@/lib/domain/labels";
import { useHydrated, useStore } from "@/lib/domain/store";
import { allItems, applyFilters, DEFAULT_FILTERS, groupItems, type GroupBy, type RangePreset, type TimelineFilters, type TimelineItem } from "@/lib/domain/timeline";
import type { SessionState } from "@/lib/domain/types";
import { fmtTimeRange } from "@/lib/format";
import { cn } from "@/lib/utils";
import { SidePeek } from "./side-peek";

export function TimelineView() {
  const router = useRouter();
  const params = useSearchParams();
  const hydrated = useHydrated();
  const state = useStore();
  const [filters, setFilters] = useState<TimelineFilters>({ ...DEFAULT_FILTERS, clientId: params.get("client"), scope: params.get("scope") === "mine" ? "mine" : "everyone" });
  const [peek, setPeek] = useState<TimelineItem | null>(null);
  const set = <K extends keyof TimelineFilters>(k: K, v: TimelineFilters[K]) => setFilters((f) => ({ ...f, [k]: v }));

  const items = useMemo(() => applyFilters(allItems(state, state.currentMemberId, filters.includeCandidates), filters, state.currentMemberId), [state, filters]);
  const groups = useMemo(() => groupItems(items, filters.groupBy), [items, filters.groupBy]);
  const activeFilters = [filters.clientId, filters.facilitatorId, filters.technologyId, filters.state !== "all" ? filters.state : null, filters.scope === "mine" ? "mine" : null].filter(Boolean).length;
  const counts = { sessions: items.filter((i) => i.kind === "session").length, candidates: items.filter((i) => i.kind === "candidate").length };

  const open = (it: TimelineItem) => (it.kind === "session" ? router.push(it.href!) : setPeek(it));

  if (!hydrated) return null;

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-6">
      {/* Controls */}
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <ToggleGroup type="single" variant="outline" size="sm" value={filters.groupBy} onValueChange={(v) => v && set("groupBy", v as GroupBy)} aria-label="Group by">
          {(["day", "week", "client", "member"] as GroupBy[]).map((g) => (
            <ToggleGroupItem key={g} value={g} className="px-3 text-xs capitalize">{g}</ToggleGroupItem>
          ))}
        </ToggleGroup>
        <ToggleGroup type="single" variant="outline" size="sm" value={filters.range} onValueChange={(v) => v && set("range", v as RangePreset)} aria-label="Date range">
          <ToggleGroupItem value="upcoming" className="px-3 text-xs">Upcoming</ToggleGroupItem>
          <ToggleGroupItem value="30d" className="px-3 text-xs">Last 30 days</ToggleGroupItem>
          <ToggleGroupItem value="month" className="px-3 text-xs">This month</ToggleGroupItem>
          <ToggleGroupItem value="all" className="px-3 text-xs">All</ToggleGroupItem>
        </ToggleGroup>
        <div className="ml-auto flex items-center gap-3 text-xs text-muted-foreground">
          <span className="tabular-nums">{counts.sessions} sessions{counts.candidates ? ` · ${counts.candidates} candidates` : ""}</span>
          <label className="flex items-center gap-1.5"><Switch checked={filters.includeCandidates} onCheckedChange={(v) => set("includeCandidates", v)} aria-label="Include candidates" /> candidates</label>
          <ToggleGroup type="single" variant="outline" size="sm" value={filters.scope} onValueChange={(v) => v && set("scope", v as TimelineFilters["scope"])}>
            <ToggleGroupItem value="mine" className="px-2.5 text-xs">Mine</ToggleGroupItem>
            <ToggleGroupItem value="everyone" className="px-2.5 text-xs">Everyone</ToggleGroupItem>
          </ToggleGroup>
        </div>
      </div>
      <div className="mb-6 flex flex-wrap items-center gap-2">
        <Filter className="size-3.5 text-muted-foreground" />
        <Combobox size="sm" value={filters.clientId} options={state.clients.map((c) => ({ value: c.id, label: c.name, icon: <ClientMark client={c} size="sm" /> }))} placeholder="Client" onChange={(v) => set("clientId", v)} clearable className="h-7 rounded-full text-xs" />
        <Combobox size="sm" value={filters.facilitatorId} options={state.members.filter((m) => m.role !== "MANAGER").map((m) => ({ value: m.id, label: m.name, icon: <MemberAvatar member={m} size="xs" /> }))} placeholder="Facilitator" onChange={(v) => set("facilitatorId", v)} clearable className="h-7 rounded-full text-xs" />
        <Combobox size="sm" value={filters.technologyId} options={catalogOf(state.catalog, "technology").map((t) => ({ value: t.id, label: t.label }))} placeholder="Technology" onChange={(v) => set("technologyId", v)} clearable className="h-7 rounded-full text-xs" />
        <Combobox size="sm" value={filters.state === "all" ? null : filters.state} options={(Object.keys(SESSION_STATE_LABELS) as SessionState[]).filter((s) => s !== "ignored").map((s) => ({ value: s, label: SESSION_STATE_LABELS[s] }))} placeholder="State" onChange={(v) => set("state", (v as SessionState | null) ?? "all")} clearable className="h-7 rounded-full text-xs" />
        {activeFilters > 0 && (
          <Button variant="ghost" size="xs" onClick={() => setFilters((f) => ({ ...DEFAULT_FILTERS, groupBy: f.groupBy, range: f.range, includeCandidates: f.includeCandidates }))} className="text-muted-foreground">
            <X data-icon="inline-start" /> Clear {activeFilters}
          </Button>
        )}
      </div>

      {groups.length === 0 && (
        <div className="rounded-3xl border border-dashed px-6 py-16 text-center">
          <SlidersHorizontal className="mx-auto mb-3 size-6 text-muted-foreground" />
          <p className="text-sm font-medium">Nothing in this view</p>
          <p className="mt-1 text-xs text-muted-foreground">Widen the date range or clear a filter.</p>
        </div>
      )}

      <LayoutGroup>
        <AnimatePresence initial={false}>
          {groups.map((g) => (
            <motion.section key={`${filters.groupBy}:${g.key}`} layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="mb-6">
              <div className="sticky top-0 z-10 -mx-2 mb-2 flex items-baseline gap-2 bg-surface/90 px-2 py-1.5 backdrop-blur">
                <h2 className="text-sm font-semibold">{g.label}</h2>
                {g.sublabel && <span className="text-xs text-muted-foreground">{g.sublabel}</span>}
                <span className="ml-auto text-xs tabular-nums text-muted-foreground">{g.items.length}</span>
              </div>
              <div className="overflow-hidden rounded-2xl border bg-card">
                {g.items.map((it, i) => (
                  <Row key={it.id} item={it} groupBy={filters.groupBy} first={i === 0} onOpen={() => open(it)} />
                ))}
              </div>
            </motion.section>
          ))}
        </AnimatePresence>
      </LayoutGroup>

      <SidePeek item={peek} onClose={() => setPeek(null)} />
    </div>
  );
}

function Row({ item, groupBy, first, onOpen }: { item: TimelineItem; groupBy: GroupBy; first: boolean; onOpen: () => void }) {
  const ghost = item.kind === "candidate";
  const dateCol = groupBy === "day" ? fmtTimeRange(item.start, item.end) : `${new Date(item.start).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })} · ${fmtTimeRange(item.start, item.end)}`;
  return (
    <motion.button
      layout="position"
      type="button"
      onClick={onOpen}
      className={cn(
        "grid w-full grid-cols-[150px_auto_1fr_auto] items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none",
        !first && "border-t border-border/60",
        ghost && "bg-[repeating-linear-gradient(135deg,transparent,transparent_8px,color-mix(in_oklch,var(--ai)_6%,transparent)_8px,color-mix(in_oklch,var(--ai)_6%,transparent)_9px)]",
      )}
    >
      <span className="text-xs tabular-nums text-muted-foreground">{dateCol}</span>
      <ClientMark client={item.client} size="sm" className={cn(ghost && "opacity-70")} />
      <span className="min-w-0">
        <span className="flex items-center gap-2">
          <span className={cn("truncate text-sm font-medium", ghost && "text-foreground/80")}>{item.title}</span>
          {ghost && <Badge variant="outline" className="shrink-0 border-dashed text-[10px]">{item.bucket === "likely" ? "likely session" : "needs a look"}</Badge>}
        </span>
        <span className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[11px] text-muted-foreground">
          {item.clientLabel && groupBy !== "client" && <span>{item.clientLabel}</span>}
          {item.sessionTypeLabel && <span>· {item.sessionTypeLabel}</span>}
          {item.technologies.slice(0, 3).map((t) => <span key={t.id} className="text-ai/80">#{t.label}</span>)}
          {item.technologies.length > 3 && <span>+{item.technologies.length - 3}</span>}
        </span>
      </span>
      <span className="flex items-center gap-2">
        {item.pending > 0 && <span className="flex items-center gap-1 text-[11px] text-ai"><Sparkle />{item.pending}</span>}
        <StatePill item={item} />
        {item.facilitator && groupBy !== "member" && <MemberAvatar member={item.facilitator} size="sm" />}
      </span>
    </motion.button>
  );
}

function StatePill({ item }: { item: TimelineItem }) {
  if (item.kind === "candidate") return null;
  if (item.state === "completed") {
    const done = item.completeness >= 1;
    return (
      <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-medium", done ? "bg-success/15 text-success" : "bg-warning/15 text-warning")}>
        {done ? `Feedback in${item.outcome ? ` · ${item.outcome.label}` : ""}` : `Feedback ${Math.round(item.completeness * 100)}%`}
      </span>
    );
  }
  return <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-medium", item.state === "planned" ? "bg-ai-soft text-ai" : "bg-muted text-muted-foreground")}>{SESSION_STATE_LABELS[item.state]}</span>;
}
