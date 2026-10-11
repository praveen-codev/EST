"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { format } from "date-fns";
import { motion } from "motion/react";
import { ArrowRight, CalendarDays, CheckCircle2, ClipboardPen, MapPin, Sparkles, Video } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { AttendeeStack, ClientMark, MemberAvatar } from "@/components/common/marks";
import { ConfidenceRing } from "@/components/common/confidence-ring";
import { Sparkle } from "@/components/common/sparkle";
import { sessionIdForEvent } from "@/lib/domain/ops";
import { useHydrated, useStore } from "@/lib/domain/store";
import { todayBoard } from "@/lib/domain/today";
import type { TimelineItem } from "@/lib/domain/timeline";
import { durationMin, fmtDuration, fmtTimeRange } from "@/lib/format";
import { cn } from "@/lib/utils";

export function TodayView() {
  const hydrated = useHydrated();
  const router = useRouter();
  const state = useStore();
  const me = state.members.find((m) => m.id === state.currentMemberId)!;
  const now = useClock();
  const board = useMemo(() => todayBoard(state, state.currentMemberId, now), [state, now]);

  if (!hydrated) return null;

  const openFeedback = (it: TimelineItem) => {
    if (it.kind === "candidate" && it.candidate) {
      state.confirmCandidate(it.candidate.id);
      router.push(`/sessions/${sessionIdForEvent(it.candidate.eventId)}`);
      return;
    }
    router.push(it.href!);
  };

  const done = board.today.filter((it) => it.completeness >= 1).length;

  return (
    <div className="mx-auto w-full max-w-4xl px-6 py-8">
      <header className="mb-6">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{format(now, "EEEE, d MMMM yyyy")}</p>
        <h2 className="mt-1 text-2xl font-semibold tracking-tight">
          {greeting(now)}, {me.name.split(" ")[0]}.{" "}
          <span className="text-muted-foreground">
            {board.today.length === 0 ? "No enablement sessions today." : `${board.today.length} session${board.today.length === 1 ? "" : "s"} today${done ? ` · ${done} with feedback in` : ""}.`}
          </span>
        </h2>
      </header>

      {state.syncing && board.today.length === 0 && (
        <div className="space-y-3" aria-busy>
          {[0, 1].map((i) => <Skeleton key={i} className="h-28 rounded-2xl" />)}
        </div>
      )}

      {!state.syncing && board.today.length === 0 && (
        <div className="rounded-3xl border border-dashed px-6 py-14 text-center">
          <CalendarDays className="mx-auto mb-3 size-6 text-muted-foreground" />
          <p className="text-sm font-medium">Nothing scheduled for you today</p>
          <p className="mt-1 text-xs text-muted-foreground">Sessions appear here on the day they run, with a button to enter feedback.</p>
          <div className="mt-5 flex justify-center gap-2">
            <Button variant="outline" asChild><Link href="/calendar">Open calendar</Link></Button>
            <Button variant="outline" asChild><Link href="/inbox">Review inbox</Link></Button>
          </div>
        </div>
      )}

      <ul className="space-y-3">
        {board.today.map((it, i) => (
          <TodayCard key={it.id} item={it} index={i} now={now} onOpen={() => openFeedback(it)} />
        ))}
      </ul>

      {board.overdue.length > 0 && (
        <Link href="/timeline?scope=mine" className="mt-8 flex items-center justify-between rounded-2xl border px-4 py-3 text-sm transition-colors hover:bg-muted/50">
          <span className="flex items-center gap-2">
            <ClipboardPen className="size-4 text-warning" />
            {board.overdue.length} earlier session{board.overdue.length === 1 ? "" : "s"} still need{board.overdue.length === 1 ? "s" : ""} feedback
            <span className="text-muted-foreground">· {board.overdue.slice(0, 2).map((o) => o.clientLabel ?? o.title).join(", ")}{board.overdue.length > 2 ? "…" : ""}</span>
          </span>
          <ArrowRight className="size-4 text-muted-foreground" />
        </Link>
      )}
    </div>
  );
}

