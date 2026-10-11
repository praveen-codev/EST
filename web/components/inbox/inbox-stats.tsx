"use client";

import { Sparkles } from "lucide-react";
import { AnimatedNumber } from "@/components/common/animated-number";
import type { InboxStats } from "@/lib/domain/selectors";
import { cn } from "@/lib/utils";

export function InboxStatsStrip({ stats, className }: { stats: InboxStats; className?: string }) {
  const items = [
    { label: "events found", value: stats.newEvents, tone: "" },
    { label: "likely sessions", value: stats.likely, tone: "text-success" },
    { label: "need a look", value: stats.review, tone: "text-warning" },
  ];
  return (
    <div className={cn("flex flex-wrap items-center gap-x-6 gap-y-2", className)}>
      {items.map((it) => (
        <div key={it.label} className="flex items-baseline gap-1.5">
          <AnimatedNumber value={it.value} className={cn("text-2xl font-semibold tabular-nums tracking-tight", it.tone)} />
          <span className="text-xs text-muted-foreground">{it.label}</span>
        </div>
      ))}
      <div className="flex items-center gap-2 rounded-full bg-ai-soft px-3 py-1 text-xs font-medium text-ai">
        <Sparkles className="size-3.5" />
        AI prefilled <AnimatedNumber value={stats.prefillRatio * 100} format={(n) => `${Math.round(n)}%`} className="tabular-nums" /> of fields
      </div>
    </div>
  );
}
