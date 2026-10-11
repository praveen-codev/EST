"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Archive, ArchiveRestore, ArrowDown, ArrowUp, Lock, Plus, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { MemberAvatar } from "@/components/common/marks";
import { useCurrentMember, useHydrated, useStore } from "@/lib/domain/store";
import type { SessionTypeDef } from "@/lib/domain/types";
import { fmtAgo } from "@/lib/format";
import { cn } from "@/lib/utils";

export function SessionTypesAdmin() {
  const hydrated = useHydrated();
  const me = useCurrentMember();
  const state = useStore();
  const [showArchived, setShowArchived] = useState(false);
  const canEdit = me.role === "ADMIN";
  const rows = [...state.sessionTypes].sort((a, b) => a.sortOrder - b.sortOrder).filter((t) => showArchived || !t.archived);
  const usage = (id: string) => state.sessions.filter((s) => s.sessionType === id).length;

  if (!hydrated) return null;

  return (
    <div className="mx-auto w-full max-w-4xl px-6 py-6">
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold tracking-tight">Session formats</h2>
          <p className="mt-1 max-w-xl text-sm text-muted-foreground">
            The only meetings that count as enablement sessions. Calendar events are classified against the keywords below; anything
            else lands in <em>Needs a look</em> at most. Changes apply to future syncs and to <em>Re-run AI</em>.
          </p>
        </div>
        <div className={cn("flex items-center gap-2 rounded-full border px-3 py-1 text-xs", canEdit ? "border-success/40 text-success" : "text-muted-foreground")}>
          {canEdit ? <ShieldCheck className="size-3.5" /> : <Lock className="size-3.5" />}
          {canEdit ? `Editing as ${me.name} · admin` : `Read-only · ${me.role.toLowerCase()}s can't change the catalogue`}
        </div>
      </header>

      <div className="overflow-hidden rounded-2xl border bg-card">
        <div className="grid grid-cols-[28px_1.2fr_2fr_90px_60px_auto] items-center gap-3 border-b bg-muted/40 px-4 py-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
          <span /><span>Format</span><span>Keywords that identify it</span><span>Typical</span><span>Used</span><span className="text-right">Actions</span>
        </div>
        <AnimatePresence initial={false}>
          {rows.map((t, i) => (
            <TypeRow key={t.id} row={t} index={i} count={rows.length} usage={usage(t.id)} canEdit={canEdit} />
          ))}
        </AnimatePresence>
        {canEdit && <NewTypeRow />}
      </div>

      <label className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <Switch checked={showArchived} onCheckedChange={setShowArchived} /> Show archived formats
        <span>· {state.sessionTypes.filter((t) => t.archived).length} archived</span>
      </label>

      <section className="mt-8 rounded-2xl border border-dashed p-4 text-xs text-muted-foreground">
        <div className="mb-1 font-medium text-foreground">How this maps to the real system</div>
        This table is the <code>session_types</code> row set from the plan&apos;s taxonomy tables, persisted locally for the prototype. Archiving keeps
        historical sessions valid (their label still resolves) while stopping new matches. Every edit records who changed it and when.
      </section>
    </div>
  );
}

