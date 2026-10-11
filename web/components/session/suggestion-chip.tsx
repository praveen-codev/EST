"use client";

import { Check, X } from "lucide-react";
import { motion } from "motion/react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { SuggestionEvidence } from "@/components/common/evidence";
import { Sparkle } from "@/components/common/sparkle";
import type { Suggestion } from "@/lib/domain/types";
import { pct } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * One AI proposal awaiting a decision. Focusable: Enter accepts, Backspace/Delete rejects.
 * The label opens the evidence; the two buttons decide.
 */
export function SuggestionChip({ suggestion, onAccept, onReject, className }: { suggestion: Suggestion; onAccept: () => void; onReject: () => void; className?: string }) {
  const low = suggestion.confidence < 0.5;
  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.9, transition: { duration: 0.15 } }}
      tabIndex={0}
      role="group"
      aria-label={`Suggestion: ${suggestion.label}, ${pct(suggestion.confidence)} confident. Enter to accept, Backspace to reject.`}
      onKeyDown={(e) => {
        if (e.key === "Enter") { e.preventDefault(); onAccept(); }
        if (e.key === "Backspace" || e.key === "Delete") { e.preventDefault(); onReject(); }
      }}
      className={cn(
        "inline-flex h-7 items-center overflow-hidden rounded-full border text-xs outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ai/50",
        low ? "border-dashed border-border text-muted-foreground" : "border-ai/30 bg-ai-soft/60 text-foreground",
        className,
      )}
    >
      <Popover>
        <PopoverTrigger asChild>
          <button type="button" className="flex h-full items-center gap-1.5 pl-2.5 pr-1.5 hover:bg-ai/10" aria-label="Show evidence">
            <Sparkle className={cn("size-3", low && "text-muted-foreground")} />
            <span className="max-w-48 truncate font-medium">{suggestion.label}</span>
            <span className="tabular-nums text-[10px] text-muted-foreground">{pct(suggestion.confidence)}</span>
          </button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-3" align="start">
          <SuggestionEvidence suggestion={suggestion} />
        </PopoverContent>
      </Popover>
      <button type="button" onClick={onAccept} className="flex h-full items-center border-l border-ai/20 px-1.5 text-ai hover:bg-ai hover:text-ai-foreground" aria-label={`Accept ${suggestion.label}`}>
        <Check className="size-3.5" />
      </button>
      <button type="button" onClick={onReject} className="flex h-full items-center border-l border-ai/20 px-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" aria-label={`Reject ${suggestion.label}`}>
        <X className="size-3.5" />
      </button>
    </motion.div>
  );
}
