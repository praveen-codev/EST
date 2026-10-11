import { Suspense } from "react";
import { SessionWorkspace } from "@/components/session/session-workspace";
import { Splash } from "@/components/shell/splash";

export default function SessionPage() {
  return (
    <Suspense fallback={<Splash />}>
      <SessionWorkspace />
    </Suspense>
  );
}
