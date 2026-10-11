"use client";

import { motion } from "motion/react";
import { Check, Copy, Info, MapPin, PencilLine, Repeat, Video, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { AttendeeStack, ClientMark } from "@/components/common/marks";
import { ConfidenceRing } from "@/components/common/confidence-ring";
import { SuggestionEvidence } from "@/components/common/evidence";
import { Sparkle } from "@/components/common/sparkle";
import type { CandidateVM } from "@/lib/domain/selectors";
import type { Suggestion } from "@/lib/domain/types";
import { durationMin, fmtDayParts, fmtDuration, fmtTimeRange } from "@/lib/format";
import { cn } from "@/lib/utils";

export interface CandidateCardProps {
  vm: CandidateVM;
  highlighted: boolean;
  showReasoning: boolean;
  variant: "likely" | "review" | "duplicate" | "unlikely" | "dismissed";
  onConfirm: () => void;
  onDismiss: () => void;
  onOpen: () => void;
  onMerge?: () => void;
  onRestore?: () => void;
  onHover: () => void;
}

export function CandidateCard({ vm, highlighted, showReasoning, variant, onConfirm, onDismiss, onOpen, onMerge, onRestore, onHover }: CandidateCardProps) {
  const { event, candidate, coverage } = vm;
  const best = (field: Suggestion["field"]) => vm.suggestions.filter((s) => s.field === field).sort((a, b) => b.confidence - a.confidence)[0];
  const clientSug = best("clientId");
  const teamSug = best("teamId");
  const typeSug = best("sessionType");
  const tagSugs = vm.suggestions.filter((s) => s.field === "tagIds").sort((a, b) => b.confidence - a.confidence).slice(0, 4);
  const muted = variant === "unlikely" || variant === "dismissed";
  const attendeeNames = vm.externalAttendees.map((a) => a.name ?? a.email);

  return (
    <motion.article
      layout
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: 80, scale: 0.98, transition: { duration: 0.22 } }}
      transition={{ type: "spring", stiffness: 420, damping: 36 }}
      onMouseEnter={onHover}
      data-candidate-id={candidate.id}
      className={cn(
        "group relative grid grid-cols-[88px_1fr_auto] gap-4 rounded-2xl border bg-card p-4 transition-shadow",
        highlighted ? "border-ai/50 shadow-lg shadow-ai/10 ring-1 ring-ai/30" : "border-border/70 hover:shadow-md",
        muted && "opacity-70",
      )}
      aria-label={event.title}
    >
      {/* Time column */}
      <div className="flex flex-col border-r border-border/60 pr-3 text-xs">
        <span className="font-semibold">{fmtDayParts(event.start)[0]}</span>
        <span className="text-muted-foreground">{fmtDayParts(event.start)[1]}</span>
        <span className="mt-2 tabular-nums text-muted-foreground">{fmtTimeRange(event.start, event.end)}</span>
        <span className="text-[11px] text-muted-foreground/80">{fmtDuration(durationMin(event.start, event.end))}</span>
      </div>

      {/* Body */}
      <div className="min-w-0">
        <div className="flex items-start gap-2.5">
          <ClientMark client={vm.client} className="mt-0.5" />
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-[15px] font-semibold leading-tight tracking-tight">{event.title}</h3>
            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
              {attendeeNames.length > 0 && (
                <span className="flex items-center gap-1.5">
                  <AttendeeStack names={attendeeNames} />
                  {attendeeNames.length} external
                </span>
              )}
              {event.conferencing && <span className="flex items-center gap-1"><Video className="size-3" /> Meet</span>}
              {event.location && <span className="flex items-center gap-1 truncate"><MapPin className="size-3" /> {event.location}</span>}
              {vm.recurringSiblings > 1 && <span className="flex items-center gap-1"><Repeat className="size-3" /> {vm.recurringSiblings} occurrences</span>}
              {event.status === "cancelled" && <Badge variant="destructive">Cancelled</Badge>}
            </div>
          </div>
        </div>

        {variant === "duplicate" && vm.duplicateOf && (
          <div className="mt-3 flex items-center gap-2 rounded-lg bg-warning/10 px-3 py-2 text-xs">
            <Copy className="size-3.5 text-warning" />
            Looks like the same meeting as <span className="font-medium">{vm.duplicateOf.event.title}</span> (same time, shared attendees).
          </div>
        )}

        {!muted && vm.suggestions.length > 0 && (
          <>
            <div className="mt-3 flex flex-wrap items-center gap-1.5">
              {clientSug && <ProposalChip suggestion={clientSug} prefix="Client" />}
              {teamSug && <ProposalChip suggestion={teamSug} prefix="Team" />}
              {typeSug && <ProposalChip suggestion={typeSug} prefix="Format" />}
              {tagSugs.map((t) => (
                <ProposalChip key={t.id} suggestion={t} />
              ))}
              {vm.suggestions.length > 4 + [clientSug, teamSug, typeSug].filter(Boolean).length && (
                <span className="text-[11px] text-muted-foreground">+{vm.suggestions.length - tagSugs.length - [clientSug, teamSug, typeSug].filter(Boolean).length} more</span>
              )}
            </div>
            <div className="mt-3 flex items-center gap-3">
              <div className="h-1.5 w-40 overflow-hidden rounded-full bg-muted">
                <motion.div
                  className="ai-gradient h-full rounded-full"
                  initial={{ width: 0 }}
                  animate={{ width: `${(coverage.filled / coverage.total) * 100}%` }}
                  transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
                />
              </div>
              <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                <Sparkle /> {coverage.filled} of {coverage.total} fields prefilled
                {vm.chips.length > 0 && <span>· {vm.chips.length} to review</span>}
              </span>
            </div>
          </>
        )}

        {(showReasoning || muted) && (
          <ul className="mt-2 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-muted-foreground">
            {candidate.classification.reasons.slice(0, 3).map((r) => (
              <li key={r} className="flex items-center gap-1">
                <Info className="size-3" /> {r}
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Actions */}
      <div className="flex flex-col items-end gap-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <div>
              <ConfidenceRing value={candidate.classification.score} />
            </div>
          </TooltipTrigger>
          <TooltipContent side="left" className="max-w-64">
            <div className="font-medium">Session likelihood</div>
            <ul className="mt-1 list-disc pl-3 text-[11px] opacity-90">
              {candidate.classification.reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          </TooltipContent>
        </Tooltip>
        <div className="mt-auto flex items-center gap-1">
          {variant === "dismissed" && onRestore && (
            <Button size="sm" variant="outline" onClick={onRestore}>Restore</Button>
          )}
          {variant === "duplicate" && onMerge && (
            <Button size="sm" variant="outline" onClick={onMerge}>
              <Copy data-icon="inline-start" /> Merge
            </Button>
          )}
          {variant !== "dismissed" && (
            <>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button size="icon-sm" variant="ghost" aria-label="Dismiss" onClick={onDismiss}>
                    <X />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Not a session <kbd className="ml-1 font-mono">X</kbd></TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button size="icon-sm" variant="ghost" aria-label="Open and edit" onClick={onOpen}>
                    <PencilLine />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Edit before confirming <kbd className="ml-1 font-mono">E</kbd></TooltipContent>
              </Tooltip>
              {variant !== "unlikely" && (
                <Button size="sm" onClick={onConfirm} className={cn("ml-1 gap-1.5", variant === "likely" && "bg-ai text-ai-foreground hover:bg-ai/90")}>
                  <Check data-icon="inline-start" /> Confirm
                </Button>
              )}
            </>
          )}
        </div>
      </div>
    </motion.article>
  );
}

function ProposalChip({ suggestion, prefix, label }: { suggestion: Suggestion; prefix?: string; label?: string }) {
  const prefill = suggestion.confidence >= 0.85;
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={cn(
            "inline-flex h-6 max-w-56 items-center gap-1 rounded-full border px-2 text-[11px] font-medium transition-colors",
            prefill ? "border-transparent bg-ai-soft text-ai hover:bg-ai/15" : "border-dashed border-ai/40 text-foreground/80 hover:bg-muted",
          )}
          aria-label={`${prefix ? `${prefix}: ` : ""}${label ?? suggestion.label}, ${Math.round(suggestion.confidence * 100)}% confident. Show evidence`}
        >
          {prefill && <Sparkle className="size-3" />}
          {prefix && <span className="opacity-60">{prefix}</span>}
          <span className="truncate">{label ?? suggestion.label}</span>
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-3" align="start">
        <SuggestionEvidence suggestion={suggestion} />
      </PopoverContent>
    </Popover>
  );
}
