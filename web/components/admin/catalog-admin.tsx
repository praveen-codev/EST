"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Archive, ArchiveRestore, ArrowDown, ArrowUp, Lock, Plus, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { MemberAvatar } from "@/components/common/marks";
import { RequiredMark } from "@/components/common/other-option";
import { CATALOG_KIND_META, catalogOf } from "@/lib/domain/labels";
import { useCurrentMember, useHydrated, useStore } from "@/lib/domain/store";
import type { CatalogItem, CatalogKind } from "@/lib/domain/types";
import { fmtAgo } from "@/lib/format";
import { cn } from "@/lib/utils";

const KINDS: CatalogKind[] = ["sessionType", "sessionOutcome", "useCaseType", "technology", "contactRole"];

export function CatalogAdmin() {
  const hydrated = useHydrated();
  const me = useCurrentMember();
  const canEdit = me.role === "ADMIN";
  const [kind, setKind] = useState<CatalogKind>("sessionType");

  if (!hydrated) return null;

  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-6">
      <header className="mb-5 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold tracking-tight">Reference tables</h2>
          <p className="mt-1 max-w-xl text-sm text-muted-foreground">
            The lists the feedback form and the AI draw from. Edits apply immediately to new sessions, future syncs and <em>Re-run AI</em>; archived rows keep old sessions valid.
          </p>
        </div>
        <div className={cn("flex items-center gap-2 rounded-full border px-3 py-1 text-xs", canEdit ? "border-success/40 text-success" : "text-muted-foreground")}>
          {canEdit ? <ShieldCheck className="size-3.5" /> : <Lock className="size-3.5" />}
          {canEdit ? `Editing as ${me.name} · admin` : `Read-only · ${me.role.toLowerCase()}s can't change these tables`}
        </div>
      </header>

      <Tabs value={kind} onValueChange={(v) => setKind(v as CatalogKind)}>
        <TabsList className="mb-4">
          {KINDS.map((k) => (
            <TabsTrigger key={k} value={k} className="text-xs">{CATALOG_KIND_META[k].title}</TabsTrigger>
          ))}
        </TabsList>
        {KINDS.map((k) => (
          <TabsContent key={k} value={k}>
            <CatalogTable kind={k} canEdit={canEdit} />
          </TabsContent>
        ))}
      </Tabs>

      <section className="mt-8 rounded-2xl border border-dashed p-4 text-xs text-muted-foreground">
        <div className="mb-1 font-medium text-foreground">How this maps to the real system</div>
        Four rows sets of one <code>catalog_items</code> table (kind, label, keywords, sort order, archived, audit), persisted locally for the prototype.
        Formats gate what counts as a session; outcomes and use-case types feed reports; technologies are suggested from invite text.
      </section>
    </div>
  );
}

function CatalogTable({ kind, canEdit }: { kind: CatalogKind; canEdit: boolean }) {
  const state = useStore();
  const [showArchived, setShowArchived] = useState(false);
  const meta = CATALOG_KIND_META[kind];
  const rows = catalogOf(state.catalog, kind, true).filter((t) => showArchived || !t.archived);
  const usage = (id: string) => {
    switch (kind) {
      case "sessionType": return state.sessions.filter((s) => s.sessionType === id).length;
      case "sessionOutcome": return state.sessions.filter((s) => s.outcomeId === id).length;
      case "technology": return state.sessions.filter((s) => s.technologyIds.includes(id)).length;
      case "useCaseType": return state.sessions.reduce((n, s) => n + s.useCases.filter((u) => u.useCaseTypeId === id).length, 0);
      case "contactRole": return state.sessions.reduce((n, s) => n + s.contacts.filter((c) => c.role === id).length, 0);
    }
  };
  const cols = meta.hasKeywords
    ? meta.hasMinutes ? "grid-cols-[28px_1.2fr_2fr_90px_60px_auto]" : "grid-cols-[28px_1.2fr_2fr_60px_auto]"
    : "grid-cols-[28px_1.4fr_1.6fr_60px_auto]";

  return (
    <>
      <p className="mb-3 text-xs text-muted-foreground">{meta.hint}</p>
      <div className="overflow-hidden rounded-2xl border bg-card">
        <div className={cn("grid items-center gap-3 border-b bg-muted/40 px-4 py-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground", cols)}>
          <span />
          <span>{meta.singular}</span>
          <span>{meta.hasKeywords ? "Keywords the AI looks for" : "Description"}</span>
          {meta.hasMinutes && <span>Typical</span>}
          <span>Used</span>
          <span className="text-right">Actions</span>
        </div>
        <AnimatePresence initial={false}>
          {rows.map((t, i) => (
            <CatalogRow key={t.id} kind={kind} row={t} index={i} count={rows.length} usage={usage(t.id)} canEdit={canEdit} cols={cols} />
          ))}
        </AnimatePresence>
        {canEdit && <NewRow kind={kind} cols={cols} />}
      </div>
      <label className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <Switch checked={showArchived} onCheckedChange={setShowArchived} /> Show archived
        <span>· {catalogOf(state.catalog, kind, true).filter((t) => t.archived).length} archived</span>
      </label>
    </>
  );
}