/** Minute-resolution clock so "Live now" and the greeting stay current without impure reads during render. */
function useClock(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(t);
  }, []);
  return now;
}

function greeting(now: Date): string {
  const h = now.getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

function TodayCard({ item, index, now, onOpen }: { item: TimelineItem; index: number; now: Date; onOpen: () => void }) {
  const state = useStore();
  const event = item.candidate ? state.events.find((e) => e.id === item.candidate!.eventId) : state.events.find((e) => e.id === item.session?.sourceEventIds[0]);
  const external = (event?.attendees ?? []).filter((a) => !a.email.endsWith("@cognition.ai")).map((a) => a.name ?? a.email);
  const complete = item.completeness >= 1;
  const started = item.kind === "session" && item.session!.version > 1;
  const inProgress = Date.parse(item.start) <= now.getTime() && Date.parse(item.end) >= now.getTime();
  const pending = item.pending;

  return (
    <motion.li initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.05 }} className={cn("grid grid-cols-[96px_1fr_auto] items-center gap-4 rounded-2xl border bg-card p-4", inProgress && "border-ai/50 ring-1 ring-ai/30")}>
      <div className="border-r pr-3">
        <div className="text-lg font-semibold tabular-nums leading-none">{format(new Date(item.start), "HH:mm")}</div>
        <div className="mt-1 text-xs text-muted-foreground tabular-nums">{fmtTimeRange(item.start, item.end).split("–")[1]} · {fmtDuration(durationMin(item.start, item.end))}</div>
        {inProgress && <div className="mt-1.5 flex items-center gap-1 text-[11px] font-medium text-ai"><span className="size-1.5 animate-pulse rounded-full bg-ai" /> Live now</div>}
      </div>
      <div className="min-w-0">
        <div className="flex items-center gap-2.5">
          <ClientMark client={item.client} />
          <div className="min-w-0">
            <div className="truncate text-[15px] font-semibold tracking-tight">{item.title}</div>
            <div className="flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
              {item.clientLabel && <span>{item.clientLabel}</span>}
              {item.sessionTypeLabel && <span>· {item.sessionTypeLabel}</span>}
              {event?.conferencing && <span className="flex items-center gap-1">· <Video className="size-3" /> Meet</span>}
              {event?.location && <span className="flex items-center gap-1 truncate">· <MapPin className="size-3" /> {event.location}</span>}
            </div>
          </div>
        </div>
        <div className="mt-2.5 flex flex-wrap items-center gap-2 text-xs">
          {external.length > 0 && <span className="flex items-center gap-1.5 text-muted-foreground"><AttendeeStack names={external} /> {external.length} from the client</span>}
          {item.facilitator && <span className="flex items-center gap-1 text-muted-foreground"><MemberAvatar member={item.facilitator} size="xs" /> {item.facilitator.name.split(" ")[0]}</span>}
          {complete ? (
            <Badge className="gap-1 bg-success/15 text-success"><CheckCircle2 className="size-3" /> Feedback complete</Badge>
          ) : started ? (
            <Badge variant="outline" className="gap-1"><ClipboardPen className="size-3" /> Feedback {Math.round(item.completeness * 100)}%</Badge>
          ) : pending > 0 ? (
            <Badge variant="outline" className="gap-1 border-ai/40 text-ai"><Sparkles className="size-3" /> {pending} field{pending === 1 ? "" : "s"} suggested by AI</Badge>
          ) : null}
          {item.kind === "candidate" && <Badge variant="outline" className="border-dashed text-[10px]">from calendar · not yet confirmed</Badge>}
        </div>
      </div>
      <div className="flex flex-col items-end gap-2">
        {item.kind === "session" && <ConfidenceRing value={item.completeness} size={36} label={`${Math.round(item.completeness * 100)}% of feedback entered`} />}
        <Button onClick={onOpen} className={cn("gap-1.5", complete ? "" : "bg-ai text-ai-foreground hover:bg-ai/90")} variant={complete ? "outline" : "default"}>
          {complete ? <>Review feedback</> : <>Enter feedback <Sparkle className="size-3.5 text-ai-foreground" /></>}
        </Button>
      </div>
    </motion.li>
  );
}
