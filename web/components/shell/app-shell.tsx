"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { useShortcuts } from "@/lib/hooks/use-shortcuts";
import { useStore } from "@/lib/domain/store";
import { remainingPages } from "@/lib/mock/calendar";
import { CommandPalette } from "./command-palette";
import { Rail } from "./rail";
import { ShortcutsDialog } from "./shortcuts-dialog";
import { Topbar } from "./topbar";
import { Splash } from "./splash";

export function AppShell({ children }: { children: React.ReactNode }) {
  const hydrated = useStore((s) => s.hydrated);
  const router = useRouter();
  const pathname = usePathname();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);

  useInitialSync(hydrated);

  useShortcuts(
    {
      "mod+k": () => setPaletteOpen((o) => !o),
      "g h": () => router.push("/"),
      "g i": () => router.push("/inbox"),
      "g t": () => router.push("/timeline"),
      "g c": () => router.push("/calendar"),
      "g a": () => router.push("/admin"),
      "?": () => setHelpOpen(true),
    },
    [router],
  );

  return (
    <div className="relative flex h-dvh w-full overflow-hidden">
      {/* The page segment always renders (Next validates instant navigation on it); the splash covers it until the store hydrates. */}
      {!hydrated && (
        <div className="absolute inset-0 z-50 bg-surface">
          <Splash />
        </div>
      )}
      <Rail />
      <div className="flex min-w-0 flex-1 flex-col" aria-busy={!hydrated}>
        <Topbar onOpenPalette={() => setPaletteOpen(true)} />
        <main key={pathname} className="scrollbar-thin min-h-0 flex-1 overflow-y-auto">
          {children}
        </main>
      </div>
      <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
      <ShortcutsDialog open={helpOpen} onOpenChange={setHelpOpen} />
    </div>
  );
}

/** First visit for a persona: run the initial bounded lookback automatically. */
function useInitialSync(hydrated: boolean) {
  const currentMemberId = useStore((s) => s.currentMemberId);
  const scenario = useStore((s) => s.settings.scenario);
  const hasRun = useStore((s) => s.syncRuns.some((r) => r.memberId === s.currentMemberId));
  const syncing = useStore((s) => s.syncing);
  const syncNow = useStore((s) => s.syncNow);
  const member = useStore((s) => s.members.find((m) => m.id === s.currentMemberId));
  useEffect(() => {
    if (!hydrated || hasRun || syncing || !member) return;
    if (remainingPages(member.email, scenario, 0) === 0) return;
    const id = toast.loading(`Connecting Google Calendar for ${member.name.split(" ")[0]}…`, { description: "Initial lookback: 2 weeks back, 3 weeks ahead" });
    void syncNow().then((outcome) => {
      if (!outcome) return toast.dismiss(id);
      toast.success(`Synced ${outcome.run.eventsSeen} events`, {
        id,
        description: `${outcome.newCandidateIds.length} candidates queued for review. Nothing was confirmed automatically.`,
      });
    });
  }, [hydrated, hasRun, syncing, member, scenario, syncNow, currentMemberId]);
}
