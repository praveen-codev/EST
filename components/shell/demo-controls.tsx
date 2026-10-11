"use client";

import { FlaskConical, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Switch } from "@/components/ui/switch";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useStore } from "@/lib/domain/store";
import type { ScenarioId } from "@/lib/domain/types";

const SCENARIOS: Array<{ id: ScenarioId; label: string; hint: string }> = [
  { id: "clean", label: "Clean week", hint: "Well-described invites, no duplicates" },
  { id: "messy", label: "Messy week", hint: "Duplicates, sparse invites, cancellations, noise" },
  { id: "empty", label: "Empty calendar", hint: "Nothing to sync — empty states" },
];

export function DemoControls() {
  const scenario = useStore((s) => s.settings.scenario);
  const showReasoning = useStore((s) => s.settings.showReasoning);
  const setScenario = useStore((s) => s.setScenario);
  const resetDemo = useStore((s) => s.resetDemo);
  const toggleReasoning = useStore((s) => s.toggleReasoning);
  return (
    <Popover>
      <Tooltip>
        <TooltipTrigger asChild>
          <PopoverTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="Demo controls">
              <FlaskConical className="size-4" />
            </Button>
          </PopoverTrigger>
        </TooltipTrigger>
        <TooltipContent side="right">Demo controls</TooltipContent>
      </Tooltip>
      <PopoverContent side="right" align="end" className="w-80 p-4">
        <div className="mb-3">
          <div className="text-sm font-semibold">Demo controls</div>
          <p className="text-xs text-muted-foreground">Everything here is mocked in the browser. No credentials, no network.</p>
        </div>
        <div className="mb-1 text-xs font-medium text-muted-foreground">Calendar scenario</div>
        <ToggleGroup
          type="single"
          value={scenario}
          onValueChange={(v) => {
            if (!v) return;
            setScenario(v as ScenarioId);
            toast.info(`Scenario: ${SCENARIOS.find((s) => s.id === v)!.label}`, { description: "Data reset. The initial sync runs again." });
          }}
          className="mb-1 w-full"
          variant="outline"
        >
          {SCENARIOS.map((s) => (
            <ToggleGroupItem key={s.id} value={s.id} className="flex-1 text-xs">
              {s.label}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        <p className="mb-4 text-[11px] text-muted-foreground">{SCENARIOS.find((s) => s.id === scenario)!.hint}</p>
        <label className="mb-4 flex items-center justify-between gap-3 text-sm">
          <span>
            Show AI reasoning
            <span className="block text-[11px] text-muted-foreground">Classification reasons on every card</span>
          </span>
          <Switch checked={showReasoning} onCheckedChange={toggleReasoning} />
        </label>
        <Button
          variant="outline"
          className="w-full"
          onClick={() => {
            resetDemo();
            toast.success("Demo reset", { description: "Candidates and confirmed sessions cleared. Teammates' history kept." });
          }}
        >
          <RotateCcw data-icon="inline-start" /> Reset demo data
        </Button>
      </PopoverContent>
    </Popover>
  );
}
