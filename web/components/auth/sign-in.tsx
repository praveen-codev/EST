"use client";

import Script from "next/script";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/api/auth";
import { API_URL } from "@/lib/api/client";

const PERSONAS = ["alex@cognition.ai", "priya@cognition.ai", "marcus@cognition.ai", "sofia@cognition.ai", "dana@cognition.ai"];

declare global {
  interface Window {
    google?: { accounts: { id: { initialize: (o: Record<string, unknown>) => void; renderButton: (el: HTMLElement, o: Record<string, unknown>) => void } } };
  }
}

export function SignIn() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next") && params.get("next")!.startsWith("/") ? params.get("next")! : "/";
  const status = useAuth((s) => s.status);
  const error = useAuth((s) => s.error);
  const bootstrap = useAuth((s) => s.bootstrap);
  const signInWithGoogle = useAuth((s) => s.signInWithGoogle);
  const devLogin = useAuth((s) => s.devLogin);
  const buttonRef = useRef<HTMLDivElement>(null);
  const [features, setFeatures] = useState<{ devMode: boolean; googleClientId: string } | null>(null);
  const clientId = features?.googleClientId || process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || "";

  useEffect(() => {
    if (status === "unknown") void bootstrap();
    if (status === "signed-in") router.replace(next);
    if (status === "pending") router.replace("/pending");
  }, [status, bootstrap, router, next]);

  // Public runtime flags: whether persona sign-in exists (local profile) and the Google OAuth client id.
  useEffect(() => {
    fetch(`${API_URL}/api/v1/features`).then((r) => r.json()).then(setFeatures).catch(() => setFeatures({ devMode: false, googleClientId: "" }));
  }, []);

  const renderGoogle = useCallback(() => {
    if (!clientId || !window.google || !buttonRef.current) return;
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (res: { credential: string }) => void signInWithGoogle(res.credential),
      ux_mode: "popup",
      auto_select: false,
    });
    window.google.accounts.id.renderButton(buttonRef.current, { theme: "filled_black", size: "large", shape: "pill", text: "signin_with", width: 280 });
  }, [signInWithGoogle, clientId]);
  useEffect(() => { renderGoogle(); }, [renderGoogle]);

  return (
    <main className="flex min-h-dvh items-center justify-center bg-surface p-6">
      {clientId && <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onLoad={renderGoogle} />}
      <div className="w-full max-w-sm rounded-3xl border bg-card p-8 shadow-xl">
        <div className="ai-gradient mb-6 flex size-11 items-center justify-center rounded-2xl text-white shadow-md shadow-ai/25">
          <Sparkles className="size-5" />
        </div>
        <h1 className="text-xl font-semibold">Enablement Session Tracker</h1>
        <p className="mt-1 text-sm text-muted-foreground">Sign in with your work Google account. Your calendar is read, never written.</p>
        <div className="mt-6 flex flex-col gap-3">
          {clientId ? <div ref={buttonRef} className="flex justify-center" /> : features && <p className="rounded-lg border border-dashed p-3 text-xs text-muted-foreground">Google sign-in is not configured on this environment (GOOGLE_CLIENT_ID).</p>}
          {status === "rejected" && error && <p role="alert" className="rounded-lg bg-destructive/10 p-3 text-xs text-destructive">{error}</p>}
          {features?.devMode && (
            <div className="mt-2 border-t pt-4">
              <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Local profile · sign in as a persona</p>
              <div className="grid grid-cols-1 gap-1.5">
                {PERSONAS.map((email) => (
                  <Button key={email} variant="outline" size="sm" className="justify-start font-mono text-xs" onClick={() => void devLogin(email)} aria-label={`Sign in as ${email}`}>
                    {email}
                  </Button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
