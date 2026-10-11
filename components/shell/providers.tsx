"use client";

import { ThemeProvider, useTheme } from "next-themes";
import { Toaster } from "sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="dark" enableSystem={false} disableTransitionOnChange>
      <TooltipProvider delayDuration={200}>
        {children}
        <ThemedToaster />
      </TooltipProvider>
    </ThemeProvider>
  );
}

function ThemedToaster() {
  const { resolvedTheme } = useTheme();
  return (
    <Toaster
      position="bottom-right"
      closeButton
      theme={resolvedTheme === "light" ? "light" : "dark"}
      toastOptions={{
        className: "font-sans",
        style: { background: "var(--popover)", color: "var(--popover-foreground)", border: "1px solid var(--border)" },
      }}
    />
  );
}
