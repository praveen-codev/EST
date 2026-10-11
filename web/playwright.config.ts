import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "tests/e2e",
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: "http://localhost:3100",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // Both servers: the Spring Boot API on the local profile (H2, seeded, mock Google) and the Next dev server.
  webServer: [
    {
      command: "cd ../backend && ./gradlew bootRun --args='--spring.profiles.active=local' --quiet",
      url: "http://localhost:8080/actuator/health",
      reuseExistingServer: true,
      timeout: 240_000,
    },
    {
      command: "pnpm dev --port 3100",
      url: "http://localhost:3100",
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
});
