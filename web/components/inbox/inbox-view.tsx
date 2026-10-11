"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { CheckCheck, ChevronDown, Inbox, PartyPopper } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import { Skeleton } from "@/components/ui/skeleton";
import { sessionIdForEvent } from "@/lib/domain/tiers";
import { useHydrated, useStore } from "@/lib/domain/store";
import { inboxGroups, inboxStats, type CandidateVM } from "@/lib/domain/selectors";
import { useShortcuts } from "@/lib/hooks/use-shortcuts";
import { cn } from "@/lib/utils";
import { CandidateCard } from "./candidate-card";
import { InboxStatsStrip } from "./inbox-stats";

export function InboxView() {
  const router = useRouter();
  const hydrated = useHydrated();
  const state = useStore();
  const me = state.members.find((m) => m.id === state.currentMemberId)!;
  const groups = useMemo(() => inboxGroups(state, state.currentMemberId), [state]);
  const stats = useMemo(() => inboxStats(state, groups, state.currentMemberId), [state, groups]);
  const [showUnlikely, setShowUnlikely] = useState(false);
  const [showDismissed, setShowDismissed] = useState(false);
  const [requestedHighlight, setHighlightId] = useState<string | null>(null);

  const ordered = useMemo(() => [...groups.likely, ...groups.review, ...groups.duplicates], [groups]);
  // Derived, not synced: fall back to the first card whenever the requested one is gone.
  const highlightId = ordered.some((v) => v.candidate.id === requestedHighlight) ? requestedHighlight : ordered[0]?.candidate.id ?? null;
  useEffect(() => {
    if (!highlightId) return;
    document.querySelector(`[data-candidate-id="${highlightId}"]`)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [highlightId]);

  const confirm = (vm: CandidateVM) => {
    state.confirmCandidate(vm.candidate.id);
    toast.success(`Confirmed · ${vm.clientLabel ?? vm.event.title}`, {
      description: `${vm.prefill.length} prefilled fields recorded as your decision${vm.chips.length ? ` · ${vm.chips.length} suggestions left to review` : ""}.`,
      action: { label: "Open", onClick: () => router.push(`/sessions/${sessionIdFor(vm)}`) },
    });
  };
  const dismiss = (vm: CandidateVM) => {
    state.dismissCandidate(vm.candidate.id);
    toast(`Dismissed · ${vm.event.title}`, { action: { label: "Undo", onClick: () => state.restoreCandidate(vm.candidate.id) } });
  };
  const open = (vm: CandidateVM) => {
    if (vm.candidate.status === "open") state.confirmCandidate(vm.candidate.id);
    router.push(`/sessions/${sessionIdFor(vm)}`);
  };
  const merge = (vm: CandidateVM) => {
    if (vm.duplicateOf && vm.duplicateOf.candidate.status === "open") state.confirmCandidate(vm.duplicateOf.candidate.id);
    state.mergeDuplicate(vm.candidate.id);
    toast.success("Merged duplicate", { description: "Both invites now point at one session." });
  };
  const confirmAllLikely = () => {
    if (!groups.likely.length) return;
    const n = groups.likely.length;
    state.confirmMany(groups.likely.map((g) => g.candidate.id));
    toast.success(`Confirmed ${n} session${n === 1 ? "" : "s"}`, {
      description: "Every prefilled value was recorded as an accepted decision. Lower-confidence suggestions wait in each session.",
      action: { label: "Timeline", onClick: () => router.push("/timeline") },
    });
  };

  const highlighted = ordered.find((v) => v.candidate.id === highlightId) ?? null;
  const move = (delta: number) => {
    if (!ordered.length) return;
    const idx = Math.max(0, ordered.findIndex((v) => v.candidate.id === highlightId));
    setHighlightId(ordered[(idx + delta + ordered.length) % ordered.length]!.candidate.id);
  };
  useShortcuts(
    {
      j: () => move(1),
      k: () => move(-1),
      Enter: () => highlighted && confirm(highlighted),
      "shift+Enter": confirmAllLikely,
      x: () => highlighted && dismiss(highlighted),
      e: () => highlighted && open(highlighted),
    },
    [highlighted, ordered, groups],
  );

  const syncing = state.syncing;
  const nothingOpen = ordered.length === 0;
  const upToDate = state.features.remainingPages === 0;

  if (!hydrated) return null;

  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-6">
      <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs text-muted-foreground">
            {me.name.split(" ")[0]}&apos;s calendar · {stats.lastRun ? `last sync looked at ${stats.lastRun.eventsSeen} events` : "not synced yet"}
          </p>
          <InboxStatsStrip stats={stats} className="mt-1" />
        </div>
        {groups.likely.length > 1 && (
          <Button onClick={confirmAllLikely} className="gap-2 bg-ai text-ai-foreground hover:bg-ai/90">
            <CheckCheck data-icon="inline-start" /> Confirm all likely ({groups.likely.length})
            <KbdGroup className="ml-1 opacity-70">
              <Kbd className="bg-ai-foreground/15 text-ai-foreground">⇧</Kbd>
              <Kbd className="bg-ai-foreground/15 text-ai-foreground">↵</Kbd>
            </KbdGroup>
          </Button>
        )}
      </header>

      {syncing && nothingOpen && <SyncingSkeleton />}

      {!syncing && nothingOpen && (
        <EmptyInbox upToDate={upToDate} hasUnlikely={groups.unlikely.length > 0} onSync={() => void state.syncNow()} onTimeline={() => router.push("/timeline")} />
      )}

      <Section title="Likely sessions" hint="High confidence. One click confirms everything prefilled." count={groups.likely.length} tone="success">
        <AnimatePresence initial={false} mode="popLayout">
          {groups.likely.map((vm) => (
            <CandidateCard key={vm.candidate.id} vm={vm} variant="likely" highlighted={vm.candidate.id === highlightId} showReasoning={state.settings.showReasoning} onConfirm={() => confirm(vm)} onDismiss={() => dismiss(vm)} onOpen={() => open(vm)} onHover={() => setHighlightId(vm.candidate.id)} />
          ))}
        </AnimatePresence>
      </Section>

      <Section title="Needs a look" hint="Ambiguous invites. The AI would rather ask than guess." count={groups.review.length} tone="warning">
        <AnimatePresence initial={false} mode="popLayout">
          {groups.review.map((vm) => (
            <CandidateCard key={vm.candidate.id} vm={vm} variant="review" highlighted={vm.candidate.id === highlightId} showReasoning={state.settings.showReasoning} onConfirm={() => confirm(vm)} onDismiss={() => dismiss(vm)} onOpen={() => open(vm)} onHover={() => setHighlightId(vm.candidate.id)} />
          ))}
        </AnimatePresence>
      </Section>

      <Section title="Possible duplicates" hint="Same meeting, two invites. Merge keeps one session with both sources." count={groups.duplicates.length} tone="warning">
        <AnimatePresence initial={false} mode="popLayout">
          {groups.duplicates.map((vm) => (
            <CandidateCard key={vm.candidate.id} vm={vm} variant="duplicate" highlighted={vm.candidate.id === highlightId} showReasoning={state.settings.showReasoning} onConfirm={() => confirm(vm)} onDismiss={() => dismiss(vm)} onOpen={() => open(vm)} onMerge={() => merge(vm)} onHover={() => setHighlightId(vm.candidate.id)} />
          ))}
        </AnimatePresence>
      </Section>

      {groups.unlikely.length > 0 && (
        <CollapsedSection
          open={showUnlikely}
          onToggle={() => setShowUnlikely((o) => !o)}
          label={`${groups.unlikely.length} event${groups.unlikely.length === 1 ? "" : "s"} skipped as not enablement`}
          hint="1:1s, standups, interviews, cancelled. Nothing is deleted — confirm any of them if we got it wrong."
        >
          {groups.unlikely.map((vm) => (
            <CandidateCard key={vm.candidate.id} vm={vm} variant="unlikely" highlighted={false} showReasoning onConfirm={() => confirm(vm)} onDismiss={() => dismiss(vm)} onOpen={() => open(vm)} onHover={() => {}} />
          ))}
        </CollapsedSection>
      )}

      {groups.dismissed.length > 0 && (
        <CollapsedSection open={showDismissed} onToggle={() => setShowDismissed((o) => !o)} label={`${groups.dismissed.length} dismissed or merged`} hint="Restore brings a card back to the queue.">
          {groups.dismissed.map((vm) => (
            <CandidateCard key={vm.candidate.id} vm={vm} variant="dismissed" highlighted={false} showReasoning={false} onConfirm={() => {}} onDismiss={() => {}} onOpen={() => {}} onRestore={() => state.restoreCandidate(vm.candidate.id)} onHover={() => {}} />
          ))}
        </CollapsedSection>
      )}
    </div>
  );
}

