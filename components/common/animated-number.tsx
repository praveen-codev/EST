"use client";

import { animate, useMotionValue, useReducedMotion } from "motion/react";
import { useEffect, useState } from "react";

export function AnimatedNumber({ value, format = (n: number) => Math.round(n).toString(), className }: { value: number; format?: (n: number) => string; className?: string }) {
  const mv = useMotionValue(0);
  const [animated, setAnimated] = useState(0);
  const reduced = useReducedMotion();
  useEffect(() => {
    if (reduced) return;
    const controls = animate(mv, value, { duration: 0.8, ease: [0.22, 1, 0.36, 1], onUpdate: (v) => setAnimated(v) });
    return () => controls.stop();
  }, [value, mv, reduced]);
  return <span className={className}>{format(reduced ? value : animated)}</span>;
}
