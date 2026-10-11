"use client";

import { useEffect, useRef, useState } from "react";
import { Check, PencilLine, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";

export const OTHER_VALUE = "__other__";

/**
 * Inline "type your own" entry: a text box with a check-mark. Enter or the check saves,
 * Escape or ✕ cancels. `onSave` must persist the value and return the id to select.
 */
export function OtherInline({ placeholder = "Type a new value…", onSave, onCancel, autoFocus = true, className, size = "default" }: {
  placeholder?: string;
  onSave: (label: string) => void;
  onCancel: () => void;
  autoFocus?: boolean;
  className?: string;
  size?: "sm" | "default";
}) {
  const [value, setValue] = useState("");
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (autoFocus) ref.current?.focus();
  }, [autoFocus]);
  const canSave = value.trim().length > 0;
  const save = () => canSave && onSave(value.trim());
  return (
    <div className={cn("flex items-center gap-1", className)} role="group" aria-label="Other value">
      <Input
        ref={ref}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className={cn("h-8", size === "sm" && "h-7 text-xs")}
        onKeyDown={(e) => {
          if (e.key === "Enter") { e.preventDefault(); e.stopPropagation(); save(); }
          if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); onCancel(); }
        }}
      />
      <Button type="button" size={size === "sm" ? "icon-xs" : "icon-sm"} onClick={save} disabled={!canSave} aria-label="Save new value" className="shrink-0 bg-success text-background hover:bg-success/90">
        <Check />
      </Button>
      <Button type="button" variant="ghost" size={size === "sm" ? "icon-xs" : "icon-sm"} onClick={onCancel} aria-label="Cancel" className="shrink-0 text-muted-foreground">
        <X />
      </Button>
    </div>
  );
}

export interface OptionItem {
  value: string;
  label: string;
  hint?: string;
}

/** A shadcn Select whose last entry is "Other…", which swaps in an OtherInline. */
export function SelectWithOther({ value, options, placeholder, onChange, onCreate, otherPlaceholder, className, ariaLabel, size }: {
  value: string | null;
  options: OptionItem[];
  placeholder?: string;
  onChange: (id: string) => void;
  onCreate: (label: string) => string;
  otherPlaceholder?: string;
  className?: string;
  ariaLabel: string;
  size?: "sm" | "default";
}) {
  const [other, setOther] = useState(false);
  if (other) {
    return (
      <OtherInline
        size={size}
        placeholder={otherPlaceholder ?? "Type a new value…"}
        onSave={(label) => { onChange(onCreate(label)); setOther(false); }}
        onCancel={() => setOther(false)}
        className={className}
      />
    );
  }
  return (
    <Select value={value ?? ""} onValueChange={(v) => (v === OTHER_VALUE ? setOther(true) : onChange(v))}>
      <SelectTrigger aria-label={ariaLabel} className={className} size={size}><SelectValue placeholder={placeholder} /></SelectTrigger>
      <SelectContent>
        {options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
        <SelectSeparator />
        <SelectItem value={OTHER_VALUE} className="text-ai"><PencilLine className="size-3.5" /> Other…</SelectItem>
      </SelectContent>
    </Select>
  );
}

/** Single-choice pills with a trailing "Other…" pill that opens an OtherInline. */
export function ToggleGroupWithOther({ value, options, onChange, onCreate, otherPlaceholder, itemClassName, ariaLabel }: {
  value: string | null;
  options: OptionItem[];
  onChange: (id: string) => void;
  onCreate: (label: string) => string;
  otherPlaceholder?: string;
  itemClassName?: string;
  ariaLabel: string;
}) {
  const [other, setOther] = useState(false);
  return (
    <div className="flex flex-wrap items-center gap-2">
      <ToggleGroup type="single" variant="outline" size="sm" value={value ?? ""} onValueChange={(v) => v && onChange(v)} className="flex-wrap" aria-label={ariaLabel}>
        {options.map((o) => (
          <ToggleGroupItem key={o.value} value={o.value} className={cn("text-xs", itemClassName)} title={o.hint}>{o.label}</ToggleGroupItem>
        ))}
      </ToggleGroup>
      {other ? (
        <OtherInline size="sm" placeholder={otherPlaceholder ?? "Type a new value…"} onSave={(label) => { onChange(onCreate(label)); setOther(false); }} onCancel={() => setOther(false)} />
      ) : (
        <Button type="button" variant="outline" size="sm" onClick={() => setOther(true)} className="gap-1 border-dashed text-xs text-ai">
          <PencilLine data-icon="inline-start" /> Other…
        </Button>
      )}
    </div>
  );
}

/** Red, 1.5× asterisk for required fields. */
export function RequiredMark() {
  return (
    <span className="ml-0.5 align-top text-[1.5em] font-semibold leading-none text-destructive" aria-hidden title="Required">
      *
    </span>
  );
}
