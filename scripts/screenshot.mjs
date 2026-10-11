// Dev helper: capture screenshots of the running app for a visual check.
// Usage: node scripts/screenshot.mjs [baseUrl]
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const base = process.argv[2] ?? "http://localhost:3100";
mkdirSync("/tmp/est-shots", { recursive: true });
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, colorScheme: "light" });
const errors = [];
page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
page.on("pageerror", (e) => errors.push(String(e)));

await page.goto(`${base}/`);
await page.waitForSelector("text=Enter feedback", { timeout: 20000 });
await page.waitForTimeout(1200);
await page.screenshot({ path: "/tmp/est-shots/today.png", fullPage: true });
await page.getByRole("button", { name: /Enter feedback|Prepare feedback/ }).first().click();
await page.waitForURL(/\/sessions\//);
await page.waitForTimeout(1200);
await page.screenshot({ path: "/tmp/est-shots/session.png", fullPage: true });

await page.goto(`${base}/inbox`);
await page.waitForSelector("[data-candidate-id]", { timeout: 20000 });
await page.waitForTimeout(800);
await page.screenshot({ path: "/tmp/est-shots/inbox.png", fullPage: true });

for (const path of ["/timeline", "/calendar", "/admin"]) {
  await page.goto(`${base}${path}`);
  await page.waitForTimeout(1200);
  await page.screenshot({ path: `/tmp/est-shots${path}.png`, fullPage: true });
}
await page.goto(`${base}/inbox`);
await page.getByRole("button", { name: "Toggle theme" }).click();
await page.waitForTimeout(800);
await page.screenshot({ path: "/tmp/est-shots/inbox-light.png", fullPage: false });

console.log("errors:", errors.length ? errors : "none");
await browser.close();
