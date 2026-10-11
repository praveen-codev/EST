import { Suspense } from "react";
import { TimelineView } from "@/components/timeline/timeline-view";
import { Splash } from "@/components/shell/splash";

export default function TimelinePage() {
  return (
    <Suspense fallback={<Splash />}>
      <TimelineView />
    </Suspense>
  );
}