function TypeRow({ row, index, count, usage, canEdit }: { row: SessionTypeDef; index: number; count: number; usage: number; canEdit: boolean }) {
  const state = useStore();
  const [keywords, setKeywords] = useState(row.keywords.join(", "));
  const [label, setLabel] = useState(row.label);
  const [description, setDescription] = useState(row.description);
  const [minutes, setMinutes] = useState(String(row.typicalMinutes));
  const updatedBy = state.members.find((m) => m.id === row.updatedBy) ?? null;

  const commit = () => {
    const nextKeywords = keywords.split(",").map((k) => k.trim()).filter(Boolean);
    const changed = label.trim() !== row.label || description !== row.description || Number(minutes) !== row.typicalMinutes || nextKeywords.join("|") !== row.keywords.join("|");
    if (!changed || !label.trim()) return;
    state.upsertSessionType(row.id, { label: label.trim(), description, keywords: nextKeywords, typicalMinutes: Number(minutes) || row.typicalMinutes });
    toast.success(`Saved “${label.trim()}”`, { description: "Future syncs and AI re-runs use the new keywords." });
  };

  return (
    <motion.div layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className={cn("grid grid-cols-[28px_1.2fr_2fr_90px_60px_auto] items-start gap-3 border-b px-4 py-3 last:border-b-0", row.archived && "opacity-60")}>
      <div className="flex flex-col pt-1">
        <button type="button" disabled={!canEdit || index === 0} onClick={() => state.moveSessionType(row.id, -1)} className="text-muted-foreground hover:text-foreground disabled:opacity-30" aria-label="Move up"><ArrowUp className="size-3.5" /></button>
        <button type="button" disabled={!canEdit || index === count - 1} onClick={() => state.moveSessionType(row.id, 1)} className="text-muted-foreground hover:text-foreground disabled:opacity-30" aria-label="Move down"><ArrowDown className="size-3.5" /></button>
      </div>
      <div className="space-y-1">
        <Input value={label} onChange={(e) => setLabel(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label="Format label" className="h-8 font-medium" />
        <Input value={description} onChange={(e) => setDescription(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label="Description" placeholder="Short description" className="h-7 text-xs" />
        <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground">
          <code className="rounded bg-muted px-1">{row.id}</code>
          {row.archived && <Badge variant="outline" className="text-[10px]">archived</Badge>}
          {updatedBy && row.updatedAt && <span className="flex items-center gap-1">· <MemberAvatar member={updatedBy} size="xs" /> {fmtAgo(row.updatedAt)}</span>}
        </div>
      </div>
      <div>
        <Input value={keywords} onChange={(e) => setKeywords(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label={`Keywords for ${row.label}`} className="h-8 font-mono text-xs" />
        <div className="mt-1.5 flex flex-wrap gap-1">
          {keywords.split(",").map((k) => k.trim()).filter(Boolean).map((k) => <Badge key={k} variant="secondary" className="text-[10px]">{k}</Badge>)}
        </div>
      </div>
      <div className="flex items-center gap-1 text-xs">
        <Input type="number" min={15} step={15} value={minutes} onChange={(e) => setMinutes(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label="Typical minutes" className="h-8 w-16 text-xs" /> min
      </div>
      <div className="pt-1.5 text-sm tabular-nums">{usage}</div>
      <div className="flex justify-end pt-0.5">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              disabled={!canEdit}
              aria-label={row.archived ? "Restore format" : "Archive format"}
              onClick={() => {
                state.upsertSessionType(row.id, { archived: !row.archived });
                toast(row.archived ? `Restored “${row.label}”` : `Archived “${row.label}”`, { description: row.archived ? "It matches calendar events again." : `${usage} existing session${usage === 1 ? "" : "s"} keep their label; no new matches.` });
              }}
            >
              {row.archived ? <ArchiveRestore /> : <Archive />}
            </Button>
          </TooltipTrigger>
          <TooltipContent>{row.archived ? "Restore" : "Archive (keeps history valid)"}</TooltipContent>
        </Tooltip>
      </div>
    </motion.div>
  );
}

function NewTypeRow() {
  const upsert = useStore((s) => s.upsertSessionType);
  const [label, setLabel] = useState("");
  const [keywords, setKeywords] = useState("");
  const [minutes, setMinutes] = useState("60");
  const add = () => {
    if (!label.trim()) return;
    upsert(null, { label: label.trim(), keywords: keywords.split(",").map((k) => k.trim()).filter(Boolean), typicalMinutes: Number(minutes) || 60 });
    toast.success(`Added “${label.trim()}”`);
    setLabel(""); setKeywords(""); setMinutes("60");
  };
  return (
    <form
      onSubmit={(e) => { e.preventDefault(); add(); }}
      className="grid grid-cols-[28px_1.2fr_2fr_90px_60px_auto] items-center gap-3 border-t bg-muted/20 px-4 py-3"
    >
      <Plus className="size-4 text-muted-foreground" />
      <Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="New format, e.g. Lunch & learn" aria-label="New format label" className="h-8" />
      <Input value={keywords} onChange={(e) => setKeywords(e.target.value)} placeholder="keywords, comma separated (label is added automatically)" aria-label="New format keywords" className="h-8 font-mono text-xs" />
      <div className="flex items-center gap-1 text-xs"><Input type="number" min={15} step={15} value={minutes} onChange={(e) => setMinutes(e.target.value)} aria-label="New format typical minutes" className="h-8 w-16 text-xs" /> min</div>
      <span />
      <div className="flex justify-end"><Button type="submit" size="sm" disabled={!label.trim()}>Add</Button></div>
    </form>
  );
}
