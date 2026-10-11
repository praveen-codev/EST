import { Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";

export function Sparkle({ className }: { className?: string }) {
  return <Sparkles className={cn("size-3.5 text-ai", className)} aria-hidden />;
}

export function AiBadge({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-full bg-ai-soft px-2 py-0.5 text-[11px] font-medium text-ai", className)}>
      <Sparkles className="size-3" aria-hidden />
      {children}
    </span>
  );
}
