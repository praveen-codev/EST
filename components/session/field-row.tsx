"use client";

import { AnimatePresence } from "motion/react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { Provenance, Suggestion } from "@/lib/domain/types";
import { cn } from "@/lib/utils";
import { SuggestionChip } from "./suggestion-chip";

const PROVENANCE_META: Record<Provenance, { label: string; className: string }> = {
  prefilled: { label: "Prefilled by AI and confirmed with the session", className: "bg-ai" },
  accepted: { label: "AI suggestion you accepted", className: "bg-success" },
  manual: { label: "Entered by hand — never overwritten by sync or AI", className: "bg-foreground" },
};

export function ProvenanceDot({ provenance }: { provenance: Provenance | undefined }) {
  if (!provenance) return <span className="inline-block size-1.5 rounded-full border border-border" aria-label="Not set" />;
  const meta = PROVENANCE_META[provenance];
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className={cn("inline-block size-1.5 rounded-full", meta.className)} aria-label={meta.label} />
      </TooltipTrigger>
      <TooltipContent>{meta.label}</TooltipContent>
    </Tooltip>
  );
}

interface FieldRowProps {
  id: string;
  label: string;
  provenance?: Provenance;
  pending?: Suggestion[];
  onAccept?: (s: Suggestion) => void;
  onReject?: (s: Suggestion) => void;
  hint?: string;
  children: React.ReactNode;
  className?: string;
}

export function FieldRow({ id, label, provenance, pending = [], onAccept, onReject, hint, children, className }: FieldRowProps) {
  return (
    <div id={`field-${id}`} className={cn("grid scroll-mt-24 gap-1.5 py-3 sm:grid-cols-[150px_1fr] sm:gap-4", className)}>
      <div className="flex items-center gap-2 pt-1.5 text-xs font-medium text-muted-foreground">
        <ProvenanceDot provenance={provenance} />
        <span>{label}</span>
      </div>
      <div className="min-w-0">
        {children}
        {hint && <p className="mt-1 text-[11px] text-muted-foreground">{hint}</p>}
        {pending.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5" role="list" aria-label={`${pending.length} suggestions for ${label}`}>
            <AnimatePresence initial={false}>
              {pending.map((s) => (
                <SuggestionChip key={s.id} suggestion={s} onAccept={() => onAccept?.(s)} onReject={() => onReject?.(s)} />
              ))}
            </AnimatePresence>
          </div>
        )}
      </div>
    </div>
  );
}
