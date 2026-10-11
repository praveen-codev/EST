"use client";

import { useEffect, useRef } from "react";
import { motion } from "motion/react";
import { Check, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Combobox } from "@/components/common/combobox";
import { RequiredMark, SelectWithOther, type Creator } from "@/components/common/other-option";
import { Sparkle } from "@/components/common/sparkle";
import type { CatalogItem, UseCase } from "@/lib/domain/types";
import { cn } from "@/lib/utils";

interface Props {
  index: number;
  useCase: UseCase;
  useCaseTypes: CatalogItem[];
  technologies: CatalogItem[];
  /** Technologies already in play for the session; shown as one-click toggles first. */
  quickTechnologyIds: string[];
  autoFocus?: boolean;
  onChange: (patch: Partial<Omit<UseCase, "id">>) => void;
  onRemove: () => void;
  onCreateType: Creator;
  onCreateTechnology: Creator;
}

export function UseCaseCard({ index, useCase, useCaseTypes, technologies, quickTechnologyIds, autoFocus, onChange, onRemove, onCreateType, onCreateTechnology }: Props) {
  const titleRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (autoFocus) titleRef.current?.focus();
  }, [autoFocus]);

  const toggleTech = (id: string) =>
    onChange({ technologyIds: useCase.technologyIds.includes(id) ? useCase.technologyIds.filter((t) => t !== id) : [...useCase.technologyIds, id] });
  const quick = quickTechnologyIds.map((id) => technologies.find((t) => t.id === id)).filter((t): t is CatalogItem => !!t);
  const chosenOutsideQuick = useCase.technologyIds.filter((id) => !quickTechnologyIds.includes(id)).map((id) => technologies.find((t) => t.id === id)).filter((t): t is CatalogItem => !!t);

  return (
    <motion.div layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.98 }} className="rounded-2xl border bg-background/40 p-4">
      <div className="flex items-start gap-3">
        <span className="mt-1.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-muted text-[11px] font-semibold tabular-nums">{index + 1}</span>
        <div className="min-w-0 flex-1 space-y-2.5">
          <div className="flex items-center gap-1 text-[11px] font-medium text-muted-foreground">Title<RequiredMark /></div>
          <Input ref={titleRef} value={useCase.title} onChange={(e) => onChange({ title: e.target.value })} placeholder="Use case title, e.g. Ab Initio graphs → PySpark on Databricks" aria-label={`Use case ${index + 1} title`} className="h-9 font-medium" />
          <Textarea value={useCase.description} onChange={(e) => onChange({ description: e.target.value })} placeholder="What did the team try, what did Devin do, what's next?" aria-label={`Use case ${index + 1} description`} rows={2} className="text-sm" />
          <div className="grid gap-3 sm:grid-cols-[220px_1fr]">
            <div>
              <div className="mb-1 text-[11px] font-medium text-muted-foreground">Use-case type<RequiredMark /></div>
              <SelectWithOther
                ariaLabel={`Use case ${index + 1} type`}
                value={useCase.useCaseTypeId}
                options={useCaseTypes.map((t) => ({ value: t.id, label: t.label }))}
                placeholder="Choose a type"
                onChange={(v) => onChange({ useCaseTypeId: v })}
                onCreate={onCreateType}
                otherPlaceholder="New use-case type…"
                className="w-full"
              />
            </div>
            <div>
              <div className="mb-1 flex items-center gap-1 text-[11px] font-medium text-muted-foreground">
                Technologies {quick.length > 0 && <span className="flex items-center gap-1 text-ai"><Sparkle className="size-3" /> from the session</span>}
              </div>
              <div className="flex flex-wrap items-center gap-1.5">
                {quick.map((t) => {
                  const on = useCase.technologyIds.includes(t.id);
                  return (
                    <button key={t.id} type="button" onClick={() => toggleTech(t.id)} aria-pressed={on} className={cn("inline-flex h-7 items-center gap-1 rounded-full border px-2.5 text-xs transition-colors", on ? "border-ai/40 bg-ai-soft text-foreground" : "border-dashed text-muted-foreground hover:bg-muted")}>
                      {on && <Check className="size-3 text-ai" />}
                      {t.label}
                    </button>
                  );
                })}
                {chosenOutsideQuick.map((t) => (
                  <button key={t.id} type="button" onClick={() => toggleTech(t.id)} aria-pressed className="inline-flex h-7 items-center gap-1 rounded-full border border-ai/40 bg-ai-soft px-2.5 text-xs">
                    <Check className="size-3 text-ai" /> {t.label}
                  </button>
                ))}
                <Combobox
                  value={null}
                  options={technologies.filter((t) => !useCase.technologyIds.includes(t.id)).map((t) => ({ value: t.id, label: t.label }))}
                  onChange={(v) => v && !useCase.technologyIds.includes(v) && toggleTech(v)}
                  onCreate={onCreateTechnology}
                  otherPlaceholder="New technology…"
                  searchPlaceholder="Search technologies…"
                  trigger={<Button variant="outline" size="xs" className="h-7 rounded-full">+ technology</Button>}
                />
              </div>
            </div>
          </div>
        </div>
        <Button variant="ghost" size="icon-sm" aria-label={`Remove use case ${index + 1}`} onClick={onRemove} className="text-muted-foreground hover:text-destructive">
          <Trash2 />
        </Button>
      </div>
    </motion.div>
  );
}
