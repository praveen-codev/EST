"use client";

import { useEffect, useRef } from "react";

export type ShortcutHandler = (e: KeyboardEvent) => void;

function isTyping(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  if (!el) return false;
  const tag = el.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || el.isContentEditable || !!el.closest("[cmdk-root]");
}

/**
 * Registers keyboard shortcuts. Keys are "k", "mod+k", "g i" (sequence), "?", "Enter", "Escape".
 * Shortcuts without modifiers are ignored while typing in a field.
 */
export function useShortcuts(map: Record<string, ShortcutHandler>, deps: unknown[] = []) {
  const pending = useRef<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey;
      const key = e.key.length === 1 ? e.key.toLowerCase() : e.key;
      const combo = `${mod ? "mod+" : ""}${e.shiftKey && e.key.length > 1 ? "shift+" : ""}${key}`;
      if (!mod && isTyping(e.target)) {
        if (combo !== "Escape") return;
      }
      if (pending.current) {
        const seq = `${pending.current} ${key}`;
        pending.current = null;
        if (timer.current) clearTimeout(timer.current);
        if (map[seq]) {
          e.preventDefault();
          map[seq](e);
          return;
        }
      }
      if (map[combo]) {
        e.preventDefault();
        map[combo](e);
        return;
      }
      if (!mod && Object.keys(map).some((k) => k.startsWith(`${key} `))) {
        pending.current = key;
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(() => (pending.current = null), 800);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
