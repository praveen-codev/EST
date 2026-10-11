import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
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
