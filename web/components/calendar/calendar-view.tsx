"use client";

import { useMemo, useState } from "react";
import {
  addDays, addMonths, addWeeks, eachDayOfInterval, endOfMonth, endOfWeek, format, getHours, getMinutes,
  isSameDay, isSameMonth, isToday, startOfMonth, startOfWeek,
} from "date-fns";
import { AnimatePresence, motion } from "motion/react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { hueStyle } from "@/components/common/marks";
import { useHydrated, useStore } from "@/lib/domain/store";
import { allItems, type TimelineItem } from "@/lib/domain/timeline";
import { fmtTimeRange } from "@/lib/format";
import { cn } from "@/lib/utils";
import { SidePeek } from "@/components/timeline/side-peek";
import { useRouter } from "next/navigation";

type Mode = "month" | "week";
const DAY_START = 8;
const DAY_END = 19;
const HOUR_PX = 56;

export function CalendarView() {
  const router = useRouter();
  const hydrated = useHydrated();
  const state = useStore();
  const [mode, setMode] = useState<Mode>("month");
  const [cursor, setCursor] = useState(() => new Date());
  const [peek, setPeek] = useState<TimelineItem | null>(null);
  const items = useMemo(() => allItems(state, state.currentMemberId, true), [state]);

  const open = (it: TimelineItem) => (it.kind === "session" ? router.push(it.href!) : setPeek(it));
  const step = (n: number) => setCursor((c) => (mode === "month" ? addMonths(c, n) : addWeeks(c, n)));
  const title = mode === "month" ? format(cursor, "MMMM yyyy") : `${format(startOfWeek(cursor, { weekStartsOn: 1 }), "d MMM")} – ${format(endOfWeek(cursor, { weekStartsOn: 1 }), "d MMM yyyy")}`;

  if (!hydrated) return null;

  return (
    <div className="mx-auto flex h-full w-full max-w-7xl flex-col px-6 py-6">
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon-sm" onClick={() => step(-1)} aria-label="Previous"><ChevronLeft /></Button>
          <Button variant="outline" size="icon-sm" onClick={() => step(1)} aria-label="Next"><ChevronRight /></Button>
          <Button variant="ghost" size="sm" onClick={() => setCursor(new Date())}>Today</Button>
        </div>
        <h2 className="text-base font-semibold tracking-tight">{title}</h2>
        <div className="ml-auto flex items-center gap-4">
          <Legend />
          <ToggleGroup type="single" variant="outline" size="sm" value={mode} onValueChange={(v) => v && setMode(v as Mode)}>
            <ToggleGroupItem value="month" className="px-3 text-xs">Month</ToggleGroupItem>
            <ToggleGroupItem value="week" className="px-3 text-xs">Week</ToggleGroupItem>
          </ToggleGroup>
        </div>
      </div>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div key={`${mode}:${format(cursor, mode === "month" ? "yyyy-MM" : "yyyy-II")}`} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.18 }} className="min-h-0 flex-1">
          {mode === "month" ? <MonthGrid cursor={cursor} items={items} onOpen={open} onDay={(d) => { setCursor(d); setMode("week"); }} /> : <WeekGrid cursor={cursor} items={items} onOpen={open} />}
        </motion.div>
      </AnimatePresence>
      <SidePeek item={peek} onClose={() => setPeek(null)} />
    </div>
  );
}

function Legend() {
  return (
    <div className="hidden items-center gap-3 text-[11px] text-muted-foreground md:flex">
      <span className="flex items-center gap-1.5"><span className="h-2.5 w-4 rounded-sm" style={hueStyle(220, "solid")} /> confirmed</span>
      <span className="flex items-center gap-1.5"><span className="h-2.5 w-4 rounded-sm border border-dashed border-ai" /> candidate</span>
    </div>
  );
}

