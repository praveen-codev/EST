"use client";

import { useRouter } from "next/navigation";
import { ArrowUpRight, Check, X } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { ClientMark, MemberAvatar } from "@/components/common/marks";
import { AiBadge } from "@/components/common/sparkle";
import { EvidenceList } from "@/components/common/evidence";
import { AUTOMATION_OUTCOME_LABELS, SESSION_STATE_LABELS, SESSION_TYPE_LABELS, SUCCESS_STATE_LABELS } from "@/lib/domain/labels";
import { sessionIdForEvent } from "@/lib/domain/ops";
import { useStore } from "@/lib/domain/store";
import type { TimelineItem } from "@/lib/domain/timeline";
import { durationMin, fmtDayLong, fmtDuration, fmtTimeRange } from "@/lib/format";

export function SidePeek({ item, onClose }: { item: TimelineItem | null; onClose: () => void }) {
  const router = useRouter();
  const state = useStore();
  const open = item !== null;
  const sessionHref = item?.kind === "session" ? item.href! : item?.candidate ? `/sessions/${sessionIdForEvent(item.candidate.eventId)}` : null;
  const topSuggestions = item?.candidate ? state.suggestions.filter((s) => s.candidateId === item.candidate!.id && s.status === "proposed").sort((a, b) => b.confidence - a.confidence).slice(0, 5) : [];

  const confirm = () => {
    if (!item?.candidate) return;
    state.confirmCandidate(item.candidate.id);
    toast.success(`Confirmed · ${item.clientLabel ?? item.title}`);
    onClose();
  };
  const dismiss = () => {
    if (!item?.candidate) return;
    state.dismissCandidate(item.candidate.id);
    toast(`Dismissed · ${item.title}`, { action: { label: "Undo", onClick: () => state.restoreCandidate(item.candidate!.id) } });
    onClose();
  };

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        {item && (
          <>
            <SheetHeader>
              <div className="flex items-center gap-3">
                <ClientMark client={item.client} size="lg" />
                <div className="min-w-0">
                  <SheetTitle className="truncate text-base">{item.title}</SheetTitle>
                  <SheetDescription>{fmtDayLong(item.start)} · {fmtTimeRange(item.start, item.end)} · {fmtDuration(durationMin(item.start, item.end))}</SheetDescription>
                </div>
              </div>
            </SheetHeader>
            <div className="space-y-5 px-4 pb-6">
              <div className="flex flex-wrap items-center gap-1.5">
                <Badge variant={item.kind === "candidate" ? "outline" : "secondary"} className={item.kind === "candidate" ? "border-dashed" : undefined}>
                  {item.kind === "candidate" ? `Candidate · ${item.bucket === "likely" ? "likely" : "needs a look"}` : SESSION_STATE_LABELS[item.state]}
                </Badge>
                {item.sessionType && <Badge variant="outline">{SESSION_TYPE_LABELS[item.sessionType]}</Badge>}
                {item.success && item.success !== "unknown" && <Badge variant="outline">{SUCCESS_STATE_LABELS[item.success]}</Badge>}
                {item.outcome && <Badge variant="outline">{AUTOMATION_OUTCOME_LABELS[item.outcome]}</Badge>}
                {item.pending > 0 && <AiBadge>{item.pending} suggestion{item.pending === 1 ? "" : "s"}</AiBadge>}
              </div>
              <dl className="grid grid-cols-[110px_1fr] gap-y-2 text-sm">
                <dt className="text-muted-foreground">Client</dt>
                <dd>{item.clientLabel ?? <span className="text-muted-foreground">Not set</span>}</dd>
                <dt className="text-muted-foreground">Facilitator</dt>
                <dd className="flex items-center gap-1.5">{item.facilitator ? <><MemberAvatar member={item.facilitator} size="xs" /> {item.facilitator.name}</> : <span className="text-muted-foreground">Not set</span>}</dd>
                <dt className="text-muted-foreground">Participants</dt>
                <dd>{item.participants || <span className="text-muted-foreground">Unknown</span>}</dd>
                <dt className="text-muted-foreground">Tags</dt>
                <dd className="flex flex-wrap gap-1">{item.tags.length ? item.tags.map((t) => <Badge key={t.id} variant="outline" className="text-[10px]">{t.label}</Badge>) : <span className="text-muted-foreground">None yet</span>}</dd>
              </dl>
              {item.session?.summary && <p className="rounded-xl bg-muted/60 p-3 text-sm">{item.session.summary}</p>}
              {topSuggestions.length > 0 && (
                <div>
                  <div className="mb-2 text-xs font-semibold">What will be prefilled on confirm</div>
                  <ul className="space-y-2">
                    {topSuggestions.map((s) => (
                      <li key={s.id} className="rounded-xl border p-2.5">
                        <div className="flex items-center justify-between text-xs"><span className="font-medium">{s.label}</span><span className="tabular-nums text-muted-foreground">{Math.round(s.confidence * 100)}%</span></div>
                        <EvidenceList evidence={s.evidence.slice(0, 1)} className="mt-1" />
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="flex gap-2 pt-2">
                {item.kind === "candidate" ? (
                  <>
                    <Button onClick={confirm} className="flex-1 bg-ai text-ai-foreground hover:bg-ai/90"><Check data-icon="inline-start" /> Confirm</Button>
                    <Button variant="outline" onClick={dismiss}><X data-icon="inline-start" /> Dismiss</Button>
                  </>
                ) : (
                  <Button onClick={() => { onClose(); router.push(sessionHref!); }} className="flex-1">Open session <ArrowUpRight data-icon="inline-end" /></Button>
                )}
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
