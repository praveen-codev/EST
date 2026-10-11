"use client";

import { Check, ChevronsUpDown, Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

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
  onCreate?: (label: string) => void;
  className?: string;
  size?: "sm" | "default";
  clearable?: boolean;
  trigger?: React.ReactNode;
}

export function Combobox({ value, options, placeholder = "Choose…", searchPlaceholder = "Search…", emptyText = "No match", onChange, onCreate, className, size = "default", clearable, trigger }: ComboboxProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const selected = options.find((o) => o.value === value) ?? null;
  const canCreate = onCreate && query.trim() && !options.some((o) => o.label.toLowerCase() === query.trim().toLowerCase());
  return (
    <Popover open={open} onOpenChange={setOpen}>
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
        <Command>
          <CommandInput placeholder={searchPlaceholder} value={query} onValueChange={setQuery} />
          <CommandList>
            <CommandEmpty>{emptyText}</CommandEmpty>
            <CommandGroup>
              {clearable && value && (
                <CommandItem value="__clear" onSelect={() => { onChange(null); setOpen(false); }} className="text-muted-foreground">
                  Clear selection
                </CommandItem>
              )}
              {options.map((o) => (
                <CommandItem key={o.value} value={`${o.label} ${o.hint ?? ""}`} onSelect={() => { onChange(o.value); setOpen(false); setQuery(""); }}>
                  {o.icon}
                  <span className="flex-1 truncate">{o.label}</span>
                  {o.hint && <span className="text-xs text-muted-foreground">{o.hint}</span>}
                  {o.value === value && <Check className="size-4 text-ai" />}
                </CommandItem>
              ))}
              {canCreate && (
                <CommandItem value={`__create ${query}`} onSelect={() => { onCreate!(query.trim()); setOpen(false); setQuery(""); }}>
                  <Plus className="size-4" /> Create “{query.trim()}”
                </CommandItem>
              )}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
