"use client";

import Link from "next/link";
import { format } from "date-fns";
import { EyeOff, History, ShieldCheck } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { MemberAvatar } from "@/components/common/marks";
import { SESSION_TYPE_LABELS } from "@/lib/domain/labels";
import type { DataState } from "@/lib/domain/ops";
import type { SessionVM } from "@/lib/domain/selectors";
import { fmtDayLabel } from "@/lib/format";

export function EvidencePanel({ vm, state }: { vm: SessionVM; state: DataState }) {
  const candidate = state.candidates.find((c) => c.sessionId === vm.session.id) ?? null;
  const event = vm.sourceEvents[0];
  if (!event) {
    return (
      <Panel title="Evidence" icon={ShieldCheck}>
        <p className="text-xs text-muted-foreground">This session was logged directly, without a calendar source.</p>
      </Panel>
    );
  }
  return (
    <Panel title="What the AI saw" icon={ShieldCheck}>
      <p className="mb-2 text-[11px] text-muted-foreground">Calendar text after redaction. Links, passcodes and phone numbers never reach the model.</p>
      <pre className="scrollbar-thin max-h-56 overflow-auto whitespace-pre-wrap rounded-lg bg-muted/60 p-3 font-mono text-[11px] leading-relaxed">{candidate?.redactedText ?? `${event.title}\n\n${event.description}`}</pre>
      {candidate?.redactions.length ? (
        <div className="mt-2 flex flex-wrap gap-1">
          {candidate.redactions.map((r) => (
            <Badge key={r} variant="outline" className="gap-1 text-[10px]">
              <EyeOff className="size-3" /> {r} redacted
            </Badge>
          ))}
        </div>
      ) : null}
      <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-[11px]">
        <dt className="text-muted-foreground">Organizer</dt>
        <dd className="truncate">{event.organizerEmail}</dd>
        <dt className="text-muted-foreground">Attendees</dt>
        <dd>{event.attendees.length}</dd>
        <dt className="text-muted-foreground">Sources</dt>
        <dd>{vm.sourceEvents.length} calendar event{vm.sourceEvents.length === 1 ? "" : "s"}</dd>
        {candidate && (
          <>
            <dt className="text-muted-foreground">Likelihood</dt>
            <dd>{Math.round(candidate.classification.score * 100)}% · {candidate.classification.reasons[0]}</dd>
          </>
        )}
      </dl>
    </Panel>
  );
}

export function HistoryPanel({ vm, state }: { vm: SessionVM; state: DataState }) {
  if (!vm.client) {
    return (
      <Panel title="Team history" icon={History}>
        <p className="text-xs text-muted-foreground">Pick a client to see what teammates already know about them.</p>
      </Panel>
    );
  }
  return (
    <Panel title={`${vm.client.name} · team history`} icon={History}>
      {vm.history.length === 0 ? (
        <p className="text-xs text-muted-foreground">First session with this client. Your decisions here will prefill the next one.</p>
      ) : (
        <ul className="space-y-2.5">
          {vm.history.slice(0, 6).map((h) => {
            const by = state.members.find((m) => m.id === h.facilitatorId);
            const tags = h.tagIds.slice(0, 3).map((id) => state.tags.find((t) => t.id === id)?.label).filter(Boolean);
            return (
              <li key={h.id}>
                <Link href={`/sessions/${h.id}`} className="block rounded-lg p-2 -mx-2 transition-colors hover:bg-muted/60">
                  <div className="flex items-center gap-2">
                    {by && <MemberAvatar member={by} size="xs" />}
                    <span className="truncate text-xs font-medium">{h.title}</span>
                  </div>
                  <div className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[11px] text-muted-foreground">
                    <span>{fmtDayLabel(h.scheduledStart)} · {format(new Date(h.scheduledStart), "yyyy")}</span>
                    {h.sessionType && <span>· {SESSION_TYPE_LABELS[h.sessionType]}</span>}
                    {by && <span>· {by.name.split(" ")[0]}</span>}
                  </div>
                  {tags.length > 0 && <div className="mt-1 truncate text-[11px] text-ai">{tags.join(" · ")}</div>}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

function Panel({ title, icon: Icon, children }: { title: string; icon: typeof History; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl border bg-card p-4">
      <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold">
        <Icon className="size-3.5 text-muted-foreground" /> {title}
      </h3>
      {children}
    </section>
  );
}
