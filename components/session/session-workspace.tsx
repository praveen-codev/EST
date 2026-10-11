"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { format } from "date-fns";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, Check, CheckCheck, ChevronDown, CircleAlert, Loader2, RefreshCw, Sparkles, X } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { Combobox } from "@/components/common/combobox";
import { ConfidenceRing } from "@/components/common/confidence-ring";
import { ClientMark, MemberAvatar, PersonAvatar } from "@/components/common/marks";
import { AiBadge } from "@/components/common/sparkle";
import {
  AUTOMATION_OUTCOME_LABELS,
  AUTOMATION_OUTCOMES,
  CONTACT_ROLE_LABELS,
  CONTACT_ROLES,
  DEVIN_ASK_LABELS,
  DEVIN_ASKS,
  SESSION_STATE_LABELS,
  SUCCESS_STATE_LABELS,
  SUCCESS_STATES,
  TAG_CATEGORY_LABELS,
} from "@/lib/domain/labels";
import { useHydrated, useStore } from "@/lib/domain/store";
import { sessionVM } from "@/lib/domain/selectors";
import type { ContactRole, SessionState, Suggestion } from "@/lib/domain/types";
import { durationMin, fmtDayLong, fmtDuration, fmtTimeRange } from "@/lib/format";
import { useShortcuts } from "@/lib/hooks/use-shortcuts";
import { cn } from "@/lib/utils";
import { FieldRow } from "./field-row";
import { EvidencePanel, HistoryPanel } from "./side-panels";

