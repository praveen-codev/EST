import type { NextConfig } from "next";

/**
 * Single-host mode: when API_INTERNAL_URL is set at build time, the web server proxies /api/* to the
 * API, so browser, cookies and Google redirects all see one origin (no custom domain needed). Leave
 * it unset to call the API directly on NEXT_PUBLIC_API_URL (two hosts on one domain).
 */
const apiInternalUrl = (process.env.API_INTERNAL_URL ?? "").replace(/\/$/, "");

const nextConfig: NextConfig = {
  // Self-contained server bundle for the Docker image (infra/Dockerfile.web → Cloud Run).
  output: "standalone",
  async rewrites() {
    return apiInternalUrl ? [{ source: "/api/:path*", destination: `${apiInternalUrl}/api/:path*` }] : [];
  },
  // The Devin browser preview proxies the dev server from 127.0.0.1; Next blocks cross-origin dev resources otherwise.
  allowedDevOrigins: ["127.0.0.1", "localhost"],
  // The dev indicator sits exactly over the rail's bottom controls (theme, demo).
  devIndicators: false,
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
