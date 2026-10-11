"use client";

import { usePathname } from "next/navigation";
import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import { PersonaSwitcher } from "./persona-switcher";
import { SyncPill } from "./sync-pill";

const TITLES: Array<[string, string, string]> = [
  ["/inbox", "Capture inbox", "Review what your calendar found. Confirm in one click."],
  ["/timeline", "Timeline", "Every session across the team, grouped your way."],
  ["/calendar", "Calendar", "Candidates ghosted, confirmed sessions solid."],
  ["/sessions", "Session", "Prefilled from evidence. You decide what sticks."],
  ["/admin", "Admin · session formats", "The catalogue of enablement formats the team actually runs."],
];

export function Topbar({ onOpenPalette }: { onOpenPalette: () => void }) {
  const pathname = usePathname();
  const [, title, subtitle] = TITLES.find(([p]) => pathname.startsWith(p)) ?? ["", "Enablement Session Tracker", ""];
  return (
    <header className="glass sticky top-0 z-30 flex h-14 shrink-0 items-center gap-3 border-b border-border/60 px-5">
      <div className="min-w-0">
        <h1 className="truncate text-sm font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="hidden truncate text-[11px] text-muted-foreground md:block">{subtitle}</p>}
      </div>
      <div className="ml-auto flex items-center gap-2">
        <SyncPill />
        <Button variant="outline" size="sm" className="hidden gap-2 text-muted-foreground sm:inline-flex" onClick={onOpenPalette}>
          <Search data-icon="inline-start" />
          Search or jump to…
          <KbdGroup className="ml-2">
            <Kbd>⌘</Kbd>
            <Kbd>K</Kbd>
          </KbdGroup>
        </Button>
        <PersonaSwitcher />
      </div>
    </header>
  );
}
