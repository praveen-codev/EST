import { differenceInCalendarDays, format, formatDistanceToNowStrict, isSameDay, isToday, isTomorrow, isYesterday } from "date-fns";

export function fmtTimeRange(start: string | Date, end: string | Date): string {
  const s = new Date(start);
  const e = new Date(end);
  return `${format(s, "HH:mm")}–${format(e, "HH:mm")}`;
}

/** Two-line day label for list columns: ["Fri", "2 Oct"] or ["Today", "11 Oct"]. */
export function fmtDayParts(d: string | Date): [string, string] {
  const date = new Date(d);
  const top = isToday(date) ? "Today" : isTomorrow(date) ? "Tomorrow" : isYesterday(date) ? "Yesterday" : format(date, "EEEE");
  return [top, format(date, "d MMM")];
}

export function fmtDayLabel(d: string | Date): string {
  const date = new Date(d);
  if (isToday(date)) return "Today";
  if (isTomorrow(date)) return "Tomorrow";
  if (isYesterday(date)) return "Yesterday";
  return format(date, "EEE d MMM");
}

export function fmtDayLong(d: string | Date): string {
  const date = new Date(d);
  const rel = isToday(date) ? "Today" : isTomorrow(date) ? "Tomorrow" : isYesterday(date) ? "Yesterday" : null;
  const full = format(date, "EEEE d MMMM");
  return rel ? `${rel} · ${full}` : full;
}

export function fmtRelativeDay(d: string | Date, now = new Date()): string {
  const date = new Date(d);
  if (isSameDay(date, now)) return "today";
  const diff = differenceInCalendarDays(date, now);
  if (diff === 1) return "tomorrow";
  if (diff === -1) return "yesterday";
  return diff > 0 ? `in ${diff} days` : `${-diff} days ago`;
}

export function fmtAgo(d: string | Date): string {
  return formatDistanceToNowStrict(new Date(d), { addSuffix: true });
}

export function durationMin(start: string | Date, end: string | Date): number {
  return Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60000);
}

export function fmtDuration(min: number): string {
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m ? `${h}h ${m}m` : `${h}h`;
}

export function pct(n: number): string {
  return `${Math.round(n * 100)}%`;
}
