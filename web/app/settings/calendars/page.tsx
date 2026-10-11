import { Suspense } from "react";
import { LinkedCalendars } from "@/components/settings/linked-calendars";

export default function LinkedCalendarsPage() {
  return (
    <Suspense>
      <LinkedCalendars />
    </Suspense>
  );
}
