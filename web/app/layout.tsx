import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/shell/providers";
import { Suspense } from "react";
import { AppShell } from "@/components/shell/app-shell";
import { Splash } from "@/components/shell/splash";

const geistSans = Geist({ variable: "--font-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Enablement Session Tracker",
  description: "Capture enablement sessions with almost no typing.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`} suppressHydrationWarning>
      <body className="min-h-full flex flex-col bg-surface">
        <Providers>
          {/* The shell is fully client-driven (store, pathname, current time); Suspense lets Next prerender the static frame and stream the rest. */}
          <Suspense fallback={<Splash />}>
            <AppShell>{children}</AppShell>
          </Suspense>
        </Providers>
      </body>
    </html>
  );
}