const sessionIdFor = (vm: CandidateVM) => sessionIdForEvent(vm.event.id);

function Section({ title, hint, count, tone, children }: { title: string; hint: string; count: number; tone: "success" | "warning"; children: React.ReactNode }) {
  if (count === 0) return null;
  return (
    <section className="mb-8" aria-label={title}>
      <div className="mb-3 flex items-baseline gap-2">
        <span className={cn("size-2 rounded-full", tone === "success" ? "bg-success" : "bg-warning")} aria-hidden />
        <h2 className="text-sm font-semibold">{title}</h2>
        <span className="text-xs tabular-nums text-muted-foreground">{count}</span>
        <span className="hidden text-xs text-muted-foreground sm:inline">· {hint}</span>
      </div>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function CollapsedSection({ open, onToggle, label, hint, children }: { open: boolean; onToggle: () => void; label: string; hint: string; children: React.ReactNode }) {
  return (
    <section className="mb-6">
      <button type="button" onClick={onToggle} className="flex w-full items-center gap-2 rounded-xl border border-dashed px-4 py-3 text-left text-sm text-muted-foreground transition-colors hover:bg-muted/50" aria-expanded={open}>
        <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} />
        <span className="font-medium text-foreground/80">{label}</span>
        <span className="hidden text-xs sm:inline">· {hint}</span>
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} className="overflow-hidden">
            <div className="space-y-3 pt-3">{children}</div>
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  );
}