function CatalogRow({ kind, row, index, count, usage, canEdit, cols }: { kind: CatalogKind; row: CatalogItem; index: number; count: number; usage: number; canEdit: boolean; cols: string }) {
  const state = useStore();
  const meta = CATALOG_KIND_META[kind];
  const [keywords, setKeywords] = useState(row.keywords.join(", "));
  const [label, setLabel] = useState(row.label);
  const [description, setDescription] = useState(row.description);
  const [minutes, setMinutes] = useState(String(row.typicalMinutes ?? ""));
  const updatedBy = state.members.find((m) => m.id === row.updatedBy) ?? null;

  const commit = () => {
    const nextKeywords = keywords.split(",").map((k) => k.trim()).filter(Boolean);
    const nextMinutes: number | null = meta.hasMinutes ? Number(minutes) || row.typicalMinutes : row.typicalMinutes;
    const changed = label.trim() !== row.label || description !== row.description || nextMinutes !== row.typicalMinutes || nextKeywords.join("|") !== row.keywords.join("|");
    if (!changed || !label.trim()) return;
    state.upsertCatalogItem(kind, row.id, { label: label.trim(), description, keywords: nextKeywords, typicalMinutes: nextMinutes });
    toast.success(`Saved “${label.trim()}”`);
  };

  return (
    <motion.div layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className={cn("grid items-start gap-3 border-b px-4 py-3 last:border-b-0", cols, row.archived && "opacity-60")}>
      <div className="flex flex-col pt-1">
        <button type="button" disabled={!canEdit || index === 0} onClick={() => state.moveCatalogItem(kind, row.id, -1)} className="text-muted-foreground hover:text-foreground disabled:opacity-30" aria-label="Move up"><ArrowUp className="size-3.5" /></button>
        <button type="button" disabled={!canEdit || index === count - 1} onClick={() => state.moveCatalogItem(kind, row.id, 1)} className="text-muted-foreground hover:text-foreground disabled:opacity-30" aria-label="Move down"><ArrowDown className="size-3.5" /></button>
      </div>
      <div className="space-y-1">
        <Input value={label} onChange={(e) => setLabel(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label={`${meta.singular} label`} className="h-8 font-medium" />
        {meta.hasKeywords && <Input value={description} onChange={(e) => setDescription(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label="Description" placeholder="Short description" className="h-7 text-xs" />}
        <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground">
          <code className="rounded bg-muted px-1">{row.id}</code>
          {row.archived && <Badge variant="outline" className="text-[10px]">archived</Badge>}
          {updatedBy && row.updatedAt && <span className="flex items-center gap-1">· <MemberAvatar member={updatedBy} size="xs" /> {fmtAgo(row.updatedAt)}</span>}
        </div>
      </div>
      {meta.hasKeywords ? (
        <div>
          <Input value={keywords} onChange={(e) => setKeywords(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label={`Keywords for ${row.label}`} className="h-8 font-mono text-xs" />
          <div className="mt-1.5 flex flex-wrap gap-1">
            {keywords.split(",").map((k) => k.trim()).filter(Boolean).map((k) => <Badge key={k} variant="secondary" className="text-[10px]">{k}</Badge>)}
          </div>
        </div>
      ) : (
        <Input value={description} onChange={(e) => setDescription(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label={`Description for ${row.label}`} placeholder="What this means" className="h-8 text-xs" />
      )}
      {meta.hasMinutes && (
        <div className="flex items-center gap-1 text-xs">
          <Input type="number" min={15} step={15} value={minutes} onChange={(e) => setMinutes(e.target.value)} onBlur={commit} disabled={!canEdit} aria-label="Typical minutes" className="h-8 w-16 text-xs" /> min
        </div>
      )}
      <div className="pt-1.5 text-sm tabular-nums">{usage}</div>
      <div className="flex justify-end pt-0.5">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              disabled={!canEdit}
              aria-label={row.archived ? `Restore ${meta.singular}` : `Archive ${meta.singular}`}
              onClick={() => {
                state.upsertCatalogItem(kind, row.id, { archived: !row.archived });
                toast(row.archived ? `Restored “${row.label}”` : `Archived “${row.label}”`, { description: row.archived ? "Available again." : `${usage} existing record${usage === 1 ? "" : "s"} keep the label; no new uses.` });
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

function NewRow({ kind, cols }: { kind: CatalogKind; cols: string }) {
  const upsert = useStore((s) => s.upsertCatalogItem);
  const meta = CATALOG_KIND_META[kind];
  const [label, setLabel] = useState("");
  const [extra, setExtra] = useState("");
  const [minutes, setMinutes] = useState("60");
  const add = () => {
    if (!label.trim()) return;
    upsert(kind, null, {
      label: label.trim(),
      keywords: meta.hasKeywords ? extra.split(",").map((k) => k.trim()).filter(Boolean) : [],
      description: meta.hasKeywords ? "" : extra.trim(),
      typicalMinutes: meta.hasMinutes ? Number(minutes) || 60 : null,
    });
    toast.success(`Added “${label.trim()}”`);
    setLabel(""); setExtra(""); setMinutes("60");
  };
  return (
    <form onSubmit={(e) => { e.preventDefault(); add(); }} className={cn("grid items-center gap-3 border-t bg-muted/20 px-4 py-3", cols)}>
      <Plus className="size-4 text-muted-foreground" />
      <div className="flex items-center gap-1"><Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder={`New ${meta.singular}`} aria-label={`New ${meta.singular} label`} className="h-8" /><RequiredMark /></div>
      <Input value={extra} onChange={(e) => setExtra(e.target.value)} placeholder={meta.hasKeywords ? "keywords, comma separated (label is added automatically)" : "description"} aria-label={`New ${meta.singular} ${meta.hasKeywords ? "keywords" : "description"}`} className={cn("h-8", meta.hasKeywords && "font-mono text-xs")} />
      {meta.hasMinutes && <div className="flex items-center gap-1 text-xs"><Input type="number" min={15} step={15} value={minutes} onChange={(e) => setMinutes(e.target.value)} aria-label="New format typical minutes" className="h-8 w-16 text-xs" /> min</div>}
      <span />
      <div className="flex justify-end"><Button type="submit" size="sm" disabled={!label.trim()}>Add</Button></div>
    </form>
  );
}
