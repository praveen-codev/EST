"use client";

import { cn } from "@/lib/utils";
import { initials } from "@/lib/domain/labels";
import type { Client, Member, Person } from "@/lib/domain/types";

/**
 * Per-entity colour derived from a hue. Chroma is kept low so marks read as rich
 * and muted on navy; light-dark() follows the active colour scheme.
 */
export function hueStyle(hue: number, strength: "soft" | "solid" = "soft"): React.CSSProperties {
  return strength === "solid"
    ? { backgroundColor: `light-dark(oklch(0.5 0.11 ${hue}), oklch(0.58 0.11 ${hue}))`, color: `light-dark(oklch(0.98 0 0), oklch(0.14 0.02 ${hue}))` }
    : { backgroundColor: `light-dark(oklch(0.93 0.035 ${hue}), oklch(0.31 0.05 ${hue}))`, color: `light-dark(oklch(0.34 0.1 ${hue}), oklch(0.9 0.06 ${hue}))` };
}

export function ClientMark({ client, size = "md", className }: { client: Client | null; size?: "sm" | "md" | "lg"; className?: string }) {
  const dims = size === "sm" ? "size-6 text-[10px] rounded-md" : size === "lg" ? "size-12 text-base rounded-xl" : "size-8 text-xs rounded-lg";
  if (!client) {
    return (
      <div className={cn("flex items-center justify-center border border-dashed border-border text-muted-foreground", dims, className)} aria-hidden>
        ?
      </div>
    );
  }
  return (
    <div className={cn("flex shrink-0 items-center justify-center font-semibold tracking-tight", dims, className)} style={hueStyle(client.hue)} aria-hidden>
      {initials(client.name)}
    </div>
  );
}

export function MemberAvatar({ member, size = "md", className, ring }: { member: Member; size?: "xs" | "sm" | "md" | "lg"; className?: string; ring?: boolean }) {
  const dims = size === "xs" ? "size-5 text-[9px]" : size === "sm" ? "size-6 text-[10px]" : size === "lg" ? "size-10 text-sm" : "size-8 text-xs";
  return (
    <div
      className={cn("flex shrink-0 items-center justify-center rounded-full font-semibold", dims, ring && "ring-2 ring-background", className)}
      style={hueStyle(member.hue, "solid")}
      title={member.name}
      aria-label={member.name}
    >
      {initials(member.name)}
    </div>
  );
}

export function PersonAvatar({ person, size = "sm", className }: { person: Pick<Person, "name"> & { hue?: number }; size?: "xs" | "sm" | "md"; className?: string }) {
  const dims = size === "xs" ? "size-5 text-[9px]" : size === "md" ? "size-8 text-xs" : "size-6 text-[10px]";
  const hue = person.hue ?? (person.name.charCodeAt(0) * 37) % 360;
  return (
    <div className={cn("flex shrink-0 items-center justify-center rounded-full font-medium ring-2 ring-background", dims, className)} style={hueStyle(hue)} title={person.name}>
      {initials(person.name)}
    </div>
  );
}

export function AttendeeStack({ names, max = 4, className }: { names: string[]; max?: number; className?: string }) {
  const shown = names.slice(0, max);
  const rest = names.length - shown.length;
  return (
    <div className={cn("flex items-center -space-x-1.5", className)}>
      {shown.map((n) => (
        <PersonAvatar key={n} person={{ name: n }} size="xs" />
      ))}
      {rest > 0 && (
        <div className="flex size-5 items-center justify-center rounded-full bg-muted text-[9px] font-medium text-muted-foreground ring-2 ring-background">+{rest}</div>
      )}
    </div>
  );
}
