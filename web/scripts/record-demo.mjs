// Scripted walkthrough that records a video: node scripts/record-demo.mjs (API on :8080 local profile, web on :3100).
import { chromium } from "@playwright/test";

const base = "http://localhost:3100";
const api = "http://localhost:8080";
const pause = (ms) => new Promise((r) => setTimeout(r, ms));

await fetch(`${api}/api/v1/dev/reset`, { method: "POST" });

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  recordVideo: { dir: process.env.OUT_DIR ?? "/tmp/est-demo/raw", size: { width: 1440, height: 900 } },
});
const page = await context.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(String(e)));
const step = async (name, fn) => { console.log("▶", name); await fn(); await pause(900); };

await step("Sign-in page (local profile: persona buttons)", async () => {
  await page.goto(`${base}/sign-in`);
  await page.getByRole("button", { name: "Sign in as alex@cognition.ai" }).waitFor({ timeout: 20000 });
  await pause(1200);
  await page.getByRole("button", { name: "Sign in as alex@cognition.ai" }).click();
});

await step("Today: initial calendar sync runs, two Mercedes sessions appear", async () => {
  await page.getByRole("button", { name: /Enter feedback/ }).first().waitFor({ timeout: 30000 });
  await pause(1800);
});

await step("Inbox: likely / needs a look / duplicates / skipped", async () => {
  await page.goto(`${base}/inbox`);
  await page.locator("[data-candidate-id]").first().waitFor({ timeout: 20000 });
  await page.mouse.wheel(0, 600); await pause(900);
  await page.mouse.wheel(0, 600); await pause(900);
  await page.mouse.wheel(0, -1200);
});

await step("Enter feedback on today's 101: prefilled from the invite + teammates", async () => {
  await page.goto(`${base}/`);
  await page.getByRole("button", { name: /Enter feedback/ }).first().waitFor({ timeout: 20000 });
  await page.getByRole("button", { name: /Enter feedback/ }).first().click();
  await page.locator("#field-clientId").waitFor({ timeout: 20000 });
  await pause(1500);
});

await step("Accept the AI-drafted use case, add one more, pick an outcome", async () => {
  await page.locator("#field-useCases").scrollIntoViewIfNeeded(); await pause(800);
  await page.getByRole("button", { name: /^Accept .*Ab Initio/ }).first().click(); await pause(1200);
  await page.getByRole("button", { name: "Add use case" }).click(); await pause(600);
  const title2 = page.getByRole("textbox", { name: "Use case 2 title" });
  await title2.waitFor({ timeout: 10000 });
  await title2.pressSequentially("Fleet KPI pack to Power BI", { delay: 35 });
  await page.getByRole("combobox", { name: "Use case 2 type" }).click(); await pause(500);
  await page.getByRole("option", { name: "Excel to dashboard" }).click(); await pause(700);
  await page.locator("#field-outcomeId").scrollIntoViewIfNeeded(); await pause(600);
  await page.getByRole("radio", { name: "PR raised" }).click(); await pause(900);
});

await step("“Other…” on a dropdown: typed value saved to the reference table", async () => {
  await page.locator("#field-outcomeId").getByRole("button", { name: "Other…" }).click(); await pause(500);
  await page.getByRole("textbox", { name: "New outcome, e.g. Demo recorded" }).pressSequentially("Demo recorded", { delay: 40 });
  await page.keyboard.press("Enter"); await pause(1200);
});

await step("Back to Today: feedback complete", async () => {
  await page.goto(`${base}/`);
  await page.getByText("Feedback complete").waitFor({ timeout: 20000 });
  await pause(1500);
});

await step("Timeline and Calendar", async () => {
  await page.goto(`${base}/timeline`); await pause(1800);
  await page.goto(`${base}/calendar`); await pause(1800);
});

await step("Admin reference tables (as Dana) with the new outcome row", async () => {
  await page.getByRole("button", { name: /Account menu/ }).click(); await pause(600);
  await page.getByRole("menuitem", { name: /Dana Whitfield/ }).click();
  await page.getByText("Now viewing as Dana Whitfield").waitFor({ timeout: 15000 });
  await page.goto(`${base}/admin`);
  await page.getByText(/Editing as Dana Whitfield/).waitFor({ timeout: 30000 }); await pause(800);
  await page.getByRole("tab", { name: "Session outcomes" }).click(); await pause(1500);
});

await step("Settings → Linked calendars: add a partner Google account, unlink it", async () => {
  await page.goto(`${base}/settings/calendars`);
  await page.getByRole("heading", { name: "Linked calendars" }).waitFor({ timeout: 20000 }); await pause(1000);
  await page.getByRole("button", { name: "Add Google account" }).click();
  await page.waitForURL(/linked=/, { timeout: 20000 }); await pause(1800);
  page.once("dialog", (d) => d.accept());
  await page.getByRole("button", { name: /Unlink partner@cognition.ai/ }).click(); await pause(1500);
});

await step("Sign out → bounced to sign-in", async () => {
  await page.getByRole("button", { name: /Account menu/ }).click(); await pause(500);
  await page.getByRole("menuitem", { name: "Sign out" }).click();
  await page.waitForURL(/\/sign-in/, { timeout: 15000 }); await pause(1500);
});

console.log("page errors:", errors.length ? errors : "none");
await context.close();
await browser.close();
