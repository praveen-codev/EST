"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { useShortcuts } from "@/lib/hooks/use-shortcuts";
import { useAuth } from "@/lib/api/auth";
import { onTokenChange } from "@/lib/api/client";
import { useStore } from "@/lib/domain/store";
import { CommandPalette } from "./command-palette";
import { Rail } from "./rail";
import { ShortcutsDialog } from "./shortcuts-dialog";
import { Topbar } from "./topbar";
import { Splash } from "./splash";

const PUBLIC_PATHS = ["/sign-in", "/pending"];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));
  if (isPublic) return <>{children}</>;
  return <AuthenticatedShell>{children}</AuthenticatedShell>;
}

function AuthenticatedShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const status = useAuth((s) => s.status);
  const bootstrap = useAuth((s) => s.bootstrap);
  const hydrated = useStore((s) => s.hydrated);
  const load = useStore((s) => s.load);
  const reset = useStore((s) => s.reset);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);

  useEffect(() => {
    if (status === "unknown") void bootstrap();
  }, [status, bootstrap]);
  useEffect(() => {
    if (status === "signed-in" && !hydrated) void load();
    if (status === "signed-out" || status === "rejected") {
      reset();
      router.replace(`/sign-in?next=${encodeURIComponent(pathname)}`);
    }
    if (status === "pending") router.replace("/pending");
  }, [status, hydrated, load, reset, router, pathname]);
  // A lost token (refresh failed) sends the user back to sign-in.
  useEffect(() => {
    const off = onTokenChange((t) => { if (!t && useAuth.getState().status === "signed-in") useAuth.setState({ status: "signed-out", member: null }); });
    return () => { off(); };
  }, []);

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
      {/* The page segment always renders (Next validates instant navigation on it); the splash covers it until the model loads. */}
      {!hydrated && (
        <div className="absolute inset-0 z-50 bg-surface">
          <Splash />
        </div>
      )}
      <Rail />
      <div className="flex min-w-0 flex-1 flex-col" aria-busy={!hydrated}>
        <Topbar onOpenPalette={() => setPaletteOpen(true)} />
        <main key={pathname} className="scrollbar-thin min-h-0 flex-1 overflow-y-auto">
          {hydrated ? children : null}
        </main>
      </div>
      <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
      <ShortcutsDialog open={helpOpen} onOpenChange={setHelpOpen} />
    </div>
  );
}

/** First visit for a member whose calendars have never been synced: run the initial lookback automatically. */
function useInitialSync(hydrated: boolean) {
  const hasRun = useStore((s) => s.syncRuns.length > 0);
  const hasIdentity = useStore((s) => s.identities.some((i) => i.status === "ACTIVE"));
  const syncing = useStore((s) => s.syncing);
  const syncNow = useStore((s) => s.syncNow);
  const member = useStore((s) => s.members.find((m) => m.id === s.currentMemberId));
  const remaining = useStore((s) => s.features.remainingPages);
  const mock = useStore((s) => s.features.mockGoogle);
  useEffect(() => {
    if (!hydrated || hasRun || syncing || !member || !hasIdentity) return;
    if (mock && remaining === 0) return;
    const id = toast.loading(`Connecting Google Calendar for ${member.name.split(" ")[0]}…`, { description: "Initial lookback: 30 days back, 60 days ahead" });
    void syncNow().then((outcome) => {
      if (!outcome) return toast.dismiss(id);
      const seen = outcome.runs.reduce((n, r) => n + r.eventsSeen, 0);
      toast.success(`Synced ${seen} events`, {
        id,
        description: `${outcome.newCandidateIds.length} candidates queued for review. Nothing was confirmed automatically.`,
      });
    });
  }, [hydrated, hasRun, syncing, member, hasIdentity, mock, remaining, syncNow]);
}
