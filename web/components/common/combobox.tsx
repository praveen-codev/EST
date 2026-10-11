"use client";

import { Check, ChevronsUpDown, PencilLine, Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator } from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { OtherInline, type Creator } from "./other-option";

export interface ComboOption {
  value: string;
  label: string;
  hint?: string;
  icon?: React.ReactNode;
}

interface ComboboxProps {
  value: string | null;
  options: ComboOption[];
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: string;
  onChange: (value: string | null) => void;
  /**
   * Enables ad-hoc entry: an "Other…" row opens an inline box; saving calls `onCreate`,
   * which must persist the value and return the id, and that id is selected immediately.
   */
  onCreate?: Creator;
  otherPlaceholder?: string;
  className?: string;
  size?: "sm" | "default";
  clearable?: boolean;
  trigger?: React.ReactNode;
}

export function Combobox({ value, options, placeholder = "Choose…", searchPlaceholder = "Search…", emptyText = "No match", onChange, onCreate, otherPlaceholder, className, size = "default", clearable, trigger }: ComboboxProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [other, setOther] = useState(false);
  const selected = options.find((o) => o.value === value) ?? null;
  const canCreateFromQuery = onCreate && query.trim() && !options.some((o) => o.label.toLowerCase() === query.trim().toLowerCase());

  const close = () => { setOpen(false); setQuery(""); setOther(false); };
  const saveNew = async (label: string) => {
    const id = await onCreate!(label);
    onChange(id);
    close();
  };

  return (
    <Popover open={open} onOpenChange={(o) => (o ? setOpen(true) : close())}>
      <PopoverTrigger asChild>
        {trigger ?? (
          <Button variant="outline" size={size} role="combobox" aria-expanded={open} className={cn("justify-between gap-2 font-normal", !selected && "text-muted-foreground", className)}>
            <span className="flex min-w-0 items-center gap-2">
              {selected?.icon}
              <span className="truncate">{selected?.label ?? placeholder}</span>
            </span>
            <ChevronsUpDown className="size-3.5 shrink-0 opacity-50" />
          </Button>
        )}
      </PopoverTrigger>
      <PopoverContent className="w-72 p-0" align="start">
        {other ? (
          <div className="p-2">
            <div className="mb-1.5 px-1 text-[11px] font-medium text-muted-foreground">Other — saved to the reference table</div>
            <OtherInline placeholder={otherPlaceholder ?? "Type a new value…"} onSave={saveNew} onCancel={() => setOther(false)} />
          </div>
        ) : (
          <Command>
            <CommandInput placeholder={searchPlaceholder} value={query} onValueChange={setQuery} />
            <CommandList>
              <CommandEmpty>{emptyText}</CommandEmpty>
              <CommandGroup>
                {clearable && value && (
                  <CommandItem value="__clear" onSelect={() => { onChange(null); close(); }} className="text-muted-foreground">
                    Clear selection
                  </CommandItem>
                )}
                {options.map((o) => (
                  <CommandItem key={o.value} value={`${o.label} ${o.hint ?? ""}`} onSelect={() => { onChange(o.value); close(); }}>
                    {o.icon}
                    <span className="flex-1 truncate">{o.label}</span>
                    {o.hint && <span className="text-xs text-muted-foreground">{o.hint}</span>}
                    {o.value === value && <Check className="size-4 text-ai" />}
                  </CommandItem>
                ))}
                {canCreateFromQuery && (
                  <CommandItem value={`__create ${query}`} onSelect={() => void saveNew(query.trim())}>
                    <Plus className="size-4" /> Save “{query.trim()}” as new
                  </CommandItem>
                )}
              </CommandGroup>
              {onCreate && (
                <>
                  <CommandSeparator />
                  <CommandGroup forceMount>
                    <CommandItem value="__other" forceMount onSelect={() => setOther(true)} className="text-ai">
                      <PencilLine className="size-4" /> Other…
                    </CommandItem>
                  </CommandGroup>
                </>
              )}
            </CommandList>
          </Command>
        )}
      </PopoverContent>
    </Popover>
  );
}
