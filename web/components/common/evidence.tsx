"use client";

import { AlignLeft, CalendarClock, History, MapPin, Tags, Type, UserRound, Users } from "lucide-react";
import type { Evidence, EvidenceSource, Suggestion } from "@/lib/domain/types";
import { pct } from "@/lib/format";
import { cn } from "@/lib/utils";

const SOURCE_META: Record<EvidenceSource, { icon: typeof Type; label: string }> = {
  "calendar.title": { icon: Type, label: "Invite title" },
  "calendar.description": { icon: AlignLeft, label: "Invite description" },
  "calendar.attendees": { icon: Users, label: "Attendees" },
  "calendar.organizer": { icon: UserRound, label: "Organizer" },
  "calendar.time": { icon: CalendarClock, label: "Time slot" },
  "calendar.location": { icon: MapPin, label: "Location" },
  "history.client": { icon: History, label: "Team history" },
  "history.team": { icon: History, label: "Team history" },
  "history.people": { icon: History, label: "Teammates' sessions" },
  "history.tags": { icon: History, label: "Teammates' tags" },
  "history.sessions": { icon: History, label: "Prior sessions" },
  "taxonomy.alias": { icon: Tags, label: "Tag alias" },
};

export function EvidenceList({ evidence, className }: { evidence: Evidence[]; className?: string }) {
  return (
    <ul className={cn("space-y-2", className)}>
      {evidence.map((e, i) => {
        const meta = SOURCE_META[e.source];
        const Icon = meta.icon;
        const fromHistory = e.source.startsWith("history.");
        return (
          <li key={i} className="flex gap-2 text-xs">
            <Icon className={cn("mt-0.5 size-3.5 shrink-0", fromHistory ? "text-ai" : "text-muted-foreground")} />
            <div className="min-w-0">
              <div className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">{meta.label}</div>
              <div className="text-foreground/90">{e.excerpt}</div>
              {e.attribution && <div className="text-[11px] text-ai">{e.attribution}</div>}
            </div>
          </li>
        );
      })}
    </ul>
  );
}

export function SuggestionEvidence({ suggestion }: { suggestion: Suggestion }) {
  return (
    <div className="w-72">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <div className="text-xs font-semibold">{suggestion.label}</div>
        <div className="text-[11px] tabular-nums text-muted-foreground">{pct(suggestion.confidence)} confident</div>
      </div>
      <EvidenceList evidence={suggestion.evidence} />
      <div className="mt-2 border-t pt-1.5 text-[10px] text-muted-foreground">
        {suggestion.promptVersion} · requires your decision
      </div>
    </div>
  );
}
