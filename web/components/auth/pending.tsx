"use client";

import { Clock } from "lucide-react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/api/auth";

export function Pending() {
  const router = useRouter();
  const error = useAuth((s) => s.error);
  return (
    <main className="flex min-h-dvh items-center justify-center bg-surface p-6">
      <div className="w-full max-w-sm rounded-3xl border bg-card p-8 text-center shadow-xl">
        <Clock className="mx-auto size-8 text-warning" />
        <h1 className="mt-4 text-lg font-semibold">Waiting for approval</h1>
        <p className="mt-2 text-sm text-muted-foreground">{error ?? "Your account was created and is waiting for an admin to approve it. You'll be able to sign in as soon as that happens."}</p>
        <Button variant="outline" className="mt-6" onClick={() => router.replace("/sign-in")}>Back to sign in</Button>
      </div>
    </main>
  );
}