export function SessionWorkspace() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const hydrated = useHydrated();
  const state = useStore();
  const session = state.sessions.find((s) => s.id === id) ?? null;
  const vm = useMemo(() => (session ? sessionVM(state, session) : null), [state, session]);
  const saved = useSavedIndicator(session?.version ?? 0);
  const [advancedOpen, setAdvancedOpen] = useState(false);

  useShortcuts(
    {
      "mod+shift+a": () => session && acceptAll(),
      Escape: () => router.push("/inbox"),
    },
    [session?.id, vm?.pending.length],
  );

  if (!hydrated) return null;

  if (!session || !vm) {
    return (
      <div className="mx-auto max-w-xl px-6 py-20 text-center">
        <h2 className="text-lg font-semibold">Session not found</h2>
        <p className="mt-1 text-sm text-muted-foreground">It may belong to a different scenario, or the demo was reset.</p>
        <Button asChild className="mt-4"><Link href="/inbox">Back to inbox</Link></Button>
      </div>
    );
  }

  const patch = (p: Parameters<typeof state.updateSession>[1]) => state.updateSession(session.id, p);
  const accept = (s: Suggestion) => state.acceptSuggestion(s.id);
  const reject = (s: Suggestion) => state.rejectSuggestion(s.id);
  const pending = (field: Suggestion["field"]) => vm.pendingByField.get(field) ?? [];
  const acceptAll = () => {
    const n = vm.pending.filter((s) => s.confidence >= 0.5).length;
    if (!n) return;
    state.acceptAllForSession(session.id);
    toast.success(`Accepted ${n} suggestion${n === 1 ? "" : "s"}`, { description: "Each one is recorded as your decision with its evidence." });
  };
  const rerun = () => {
    state.rerunAI(session.id);
    toast("AI re-run complete", { description: "Only new, non-conflicting suggestions were added. Nothing you set was touched." });
  };
  const focusField = (field: string) => document.getElementById(`field-${field}`)?.scrollIntoView({ behavior: "smooth", block: "center" });

  const clientPeople = state.people.filter((p) => !vm.client || p.clientId === vm.client.id || p.clientId === null);
  const teams = state.teams.filter((t) => t.clientId === session.clientId);
  const members = state.members.filter((m) => m.role !== "MANAGER");
  const highConfidencePending = vm.pending.filter((s) => s.confidence >= 0.5).length;

  return (
    <div className="mx-auto grid w-full max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div className="min-w-0">
        {/* Breadcrumb + save state */}
        <div className="mb-4 flex items-center justify-between text-xs text-muted-foreground">
          <Link href="/inbox" className="inline-flex items-center gap-1 hover:text-foreground"><ArrowLeft className="size-3.5" /> Inbox</Link>
          <span className="flex items-center gap-1.5" aria-live="polite">
            {saved === "saving" ? <Loader2 className="size-3 animate-spin" /> : <Check className="size-3 text-success" />}
            {saved === "saving" ? "Saving…" : "All changes saved"} · v{session.version}
          </span>
        </div>

        {/* Hero */}
        <header className="rounded-3xl border bg-card p-5">
          <div className="flex items-start gap-4">
            <ClientMark client={vm.client} size="lg" />
            <div className="min-w-0 flex-1">
              <input
                value={session.title}
                onChange={(e) => patch({ title: e.target.value })}
                aria-label="Session title"
                className="w-full bg-transparent text-xl font-semibold tracking-tight outline-none placeholder:text-muted-foreground focus:ring-0"
                placeholder="Untitled session"
              />
              <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                <span>{fmtDayLong(session.scheduledStart)}</span>
                <span>·</span>
                <span className="tabular-nums">{fmtTimeRange(session.scheduledStart, session.scheduledEnd)} ({fmtDuration(durationMin(session.scheduledStart, session.scheduledEnd))})</span>
                {vm.client && <><span>·</span><span>{vm.client.name}{vm.team ? ` / ${vm.team.name}` : ""}</span></>}
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Select value={session.state} onValueChange={(v) => patch({ state: v as SessionState })}>
                  <SelectTrigger size="sm" className="h-7 w-auto gap-1.5 rounded-full text-xs" aria-label="Lifecycle state">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(Object.keys(SESSION_STATE_LABELS) as SessionState[]).filter((s) => s !== "candidate").map((s) => (
                      <SelectItem key={s} value={s}>{SESSION_STATE_LABELS[s]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {vm.facilitator && (
                  <span className="inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs">
                    <MemberAvatar member={vm.facilitator} size="xs" /> {vm.facilitator.name}
                  </span>
                )}
                {vm.pending.length > 0 && <AiBadge>{vm.pending.length} suggestion{vm.pending.length === 1 ? "" : "s"} waiting</AiBadge>}
              </div>
            </div>
            <div className="flex flex-col items-end gap-2">
              <ConfidenceRing value={vm.completeness} size={44} label={`${Math.round(vm.completeness * 100)}% complete`} />
              <div className="flex gap-1">
                <Button size="sm" variant="ghost" onClick={rerun} className="gap-1.5 text-muted-foreground"><RefreshCw data-icon="inline-start" /> Re-run AI</Button>
                {highConfidencePending > 0 && (
                  <Button size="sm" onClick={acceptAll} className="gap-1.5 bg-ai text-ai-foreground hover:bg-ai/90">
                    <CheckCheck data-icon="inline-start" /> Accept all ({highConfidencePending})
                  </Button>
                )}
              </div>
            </div>
          </div>
          {vm.missing.length > 0 && (
            <div className="mt-4 flex flex-wrap items-center gap-1.5 border-t pt-3 text-xs">
              <CircleAlert className="size-3.5 text-warning" />
              <span className="text-muted-foreground">Still missing:</span>
              {vm.missing.map((m) => (
                <button key={m.field} type="button" onClick={() => focusField(m.field)} className="rounded-full border border-dashed px-2 py-0.5 hover:border-foreground/40 hover:bg-muted">
                  {m.label}
                </button>
              ))}
            </div>
          )}
        </header>

        {/* Essentials */}
        <Section title="Essentials" subtitle="What, who for, and what Devin was asked to do.">
          <FieldRow id="clientId" label="Client" provenance={session.provenance.clientId} pending={pending("clientId")} onAccept={accept} onReject={reject}>
            <Combobox
              value={session.clientId}
              options={state.clients.map((c) => ({ value: c.id, label: c.name, hint: c.industry ?? undefined, icon: <ClientMark client={c} size="sm" /> }))}
              placeholder="Choose a client"
              onChange={(v) => patch({ clientId: v, teamId: null })}
              onCreate={(name) => patch({ clientId: state.createClient(name), teamId: null })}
              clearable
            />
          </FieldRow>
          <FieldRow id="teamId" label="Team" provenance={session.provenance.teamId} pending={pending("teamId")} onAccept={accept} onReject={reject}>
            <Combobox value={session.teamId} options={teams.map((t) => ({ value: t.id, label: t.name }))} placeholder={session.clientId ? "Choose a team" : "Pick a client first"} onChange={(v) => patch({ teamId: v })} clearable emptyText="No teams recorded for this client yet" />
          </FieldRow>
          <FieldRow id="sessionType" label="Session type" provenance={session.provenance.sessionType} pending={pending("sessionType")} onAccept={accept} onReject={reject}>
            <Select value={session.sessionType ?? ""} onValueChange={(v) => patch({ sessionType: v as typeof session.sessionType })}>
              <SelectTrigger className="w-60" aria-label="Session type"><SelectValue placeholder="Choose a format" /></SelectTrigger>
              <SelectContent>
                {state.sessionTypes.filter((t) => !t.archived || t.id === session.sessionType).sort((a, b) => a.sortOrder - b.sortOrder).map((t) => (
                  <SelectItem key={t.id} value={t.id}>{t.label}{t.archived ? " (archived)" : ""}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FieldRow>
          <FieldRow id="devinAsk" label="Devin ask" provenance={session.provenance.devinAsk} pending={pending("devinAsk")} onAccept={accept} onReject={reject}>
            <ToggleGroup type="single" variant="outline" size="sm" value={session.devinAsk ?? ""} onValueChange={(v) => v && patch({ devinAsk: v as typeof session.devinAsk })}>
              {DEVIN_ASKS.map((a) => <ToggleGroupItem key={a} value={a} className="text-xs">{DEVIN_ASK_LABELS[a]}</ToggleGroupItem>)}
            </ToggleGroup>
          </FieldRow>
          <FieldRow id="participantIds" label="Participants" provenance={session.provenance.participantIds ?? session.provenance.participantsEstimated} pending={[...pending("participantIds"), ...pending("participantsEstimated")]} onAccept={accept} onReject={reject} hint="Known participants come from the invite; the estimate covers people who weren't on it.">
            <div className="flex flex-wrap items-center gap-2">
              <div className="flex flex-wrap gap-1">
                {vm.participants.map((p) => (
                  <span key={p.id} className="inline-flex items-center gap-1 rounded-full bg-muted py-0.5 pl-0.5 pr-2 text-xs">
                    <PersonAvatar person={p} size="xs" /> {p.name}
                    <button type="button" aria-label={`Remove ${p.name}`} onClick={() => patch({ participantIds: session.participantIds.filter((x) => x !== p.id) })} className="ml-0.5 text-muted-foreground hover:text-foreground"><X className="size-3" /></button>
                  </span>
                ))}
                <Combobox
                  value={null}
                  options={clientPeople.filter((p) => !session.participantIds.includes(p.id)).map((p) => ({ value: p.id, label: p.name, hint: p.role ? CONTACT_ROLE_LABELS[p.role] : undefined }))}
                  onChange={(v) => v && patch({ participantIds: [...session.participantIds, v] })}
                  onCreate={(name) => patch({ participantIds: [...session.participantIds, state.createPerson(name, session.clientId)] })}
                  trigger={<Button variant="outline" size="xs" className="rounded-full">+ person</Button>}
                />
              </div>
              <span className="text-xs text-muted-foreground">·</span>
              <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                ~<Input type="number" min={0} value={session.participantsEstimated ?? ""} onChange={(e) => patch({ participantsEstimated: e.target.value === "" ? null : Number(e.target.value) })} className="h-7 w-16 text-xs" aria-label="Estimated participants" /> estimated
              </label>
            </div>
          </FieldRow>
          {session.state === "completed" && (
            <>
              <FieldRow id="successState" label="Success" provenance={session.provenance.successState}>
                <ToggleGroup type="single" variant="outline" size="sm" value={session.successState} onValueChange={(v) => v && patch({ successState: v as typeof session.successState })}>
                  {SUCCESS_STATES.map((s) => <ToggleGroupItem key={s} value={s} className={cn("text-xs", s === "successful" && "data-[state=on]:bg-success/15 data-[state=on]:text-success")}>{SUCCESS_STATE_LABELS[s]}</ToggleGroupItem>)}
                </ToggleGroup>
              </FieldRow>
              <FieldRow id="automationOutcome" label="Automation outcome" provenance={session.provenance.automationOutcome}>
                <ToggleGroup type="single" variant="outline" size="sm" value={session.automationOutcome ?? ""} onValueChange={(v) => v && patch({ automationOutcome: v as typeof session.automationOutcome })}>
                  {AUTOMATION_OUTCOMES.map((o) => <ToggleGroupItem key={o} value={o} className="text-xs">{AUTOMATION_OUTCOME_LABELS[o]}</ToggleGroupItem>)}
                </ToggleGroup>
              </FieldRow>
            </>
          )}
        </Section>

        {/* People */}
        <Section title="People" subtitle="Who ran it and who to follow up with.">
          <FieldRow id="facilitatorId" label="Facilitator" provenance={session.provenance.facilitatorId} pending={pending("facilitatorId")} onAccept={accept} onReject={reject}>
            <Combobox value={session.facilitatorId} options={members.map((m) => ({ value: m.id, label: m.name, hint: m.title, icon: <MemberAvatar member={m} size="xs" /> }))} onChange={(v) => patch({ facilitatorId: v })} placeholder="Choose a facilitator" />
          </FieldRow>
          <FieldRow id="coFacilitatorIds" label="Co-facilitators" provenance={session.provenance.coFacilitatorIds} pending={pending("coFacilitatorIds")} onAccept={accept} onReject={reject}>
            <div className="flex flex-wrap gap-1">
              {vm.coFacilitators.map((m) => (
                <span key={m.id} className="inline-flex items-center gap-1 rounded-full bg-muted py-0.5 pl-0.5 pr-2 text-xs">
                  <MemberAvatar member={m} size="xs" /> {m.name}
                  <button type="button" aria-label={`Remove ${m.name}`} onClick={() => patch({ coFacilitatorIds: session.coFacilitatorIds.filter((x) => x !== m.id) })} className="ml-0.5 text-muted-foreground hover:text-foreground"><X className="size-3" /></button>
                </span>
              ))}
              <Combobox value={null} options={members.filter((m) => m.id !== session.facilitatorId && !session.coFacilitatorIds.includes(m.id)).map((m) => ({ value: m.id, label: m.name, icon: <MemberAvatar member={m} size="xs" /> }))} onChange={(v) => v && patch({ coFacilitatorIds: [...session.coFacilitatorIds, v] })} trigger={<Button variant="outline" size="xs" className="rounded-full">+ teammate</Button>} />
            </div>
          </FieldRow>
          <FieldRow id="contacts" label="Contacts" provenance={session.provenance.contacts} pending={pending("contacts")} onAccept={accept} onReject={reject} hint="Roles are remembered per client and suggested to teammates next time.">
            <div className="space-y-1.5">
              {vm.contacts.map((c) => (
                <div key={`${c.personId}:${c.role}`} className="flex items-center gap-2 text-sm">
                  <PersonAvatar person={c.person} size="sm" />
                  <span className="font-medium">{c.person.name}</span>
                  <Select value={c.role} onValueChange={(role) => patch({ contacts: session.contacts.map((x) => (x.personId === c.personId && x.role === c.role ? { ...x, role: role as ContactRole } : x)) })}>
                    <SelectTrigger size="sm" className="h-6 w-auto gap-1 rounded-full text-[11px]" aria-label={`Role for ${c.person.name}`}><SelectValue /></SelectTrigger>
                    <SelectContent>{CONTACT_ROLES.map((r) => <SelectItem key={r} value={r}>{CONTACT_ROLE_LABELS[r]}</SelectItem>)}</SelectContent>
                  </Select>
                  <button type="button" aria-label={`Remove ${c.person.name}`} onClick={() => patch({ contacts: session.contacts.filter((x) => !(x.personId === c.personId && x.role === c.role)) })} className="text-muted-foreground hover:text-foreground"><X className="size-3.5" /></button>
                </div>
              ))}
              <Combobox
                value={null}
                options={clientPeople.filter((p) => !session.contacts.some((c) => c.personId === p.id)).map((p) => ({ value: p.id, label: p.name, hint: p.role ? CONTACT_ROLE_LABELS[p.role] : undefined }))}
                onChange={(v) => {
                  if (!v) return;
                  const person = state.people.find((p) => p.id === v);
                  patch({ contacts: [...session.contacts, { personId: v, role: person?.role ?? "engineer" }] });
                }}
                onCreate={(name) => patch({ contacts: [...session.contacts, { personId: state.createPerson(name, session.clientId), role: "engineer" }] })}
                trigger={<Button variant="outline" size="xs" className="rounded-full">+ contact</Button>}
              />
            </div>
          </FieldRow>
        </Section>

        {/* Tags */}
        <Section title="Tags" subtitle="Canonical tags. Aliases resolve automatically; teammates' usage shapes suggestions.">
          <FieldRow id="tagIds" label="Tags" provenance={session.provenance.tagIds} pending={pending("tagIds")} onAccept={accept} onReject={reject}>
            <div className="flex flex-wrap gap-1">
              <AnimatePresence initial={false}>
                {vm.tags.map((t) => (
                  <motion.span key={t.id} layout initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.9 }} className="inline-flex h-7 items-center gap-1 rounded-full border bg-background px-2.5 text-xs">
                    <span className="text-[10px] uppercase tracking-wide text-muted-foreground">{TAG_CATEGORY_LABELS[t.category].slice(0, 4)}</span>
                    {t.label}
                    <button type="button" aria-label={`Remove tag ${t.label}`} onClick={() => patch({ tagIds: session.tagIds.filter((x) => x !== t.id) })} className="ml-0.5 text-muted-foreground hover:text-foreground"><X className="size-3" /></button>
                  </motion.span>
                ))}
              </AnimatePresence>
              <Combobox value={null} options={state.tags.filter((t) => !session.tagIds.includes(t.id)).map((t) => ({ value: t.id, label: t.label, hint: TAG_CATEGORY_LABELS[t.category] }))} onChange={(v) => v && patch({ tagIds: [...session.tagIds, v] })} onCreate={(label) => patch({ tagIds: [...session.tagIds, state.createTag(label)] })} trigger={<Button variant="outline" size="xs" className="h-7 rounded-full">+ tag</Button>} />
            </div>
          </FieldRow>
        </Section>

        {/* Summary & notes */}
        <Section title="Summary & notes" subtitle="A short structured summary feeds reports; notes are for you.">
          <FieldRow id="summary" label="Summary" provenance={session.provenance.summary} pending={pending("summary")} onAccept={accept} onReject={reject}>
            <Textarea value={session.summary} onChange={(e) => patch({ summary: e.target.value })} placeholder="What happened, what was demoed, what's next…" rows={3} />
          </FieldRow>
          <FieldRow id="notes" label="Notes" provenance={session.provenance.notes}>
            <Textarea value={session.notes} onChange={(e) => patch({ notes: e.target.value })} placeholder="Free-form notes, quotes, blockers" rows={3} />
          </FieldRow>
        </Section>

        {/* Advanced */}
        <section className="mt-4">
          <button type="button" onClick={() => setAdvancedOpen((o) => !o)} className="flex w-full items-center gap-2 py-2 text-left text-xs font-medium text-muted-foreground hover:text-foreground" aria-expanded={advancedOpen}>
            <ChevronDown className={cn("size-4 transition-transform", advancedOpen && "rotate-180")} /> Advanced · sources, timing, audit
          </button>
          <AnimatePresence initial={false}>
            {advancedOpen && (
              <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} className="overflow-hidden">
                <div className="rounded-2xl border bg-card p-4 text-xs">
                  <dl className="grid grid-cols-[140px_1fr] gap-y-2">
                    <dt className="text-muted-foreground">Source events</dt>
                    <dd>{vm.sourceEvents.length ? vm.sourceEvents.map((e) => <div key={e.id} className="truncate">{e.title} <span className="text-muted-foreground">· {e.providerEventId}</span></div>) : "None (logged manually)"}</dd>
                    <dt className="text-muted-foreground">Actual start / end</dt>
                    <dd>{session.actualStart ? `${format(new Date(session.actualStart), "HH:mm")} – ${session.actualEnd ? format(new Date(session.actualEnd), "HH:mm") : "…"}` : "Not recorded"}</dd>
                    <dt className="text-muted-foreground">Created</dt>
                    <dd>{format(new Date(session.createdAt), "d MMM yyyy HH:mm")} by {state.members.find((m) => m.id === session.createdBy)?.name}</dd>
                    <dt className="text-muted-foreground">Last edit</dt>
                    <dd>{format(new Date(session.updatedAt), "d MMM yyyy HH:mm")} by {state.members.find((m) => m.id === session.updatedBy)?.name} · version {session.version}</dd>
                    <dt className="text-muted-foreground">Decisions</dt>
                    <dd>
                      {["accepted", "rejected", "superseded"].map((st) => {
                        const n = state.suggestions.filter((s) => s.sessionId === session.id && s.status === st).length;
                        return n ? <Badge key={st} variant="outline" className="mr-1 text-[10px]">{n} {st}</Badge> : null;
                      })}
                    </dd>
                  </dl>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </section>
      </div>

      <aside className="space-y-4 lg:sticky lg:top-20 lg:self-start">
        <EvidencePanel vm={vm} state={state} />
        <HistoryPanel vm={vm} state={state} />
        <div className="rounded-2xl border border-dashed p-4 text-[11px] text-muted-foreground">
          <div className="mb-1 flex items-center gap-1.5 font-medium text-foreground"><Sparkles className="size-3.5 text-ai" /> Three layers, never mixed</div>
          Calendar sync updates evidence. AI only proposes. You own the record — fields with a <span className="mx-0.5 inline-block size-1.5 rounded-full bg-foreground align-middle" /> were typed by hand and survive every re-sync.
        </div>
      </aside>
    </div>
  );
}

function Section({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <section className="mt-4 rounded-3xl border bg-card px-5 py-3">
      <div className="border-b pb-2 pt-1">
        <h2 className="text-sm font-semibold">{title}</h2>
        <p className="text-[11px] text-muted-foreground">{subtitle}</p>
      </div>
      <div className="divide-y divide-border/60">{children}</div>
    </section>
  );
}

function useSavedIndicator(version: number): "saved" | "saving" {
  const [state, setState] = useState<"saved" | "saving">("saved");
  const first = useRef(true);
  useEffect(() => {
    if (first.current) { first.current = false; return; }
    setState("saving");
    const t = setTimeout(() => setState("saved"), 500);
    return () => clearTimeout(t);
  }, [version]);
  return state;
}
