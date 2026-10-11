"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { format } from "date-fns";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, Check, CheckCheck, ChevronDown, CircleAlert, Loader2, Plus, RefreshCw, Sparkles, X } from "lucide-react";
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
import { catalogOf, CONTACT_ROLE_LABELS, CONTACT_ROLES, SESSION_STATE_LABELS } from "@/lib/domain/labels";
import { useHydrated, useStore } from "@/lib/domain/store";
import { sessionVM } from "@/lib/domain/selectors";
import type { ContactRole, SessionState, Suggestion, UseCase } from "@/lib/domain/types";
import { durationMin, fmtDayLong, fmtDuration, fmtTimeRange } from "@/lib/format";
import { useShortcuts } from "@/lib/hooks/use-shortcuts";
import { cn } from "@/lib/utils";
import { FieldRow, SuggestionChips } from "./field-row";
import { EvidencePanel, HistoryPanel } from "./side-panels";
import { UseCaseCard } from "./use-case-card";

export function SessionWorkspace() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const hydrated = useHydrated();
  const state = useStore();
  const session = state.sessions.find((s) => s.id === id) ?? null;
  const vm = useMemo(() => (session ? sessionVM(state, session) : null), [state, session]);
  const saved = useSavedIndicator(session?.version ?? 0);
  const [moreOpen, setMoreOpen] = useState(false);
  const [focusUseCaseId, setFocusUseCaseId] = useState<string | null>(null);

  const acceptAll = () => {
    if (!session || !vm) return;
    const n = vm.pending.filter((s) => s.confidence >= 0.5).length;
    if (!n) return;
    state.acceptAllForSession(session.id);
    toast.success(`Accepted ${n} suggestion${n === 1 ? "" : "s"}`, { description: "Each one is recorded as your decision with its evidence." });
  };

  useShortcuts(
    {
      "mod+shift+a": acceptAll,
      Escape: () => router.push("/"),
    },
    [session?.id, vm?.pending.length],
  );

  if (!hydrated) return null;

  if (!session || !vm) {
    return (
      <div className="mx-auto max-w-xl px-6 py-20 text-center">
        <h2 className="text-lg font-semibold">Session not found</h2>
        <p className="mt-1 text-sm text-muted-foreground">It may belong to a different scenario, or the demo was reset.</p>
        <Button asChild className="mt-4"><Link href="/">Back to today</Link></Button>
      </div>
    );
  }

  const patch = (p: Parameters<typeof state.updateSession>[1]) => state.updateSession(session.id, p);
  const accept = (s: Suggestion) => state.acceptSuggestion(s.id);
  const reject = (s: Suggestion) => state.rejectSuggestion(s.id);
  const pending = (field: Suggestion["field"]) => vm.pendingByField.get(field) ?? [];
  const rerun = () => {
    state.rerunAI(session.id);
    toast("AI re-run complete", { description: "Only new, non-conflicting suggestions were added. Nothing you set was touched." });
  };
  const focusField = (field: string) => document.getElementById(`field-${field}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
  const addUseCase = () => {
    const nextIndex = session.useCases.length;
    state.addUseCase(session.id);
    // The new card's id is derived in the store; focus the last card after render.
    requestAnimationFrame(() => {
      const latest = useStore.getState().sessions.find((s) => s.id === session.id)?.useCases[nextIndex];
      if (latest) setFocusUseCaseId(latest.id);
    });
  };

  const clientPeople = state.people.filter((p) => !vm.client || p.clientId === vm.client.id || p.clientId === null);
  const teams = state.teams.filter((t) => t.clientId === session.clientId);
  const members = state.members.filter((m) => m.role !== "MANAGER");
  const formats = catalogOf(state.catalog, "sessionType", true).filter((t) => !t.archived || t.id === session.sessionType);
  const outcomes = catalogOf(state.catalog, "sessionOutcome", true).filter((t) => !t.archived || t.id === session.outcomeId);
  const technologies = catalogOf(state.catalog, "technology");
  const useCaseTypes = catalogOf(state.catalog, "useCaseType");
  const highConfidencePending = vm.pending.filter((s) => s.confidence >= 0.5).length;
  const useCaseDrafts = pending("useCases");

  return (
    <div className="mx-auto grid w-full max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div className="min-w-0">
        <div className="mb-4 flex items-center justify-between text-xs text-muted-foreground">
          <Link href="/" className="inline-flex items-center gap-1 hover:text-foreground"><ArrowLeft className="size-3.5" /> Today</Link>
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
              <input value={session.title} onChange={(e) => patch({ title: e.target.value })} aria-label="Session title" className="w-full bg-transparent text-xl font-semibold tracking-tight outline-none placeholder:text-muted-foreground" placeholder="Untitled session" />
              <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                <span>{fmtDayLong(session.scheduledStart)}</span>
                <span>·</span>
                <span className="tabular-nums">{fmtTimeRange(session.scheduledStart, session.scheduledEnd)} ({fmtDuration(durationMin(session.scheduledStart, session.scheduledEnd))})</span>
                {vm.client && <><span>·</span><span>{vm.client.name}{vm.team ? ` / ${vm.team.name}` : ""}</span></>}
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Select value={session.state} onValueChange={(v) => patch({ state: v as SessionState })}>
                  <SelectTrigger size="sm" className="h-7 w-auto gap-1.5 rounded-full text-xs" aria-label="Lifecycle state"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {(Object.keys(SESSION_STATE_LABELS) as SessionState[]).filter((s) => s !== "candidate").map((s) => <SelectItem key={s} value={s}>{SESSION_STATE_LABELS[s]}</SelectItem>)}
                  </SelectContent>
                </Select>
                {vm.facilitator && <span className="inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs"><MemberAvatar member={vm.facilitator} size="xs" /> {vm.facilitator.name}</span>}
                {vm.pending.length > 0 && <AiBadge>{vm.pending.length} suggestion{vm.pending.length === 1 ? "" : "s"} waiting</AiBadge>}
              </div>
            </div>
            <div className="flex flex-col items-end gap-2">
              <ConfidenceRing value={vm.completeness} size={44} label={`${Math.round(vm.completeness * 100)}% complete`} />
              <div className="flex gap-1">
                <Button size="sm" variant="ghost" onClick={rerun} className="gap-1.5 text-muted-foreground"><RefreshCw data-icon="inline-start" /> Re-run AI</Button>
                {highConfidencePending > 0 && (
                  <Button size="sm" onClick={acceptAll} className="gap-1.5 bg-ai text-ai-foreground hover:bg-ai/90"><CheckCheck data-icon="inline-start" /> Accept all ({highConfidencePending})</Button>
                )}
              </div>
            </div>
          </div>
          {vm.missing.length > 0 && (
            <div className="mt-4 flex flex-wrap items-center gap-1.5 border-t pt-3 text-xs">
              <CircleAlert className="size-3.5 text-warning" />
              <span className="text-muted-foreground">Still missing:</span>
              {vm.missing.map((m) => (
                <button key={m.field} type="button" onClick={() => focusField(m.field)} className="rounded-full border border-dashed px-2 py-0.5 hover:border-foreground/40 hover:bg-muted">{m.label}</button>
              ))}
            </div>
          )}
        </header>

        {/* 1 · Basics */}
        <Section step={1} title="Confirm the basics" subtitle="Prefilled from the invite and from teammates' sessions with this client. Approve or change.">
          <FieldRow id="clientId" label="Client" provenance={session.provenance.clientId} pending={pending("clientId")} onAccept={accept} onReject={reject}>
            <Combobox value={session.clientId} options={state.clients.map((c) => ({ value: c.id, label: c.name, hint: c.industry ?? undefined, icon: <ClientMark client={c} size="sm" /> }))} placeholder="Choose a client" onChange={(v) => patch({ clientId: v, teamId: null })} onCreate={(name) => patch({ clientId: state.createClient(name), teamId: null })} clearable />
          </FieldRow>
          <FieldRow id="teamId" label="Team" provenance={session.provenance.teamId} pending={pending("teamId")} onAccept={accept} onReject={reject}>
            <Combobox value={session.teamId} options={teams.map((t) => ({ value: t.id, label: t.name }))} placeholder={session.clientId ? "Choose a team" : "Pick a client first"} onChange={(v) => patch({ teamId: v })} clearable emptyText="No teams recorded for this client yet" />
          </FieldRow>
          <FieldRow id="sessionType" label="Format" provenance={session.provenance.sessionType} pending={pending("sessionType")} onAccept={accept} onReject={reject}>
            <ToggleGroup type="single" variant="outline" size="sm" value={session.sessionType ?? ""} onValueChange={(v) => v && patch({ sessionType: v })} className="flex-wrap">
              {formats.map((t) => <ToggleGroupItem key={t.id} value={t.id} className="text-xs">{t.label}{t.archived ? " (archived)" : ""}</ToggleGroupItem>)}
            </ToggleGroup>
          </FieldRow>
          <FieldRow id="facilitatorId" label="Facilitator" provenance={session.provenance.facilitatorId} pending={pending("facilitatorId")} onAccept={accept} onReject={reject}>
            <div className="flex flex-wrap items-center gap-2">
              <Combobox value={session.facilitatorId} options={members.map((m) => ({ value: m.id, label: m.name, hint: m.title, icon: <MemberAvatar member={m} size="xs" /> }))} onChange={(v) => patch({ facilitatorId: v })} placeholder="Choose a facilitator" />
              {vm.coFacilitators.map((m) => (
                <span key={m.id} className="inline-flex items-center gap-1 rounded-full bg-muted py-0.5 pl-0.5 pr-2 text-xs">
                  <MemberAvatar member={m} size="xs" /> {m.name}
                  <button type="button" aria-label={`Remove ${m.name}`} onClick={() => patch({ coFacilitatorIds: session.coFacilitatorIds.filter((x) => x !== m.id) })} className="ml-0.5 text-muted-foreground hover:text-foreground"><X className="size-3" /></button>
                </span>
              ))}
              <Combobox value={null} options={members.filter((m) => m.id !== session.facilitatorId && !session.coFacilitatorIds.includes(m.id)).map((m) => ({ value: m.id, label: m.name, icon: <MemberAvatar member={m} size="xs" /> }))} onChange={(v) => v && patch({ coFacilitatorIds: [...session.coFacilitatorIds, v] })} trigger={<Button variant="outline" size="xs" className="rounded-full">+ co-facilitator</Button>} />
            </div>
            <div className="mt-2"><SuggestionChips pending={pending("coFacilitatorIds")} onAccept={accept} onReject={reject} label="Suggested co-facilitators" /></div>
          </FieldRow>
          <FieldRow id="contacts" label="Client contacts" provenance={session.provenance.contacts} pending={pending("contacts")} onAccept={accept} onReject={reject} hint="Roles are remembered per client and suggested to teammates next time.">
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
                onChange={(v) => { if (!v) return; const person = state.people.find((p) => p.id === v); patch({ contacts: [...session.contacts, { personId: v, role: person?.role ?? "engineer" }] }); }}
                onCreate={(name) => patch({ contacts: [...session.contacts, { personId: state.createPerson(name, session.clientId), role: "engineer" }] })}
                trigger={<Button variant="outline" size="xs" className="rounded-full">+ contact</Button>}
              />
            </div>
          </FieldRow>
        </Section>

        {/* 2 · Participants */}
        <Section step={2} title="Participants" subtitle="How many people took part. Known names come from the invite.">
          <FieldRow id="participantIds" label="Number of participants" provenance={session.provenance.participantsEstimated ?? session.provenance.participantIds} pending={[...pending("participantsEstimated"), ...pending("participantIds")]} onAccept={accept} onReject={reject}>
            <div className="flex flex-wrap items-center gap-3">
              <Input type="number" min={0} value={session.participantsEstimated ?? ""} onChange={(e) => patch({ participantsEstimated: e.target.value === "" ? null : Number(e.target.value) })} className="h-10 w-24 text-lg font-semibold tabular-nums" aria-label="Number of participants" placeholder="0" />
              <span className="text-xs text-muted-foreground">people in the room</span>
              <div className="flex flex-wrap gap-1">
                {vm.participants.map((p) => (
                  <span key={p.id} className="inline-flex items-center gap-1 rounded-full bg-muted py-0.5 pl-0.5 pr-2 text-xs">
                    <PersonAvatar person={p} size="xs" /> {p.name}
                    <button type="button" aria-label={`Remove ${p.name}`} onClick={() => patch({ participantIds: session.participantIds.filter((x) => x !== p.id) })} className="ml-0.5 text-muted-foreground hover:text-foreground"><X className="size-3" /></button>
                  </span>
                ))}
                <Combobox value={null} options={clientPeople.filter((p) => !session.participantIds.includes(p.id)).map((p) => ({ value: p.id, label: p.name }))} onChange={(v) => v && patch({ participantIds: [...session.participantIds, v] })} onCreate={(name) => patch({ participantIds: [...session.participantIds, state.createPerson(name, session.clientId)] })} trigger={<Button variant="outline" size="xs" className="rounded-full">+ known person</Button>} />
              </div>
            </div>
          </FieldRow>
        </Section>

        {/* 3 · Use cases */}
        <Section step={3} title="Use cases" subtitle="One card per thing the team worked on. Each gets a type and the technologies involved." id="field-useCases">
          <FieldRow id="technologyIds" label="Technologies in play" provenance={session.provenance.technologyIds} pending={pending("technologyIds")} onAccept={accept} onReject={reject} hint="Suggested from the invite and from teammates' sessions. Use cases pick from these first.">
            <div className="flex flex-wrap gap-1">
              <AnimatePresence initial={false}>
                {vm.technologies.map((t) => (
                  <motion.span key={t.id} layout initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.9 }} className="inline-flex h-7 items-center gap-1 rounded-full border bg-background px-2.5 text-xs">
                    {t.label}
                    <button type="button" aria-label={`Remove ${t.label}`} onClick={() => patch({ technologyIds: session.technologyIds.filter((x) => x !== t.id) })} className="ml-0.5 text-muted-foreground hover:text-foreground"><X className="size-3" /></button>
                  </motion.span>
                ))}
              </AnimatePresence>
              <Combobox value={null} options={technologies.filter((t) => !session.technologyIds.includes(t.id)).map((t) => ({ value: t.id, label: t.label }))} onChange={(v) => v && patch({ technologyIds: [...session.technologyIds, v] })} searchPlaceholder="Search technologies…" trigger={<Button variant="outline" size="xs" className="h-7 rounded-full">+ technology</Button>} />
            </div>
          </FieldRow>

          <div className="py-3">
            {useCaseDrafts.length > 0 && (
              <div className="mb-3 rounded-xl border border-dashed border-ai/40 bg-ai-soft/30 p-3">
                <div className="mb-2 flex items-center gap-1.5 text-xs font-medium"><Sparkles className="size-3.5 text-ai" /> Suggested from the invite</div>
                <SuggestionChips pending={useCaseDrafts} onAccept={accept} onReject={reject} label="Suggested use cases" />
              </div>
            )}
            <div className="space-y-3">
              <AnimatePresence initial={false}>
                {session.useCases.map((uc: UseCase, i: number) => (
                  <UseCaseCard
                    key={uc.id}
                    index={i}
                    useCase={uc}
                    useCaseTypes={useCaseTypes}
                    technologies={technologies}
                    quickTechnologyIds={session.technologyIds}
                    autoFocus={focusUseCaseId === uc.id}
                    onChange={(p) => state.updateUseCase(session.id, uc.id, p)}
                    onRemove={() => state.removeUseCase(session.id, uc.id)}
                  />
                ))}
              </AnimatePresence>
            </div>
            <Button variant="outline" onClick={addUseCase} className="mt-3 w-full gap-2 border-dashed">
              <Plus data-icon="inline-start" /> Add use case
            </Button>
          </div>
        </Section>

        {/* 4 · Outcome */}
        <Section step={4} title="Session outcome" subtitle="What the session produced. Configured by your admin.">
          <FieldRow id="outcomeId" label="Outcome" provenance={session.provenance.outcomeId}>
            <ToggleGroup type="single" variant="outline" size="sm" value={session.outcomeId ?? ""} onValueChange={(v) => v && patch({ outcomeId: v })} className="flex-wrap">
              {outcomes.map((o) => (
                <ToggleGroupItem key={o.id} value={o.id} className="h-9 px-3 text-xs data-[state=on]:bg-success/15 data-[state=on]:text-success" title={o.description}>{o.label}</ToggleGroupItem>
              ))}
            </ToggleGroup>
          </FieldRow>
        </Section>

        {/* More */}
        <section className="mt-4">
          <button type="button" onClick={() => setMoreOpen((o) => !o)} className="flex w-full items-center gap-2 py-2 text-left text-xs font-medium text-muted-foreground hover:text-foreground" aria-expanded={moreOpen}>
            <ChevronDown className={cn("size-4 transition-transform", moreOpen && "rotate-180")} /> Summary, notes, sources &amp; audit
          </button>
          <AnimatePresence initial={false}>
            {moreOpen && (
              <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} className="overflow-hidden">
                <div className="rounded-2xl border bg-card px-5 py-2">
                  <FieldRow id="summary" label="Summary" provenance={session.provenance.summary} pending={pending("summary")} onAccept={accept} onReject={reject}>
                    <Textarea value={session.summary} onChange={(e) => patch({ summary: e.target.value })} placeholder="Short structured summary for reports" rows={3} />
                  </FieldRow>
                  <FieldRow id="notes" label="Notes" provenance={session.provenance.notes}>
                    <Textarea value={session.notes} onChange={(e) => patch({ notes: e.target.value })} placeholder="Free-form notes, quotes, blockers" rows={3} />
                  </FieldRow>
                  <dl className="grid grid-cols-[140px_1fr] gap-y-2 border-t py-3 text-xs">
                    <dt className="text-muted-foreground">Source events</dt>
                    <dd>{vm.sourceEvents.length ? vm.sourceEvents.map((e) => <div key={e.id} className="truncate">{e.title} <span className="text-muted-foreground">· {e.providerEventId}</span></div>) : "None (logged manually)"}</dd>
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
          Calendar sync updates evidence. AI only proposes. You own the record — fields with a <span className="mx-0.5 inline-block size-1.5 rounded-full bg-foreground align-middle" /> were set by hand and survive every re-sync.
        </div>
      </aside>
    </div>
  );
}

function Section({ step, title, subtitle, children, id }: { step: number; title: string; subtitle: string; children: React.ReactNode; id?: string }) {
  return (
    <section id={id} className="mt-4 scroll-mt-24 rounded-3xl border bg-card px-5 py-3">
      <div className="flex items-start gap-3 border-b pb-2 pt-1">
        <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-foreground text-[10px] font-semibold text-background">{step}</span>
        <div>
          <h2 className="text-sm font-semibold">{title}</h2>
          <p className="text-[11px] text-muted-foreground">{subtitle}</p>
        </div>
      </div>
      <div className="divide-y divide-border/60">{children}</div>
    </section>
  );
}

function useSavedIndicator(version: number): "saved" | "saving" {
  const [saving, setSaving] = useState(false);
  const first = useRef(true);
  useEffect(() => {
    if (first.current) { first.current = false; return; }
    const t1 = setTimeout(() => setSaving(true), 0);
    const t2 = setTimeout(() => setSaving(false), 500);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [version]);
  return saving ? "saving" : "saved";
}
