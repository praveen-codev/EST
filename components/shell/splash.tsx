"use client";

import { motion } from "motion/react";
import { Sparkles } from "lucide-react";

export function Splash() {
  return (
    <div className="flex h-dvh w-full items-center justify-center">
      <motion.div initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} className="flex flex-col items-center gap-4">
        <div className="ai-gradient flex size-12 items-center justify-center rounded-2xl text-white shadow-lg shadow-ai/30">
          <Sparkles className="size-6" />
        </div>
        <div className="text-sm text-muted-foreground">Loading your sessions…</div>
      </motion.div>
    </div>
  );
}