function MonthGrid({ cursor, items, onOpen, onDay }: { cursor: Date; items: TimelineItem[]; onOpen: (i: TimelineItem) => void; onDay: (d: Date) => void }) {
  const start = startOfWeek(startOfMonth(cursor), { weekStartsOn: 1 });
  const end = endOfWeek(endOfMonth(cursor), { weekStartsOn: 1 });
  const days = eachDayOfInterval({ start, end });
  return (
    <div className="grid h-full grid-cols-7 overflow-hidden rounded-2xl border bg-card" style={{ gridTemplateRows: `auto repeat(${days.length / 7}, minmax(104px, 1fr))` }}>
      {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => (
        <div key={d} className="border-b bg-muted/40 px-2 py-1.5 text-[11px] font-medium text-muted-foreground">{d}</div>
      ))}
      {days.map((day) => {
        const dayItems = items.filter((i) => isSameDay(new Date(i.start), day));
        const inMonth = isSameMonth(day, cursor);
        return (
          <div key={day.toISOString()} className={cn("group flex min-h-0 flex-col border-b border-r p-1.5 [&:nth-child(7n+7)]:border-r-0", !inMonth && "bg-muted/20")}>
            <button type="button" onClick={() => onDay(day)} className={cn("mb-1 flex size-6 items-center justify-center self-start rounded-full text-xs tabular-nums hover:bg-muted", !inMonth && "text-muted-foreground/60", isToday(day) && "bg-foreground font-semibold text-background hover:bg-foreground")} aria-label={format(day, "EEEE d MMMM")}>
              {format(day, "d")}
            </button>
            <div className="flex min-h-0 flex-col gap-1 overflow-hidden">
              {dayItems.slice(0, 3).map((it) => <Chip key={it.id} item={it} onOpen={() => onOpen(it)} />)}
              {dayItems.length > 3 && <button type="button" onClick={() => onDay(day)} className="text-left text-[10px] text-muted-foreground hover:text-foreground">+{dayItems.length - 3} more</button>}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function Chip({ item, onOpen }: { item: TimelineItem; onOpen: () => void }) {
  const ghost = item.kind === "candidate";
  const hue = item.client?.hue ?? 260;
  return (
    <button
      type="button"
      onClick={onOpen}
      title={`${item.title} · ${fmtTimeRange(item.start, item.end)}`}
      className={cn("flex w-full items-center gap-1 truncate rounded-md px-1.5 py-0.5 text-left text-[11px] leading-tight transition-transform hover:translate-x-0.5", ghost && "border border-dashed border-ai/60 bg-ai-soft/40 text-foreground/80")}
      style={ghost ? undefined : hueStyle(hue)}
    >
      <span className="shrink-0 tabular-nums opacity-70">{format(new Date(item.start), "HH:mm")}</span>
      <span className="truncate">{item.clientLabel ? `${item.clientLabel.split(" ")[0]} · ` : ""}{item.title}</span>
    </button>
  );
}

function WeekGrid({ cursor, items, onOpen }: { cursor: Date; items: TimelineItem[]; onOpen: (i: TimelineItem) => void }) {
  const start = startOfWeek(cursor, { weekStartsOn: 1 });
  const days = Array.from({ length: 7 }, (_, i) => addDays(start, i));
  const hours = Array.from({ length: DAY_END - DAY_START + 1 }, (_, i) => DAY_START + i);
  return (
    <div className="scrollbar-thin h-full overflow-auto rounded-2xl border bg-card">
      <div className="grid min-w-[840px] grid-cols-[56px_repeat(7,1fr)]">
        <div className="sticky top-0 z-10 border-b bg-card" />
        {days.map((d) => (
          <div key={d.toISOString()} className="sticky top-0 z-10 border-b border-l bg-card px-2 py-2 text-xs">
            <span className="text-muted-foreground">{format(d, "EEE")}</span>{" "}
            <span className={cn("inline-flex size-6 items-center justify-center rounded-full tabular-nums", isToday(d) && "bg-foreground font-semibold text-background")}>{format(d, "d")}</span>
          </div>
        ))}
        <div className="relative">
          {hours.map((h) => (
            <div key={h} className="pr-2 text-right text-[10px] tabular-nums text-muted-foreground" style={{ height: HOUR_PX }}>
              <span className="relative -top-1.5">{String(h).padStart(2, "0")}:00</span>
            </div>
          ))}
        </div>
        {days.map((d) => {
          const dayItems = items.filter((i) => isSameDay(new Date(i.start), d));
          return (
            <div key={d.toISOString()} className="relative border-l" style={{ height: HOUR_PX * (hours.length) }}>
              {hours.map((h) => <div key={h} className="border-b border-border/50" style={{ height: HOUR_PX }} />)}
              {dayItems.map((it) => {
                const s = new Date(it.start);
                const e = new Date(it.end);
                const top = ((getHours(s) + getMinutes(s) / 60) - DAY_START) * HOUR_PX;
                const height = Math.max(22, ((e.getTime() - s.getTime()) / 3_600_000) * HOUR_PX - 2);
                const ghost = it.kind === "candidate";
                return (
                  <button
                    key={it.id}
                    type="button"
                    onClick={() => onOpen(it)}
                    className={cn("absolute inset-x-1 overflow-hidden rounded-lg px-2 py-1 text-left text-[11px] leading-tight shadow-sm transition-transform hover:z-10 hover:scale-[1.02]", ghost && "border border-dashed border-ai/70 bg-ai-soft/50 text-foreground/80")}
                    style={{ top, height, ...(ghost ? {} : hueStyle(it.client?.hue ?? 260)) }}
                    title={it.title}
                  >
                    <div className="truncate font-medium">{it.title}</div>
                    <div className="truncate opacity-70">{fmtTimeRange(it.start, it.end)}{it.clientLabel ? ` · ${it.clientLabel}` : ""}</div>
                  </button>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