function SyncingSkeleton() {
  return (
    <div className="space-y-3" aria-busy>
      {[0, 1, 2].map((i) => (
        <div key={i} className="grid grid-cols-[88px_1fr_auto] gap-4 rounded-2xl border p-4">
          <div className="space-y-2"><Skeleton className="h-3 w-14" /><Skeleton className="h-3 w-10" /><Skeleton className="mt-3 h-3 w-16" /></div>
          <div className="space-y-2"><Skeleton className="h-4 w-2/3" /><Skeleton className="h-3 w-1/3" /><div className="flex gap-2 pt-2"><Skeleton className="h-6 w-24 rounded-full" /><Skeleton className="h-6 w-20 rounded-full" /><Skeleton className="h-6 w-16 rounded-full" /></div></div>
          <Skeleton className="size-9 rounded-full" />
        </div>
      ))}
    </div>
  );
}

function EmptyInbox({ upToDate, hasUnlikely, onSync, onTimeline }: { upToDate: boolean; hasUnlikely: boolean; onSync: () => void; onTimeline: () => void }) {
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="mb-8 flex flex-col items-center rounded-3xl border border-dashed px-6 py-14 text-center">
      <div className="ai-gradient mb-4 flex size-12 items-center justify-center rounded-2xl text-white shadow-lg shadow-ai/25">
        {upToDate ? <PartyPopper className="size-6" /> : <Inbox className="size-6" />}
      </div>
      <h2 className="text-lg font-semibold tracking-tight">{upToDate ? "Inbox zero" : "Nothing waiting"}</h2>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">
        {upToDate
          ? `Every candidate has a decision.${hasUnlikely ? " Skipped events stay below in case we got one wrong." : ""}`
          : "Your calendar has more to sync. Each sync is incremental and never creates duplicates."}
      </p>
      <div className="mt-5 flex gap-2">
        {!upToDate && <Button onClick={onSync}>Sync calendar</Button>}
        <Button variant={upToDate ? "default" : "outline"} onClick={onTimeline}>Open timeline</Button>
      </div>
    </motion.div>
  );
}
