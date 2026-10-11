"use client";

import { Check, ChevronDown, LogOut, Settings, Users } from "lucide-react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MemberAvatar } from "@/components/common/marks";
import { useAuth } from "@/lib/api/auth";
import { useCurrentMember, useStore } from "@/lib/domain/store";

/** Profile menu: settings, sign out, and — in the local profile only — switching persona via mock sign-in. */
export function PersonaSwitcher() {
  const me = useCurrentMember();
  const router = useRouter();
  const members = useStore((s) => s.members);
  const devMode = useStore((s) => s.features.devMode);
  const reset = useStore((s) => s.reset);
  const devLogin = useAuth((s) => s.devLogin);
  const signOut = useAuth((s) => s.signOut);
  if (!me) return null;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2 pl-1.5" aria-label={`Signed in as ${me.name}. Account menu`}>
          <MemberAvatar member={me} size="sm" />
          <span className="hidden text-left leading-tight md:block">
            <span className="block text-xs font-medium">{me.name}</span>
            <span className="block text-[10px] text-muted-foreground">{me.title ?? me.email}</span>
          </span>
          <ChevronDown className="size-3.5 text-muted-foreground" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72">
        <DropdownMenuItem className="gap-2" onSelect={() => router.push("/settings/calendars")}>
          <Settings className="size-4" /> Linked calendars
        </DropdownMenuItem>
        <DropdownMenuItem className="gap-2" onSelect={() => { void signOut(); }}>
          <LogOut className="size-4" /> Sign out
        </DropdownMenuItem>
        {devMode && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuLabel className="flex items-center gap-2 text-xs font-normal text-muted-foreground">
              <Users className="size-3.5" /> Switch persona (mock sign-in, local only)
            </DropdownMenuLabel>
            {members.map((m) => (
              <DropdownMenuItem
                key={m.id}
                className="gap-3 py-2"
                onSelect={async () => {
                  if (m.id === me.id) return;
                  await devLogin(m.email);
                  reset();
                  toast(`Now viewing as ${m.name}`, { description: m.role === "MANAGER" ? "Managers see every session but have no capture inbox of their own." : "Their calendar, their inbox, everyone's history." });
                }}
              >
                <MemberAvatar member={m} size="md" />
                <span className="flex-1 leading-tight">
                  <span className="block text-sm font-medium">{m.name}</span>
                  <span className="block text-[11px] text-muted-foreground">
                    {m.title ?? m.email} · {m.role.toLowerCase()}
                  </span>
                </span>
                {m.id === me.id && <Check className="size-4 text-ai" />}
              </DropdownMenuItem>
            ))}
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
