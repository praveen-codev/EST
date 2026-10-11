"use client";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Kbd, KbdGroup } from "@/components/ui/kbd";

const GROUPS: Array<[string, Array<[string[], string]>]> = [
  ["Everywhere", [[["⌘", "K"], "Search or jump to anything"], [["G", "I"], "Inbox"], [["G", "T"], "Timeline"], [["G", "C"], "Calendar"], [["?"], "This help"]]],
  ["Inbox", [[["J"], "Next card"], [["K"], "Previous card"], [["Enter"], "Confirm highlighted card"], [["X"], "Dismiss highlighted card"], [["E"], "Open and edit before confirming"], [["⇧", "Enter"], "Confirm all likely"]]],
  ["Session", [[["Tab"], "Move between suggestions"], [["Enter"], "Accept focused suggestion"], [["Backspace"], "Reject focused suggestion"], [["⌘", "⇧", "A"], "Accept all remaining"]]],
];

export function ShortcutsDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Keyboard shortcuts</DialogTitle>
          <DialogDescription>Designed so a full week of sessions takes under a minute to capture.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-5 sm:grid-cols-2">
          {GROUPS.map(([title, rows]) => (
            <div key={title} className={title === "Everywhere" ? "sm:col-span-2" : undefined}>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</div>
              <ul className="space-y-1.5">
                {rows.map(([keys, label]) => (
                  <li key={label} className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-muted-foreground">{label}</span>
                    <KbdGroup>
                      {keys.map((k) => (
                        <Kbd key={k}>{k}</Kbd>
                      ))}
                    </KbdGroup>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
