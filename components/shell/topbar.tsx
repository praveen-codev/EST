"use client";

import { usePathname } from "next/navigation";
import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import { PersonaSwitcher } from "./persona-switcher";
import { SyncPill } from "./sync-pill";
import { ThemeToggle } from "./theme-toggle";

const TITLES: Array<[string, string, string]> = [
  ["/inbox", "Capture inbox", "Review what your calendar found. Confirm in one click."],
  ["/sessions", "Session feedback", "Prefilled from the invite and your teammates. Approve, adjust, done."],
  ["/timeline", "Timeline", "Every session across the team, grouped your way."],
  ["/calendar", "Calendar", "Candidates ghosted, confirmed sessions solid."],
  ["/admin", "Admin · reference tables", "Session formats, outcomes, use-case types and technologies."],
];

export function Topbar({ onOpenPalette }: { onOpenPalette: () => void }) {
  const pathname = usePathname();
  const [, title, subtitle] = TITLES.find(([p]) => pathname.startsWith(p)) ?? ["", "Today", "Your sessions for today, ready for feedback."];
  return (
    <header className="glass sticky top-0 z-30 flex h-14 shrink-0 items-center gap-3 border-b border-border/60 px-5">
      <div className="min-w-0 flex-1">
        <h1 className="truncate text-sm font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="hidden truncate text-[11px] text-muted-foreground md:block">{subtitle}</p>}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <SyncPill />
        <Button variant="outline" size="sm" className="hidden gap-2 text-muted-foreground md:inline-flex" onClick={onOpenPalette}>
          <Search data-icon="inline-start" />
          Search or jump to…
          <KbdGroup className="ml-2">
            <Kbd>⌘</Kbd>
            <Kbd>K</Kbd>
          </KbdGroup>
        </Button>
        <PersonaSwitcher />
        <ThemeToggle />
      </div>
    </header>
  );
}
