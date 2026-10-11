"use client";

import { useRouter } from "next/navigation";
import { CalendarDays, CheckCheck, FlaskConical, Inbox, ListTree, RefreshCw, Users } from "lucide-react";
import { toast } from "sonner";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from "@/components/ui/command";
import { ClientMark, MemberAvatar } from "@/components/common/marks";
import { useStore } from "@/lib/domain/store";
import { inboxGroups } from "@/lib/domain/selectors";
import { fmtDayLabel } from "@/lib/format";

export function CommandPalette({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const router = useRouter();
  const state = useStore();
  const groups = inboxGroups(state, state.currentMemberId);
  const recentSessions = [...state.sessions]
    .filter((s) => s.state !== "ignored")
    .sort((a, b) => Date.parse(b.scheduledStart) - Date.parse(a.scheduledStart))
    .slice(0, 8);

  const go = (href: string) => {
    onOpenChange(false);
    router.push(href);
  };

  return (
    <CommandDialog open={open} onOpenChange={onOpenChange} title="Jump to" description="Navigate, act, or switch persona" className="sm:max-w-xl">
      <CommandInput placeholder="Search sessions, clients, actions…" />
      <CommandList>
        <CommandEmpty>Nothing matches.</CommandEmpty>
        <CommandGroup heading="Actions">
          {groups.likely.length > 0 && (
            <CommandItem
              onSelect={() => {
                state.confirmMany(groups.likely.map((g) => g.candidate.id));
                onOpenChange(false);
                toast.success(`Confirmed ${groups.likely.length} sessions`, { description: "Prefilled fields recorded as accepted decisions." });
              }}
            >
              <CheckCheck /> Confirm all likely sessions ({groups.likely.length})
            </CommandItem>
          )}
          <CommandItem
            onSelect={() => {
              onOpenChange(false);
              void state.syncNow();
            }}
          >
            <RefreshCw /> Sync calendar now
          </CommandItem>
          <CommandItem
            onSelect={() => {
              state.resetDemo();
              onOpenChange(false);
              toast.success("Demo reset");
            }}
          >
            <FlaskConical /> Reset demo data
          </CommandItem>
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Go to">
          <CommandItem onSelect={() => go("/inbox")}>
            <Inbox /> Inbox <CommandShortcut>G I</CommandShortcut>
          </CommandItem>
          <CommandItem onSelect={() => go("/timeline")}>
            <ListTree /> Timeline <CommandShortcut>G T</CommandShortcut>
          </CommandItem>
          <CommandItem onSelect={() => go("/calendar")}>
            <CalendarDays /> Calendar <CommandShortcut>G C</CommandShortcut>
          </CommandItem>
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Sessions">
          {recentSessions.map((s) => {
            const client = state.clients.find((c) => c.id === s.clientId) ?? null;
            return (
              <CommandItem key={s.id} value={`${s.title} ${client?.name ?? ""}`} onSelect={() => go(`/sessions/${s.id}`)}>
                <ClientMark client={client} size="sm" />
                <span className="truncate">{s.title}</span>
                <span className="ml-auto text-xs text-muted-foreground">{fmtDayLabel(s.scheduledStart)}</span>
              </CommandItem>
            );
          })}
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Clients">
          {state.clients.map((c) => (
            <CommandItem key={c.id} value={`client ${c.name}`} onSelect={() => go(`/timeline?client=${c.id}`)}>
              <ClientMark client={c} size="sm" /> {c.name}
              <span className="ml-auto text-xs text-muted-foreground">{state.sessions.filter((s) => s.clientId === c.id).length} sessions</span>
            </CommandItem>
          ))}
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Switch persona">
          {state.members.map((m) => (
            <CommandItem
              key={m.id}
              value={`persona ${m.name}`}
              onSelect={() => {
                state.switchMember(m.id);
                onOpenChange(false);
                toast(`Now viewing as ${m.name}`);
              }}
            >
              <MemberAvatar member={m} size="sm" /> {m.name}
              <span className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
                <Users className="size-3" /> {m.role.toLowerCase()}
              </span>
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
}
