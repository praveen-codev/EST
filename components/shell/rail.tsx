"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "motion/react";
import { BarChart3, CalendarDays, Inbox, ListTree, Settings2, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { useOpenCandidateCount } from "@/lib/domain/store";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DemoControls } from "./demo-controls";
import { ThemeToggle } from "./theme-toggle";

const NAV = [
  { href: "/inbox", label: "Inbox", icon: Inbox, key: "G I" },
  { href: "/timeline", label: "Timeline", icon: ListTree, key: "G T" },
  { href: "/calendar", label: "Calendar", icon: CalendarDays, key: "G C" },
] as const;

const SOON = [
  { label: "Reports", icon: BarChart3 },
  { label: "Admin", icon: Settings2 },
] as const;

export function Rail() {
  const pathname = usePathname();
  const inboxCount = useOpenCandidateCount();
  return (
    <nav className="flex w-[72px] shrink-0 flex-col items-center border-r border-border/60 bg-background/60 py-3" aria-label="Primary">
      <Link href="/inbox" className="ai-gradient mb-4 flex size-9 items-center justify-center rounded-xl text-white shadow-md shadow-ai/25" aria-label="Enablement Session Tracker">
        <Sparkles className="size-4.5" />
      </Link>
      <ul className="flex flex-col items-center gap-1">
        {NAV.map(({ href, label, icon: Icon, key }) => {
          const active = pathname.startsWith(href) || (href === "/timeline" && pathname.startsWith("/sessions"));
          const count = href === "/inbox" ? inboxCount : 0;
          return (
            <li key={href}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Link
                    href={href}
                    aria-current={active ? "page" : undefined}
                    className={cn(
                      "relative flex w-14 flex-col items-center gap-0.5 rounded-xl py-2 text-[10px] font-medium text-muted-foreground transition-colors hover:text-foreground",
                      active && "text-foreground",
                    )}
                  >
                    {active && <motion.span layoutId="rail-active" className="absolute inset-0 rounded-xl bg-foreground/[0.06] dark:bg-foreground/[0.08]" transition={{ type: "spring", stiffness: 500, damping: 40 }} />}
                    <span className="relative">
                      <Icon className="size-5" />
                      {count > 0 && (
                        <span className="absolute -right-2 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-ai px-1 text-[9px] font-semibold text-ai-foreground ring-2 ring-background">
                          {count}
                        </span>
                      )}
                    </span>
                    <span className="relative">{label}</span>
                  </Link>
                </TooltipTrigger>
                <TooltipContent side="right">
                  {label} <kbd className="ml-1 rounded bg-background/20 px-1 font-mono text-[10px]">{key}</kbd>
                </TooltipContent>
              </Tooltip>
            </li>
          );
        })}
        <li className="my-1 h-px w-8 bg-border" aria-hidden />
        {SOON.map(({ label, icon: Icon }) => (
          <li key={label}>
            <Tooltip>
              <TooltipTrigger asChild>
                <button type="button" disabled className="flex w-14 cursor-not-allowed flex-col items-center gap-0.5 rounded-xl py-2 text-[10px] font-medium text-muted-foreground/50">
                  <Icon className="size-5" />
                  <span>{label}</span>
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">{label} — next phase</TooltipContent>
            </Tooltip>
          </li>
        ))}
      </ul>
      <div className="mt-auto flex flex-col items-center gap-1">
        <DemoControls />
        <ThemeToggle />
      </div>
    </nav>
  );
}
