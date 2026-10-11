import { expect, test, type Page } from "@playwright/test";

async function waitForInbox(page: Page) {
  await page.goto("/inbox");
  await expect(page.locator("[data-candidate-id]").first()).toBeVisible({ timeout: 20_000 });
}

async function timelineSessionCount(page: Page) {
  await page.goto("/timeline");
  const text = await page.getByText(/\d+ sessions/).first().textContent();
  return Number(text!.match(/(\d+) sessions/)![1]);
}

test.describe.configure({ mode: "serial" });

test("capture workflow: sync → one-click confirm → timeline → re-sync without duplicates → manual edit survives", async ({ page }) => {
  // Fresh browser context ⇒ empty IndexedDB ⇒ initial sync runs automatically.
  await waitForInbox(page);
  await expect(page.getByRole("heading", { name: "Likely sessions" })).toBeVisible();
  const likely = page.getByRole("region", { name: "Likely sessions" }).locator("[data-candidate-id]");
  const likelyCount = await likely.count();
  expect(likelyCount).toBeGreaterThan(3);

  // Candidates are never confirmed automatically.
  const before = await timelineSessionCount(page);
  await waitForInbox(page);

  // One click confirms every likely session with its prefilled fields.
  await page.getByRole("button", { name: /Confirm all likely/ }).click();
  await expect(page.getByText(/Confirmed \d+ sessions/)).toBeVisible();
  await expect(page.getByRole("region", { name: "Likely sessions" })).toHaveCount(0);

  const after = await timelineSessionCount(page);
  expect(after).toBe(before + likelyCount);

  // Open the first confirmed session and make a manual edit.
  await page.getByRole("button", { name: /Acme Platform — Devin office hours/ }).first().click();
  await expect(page).toHaveURL(/\/sessions\//);
  const title = page.getByRole("textbox", { name: "Session title" });
  await title.fill("Acme office hours — my title");
  await expect(page.getByText("All changes saved")).toBeVisible();
  const sessionUrl = page.url();

  // Accept one pending suggestion by keyboard: focus the chip, press Enter.
  const chip = page.getByRole("group", { name: /^Suggestion:/ }).first();
  const chipLabel = await chip.getAttribute("aria-label");
  await chip.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("group", { name: chipLabel! })).toHaveCount(0);

  // Incremental sync: the Acme event changes upstream, new events arrive, nothing duplicates.
  await page.getByRole("button", { name: /Synced|Sync calendar/ }).click();
  await expect(page.getByText(/Synced \d+ events/)).toBeVisible({ timeout: 10_000 });
  await page.goto(sessionUrl);
  await expect(page.getByRole("textbox", { name: "Session title" })).toHaveValue("Acme office hours — my title");
  await page.getByRole("button", { name: /Advanced/ }).click();
  await expect(page.getByText(/Raj Patel/).first()).toBeVisible(); // refreshed evidence shows in the side panel

  // Re-syncing identical data creates no new sessions or candidates.
  const sessionsAfterSync = await timelineSessionCount(page);
  expect(sessionsAfterSync).toBe(after);
  const acmeRows = page.getByRole("button", { name: /Acme office hours — my title/ });
  await expect(acmeRows).toHaveCount(1);
});

test("noise is never classified as a session and cancelled events are explained", async ({ page }) => {
  await waitForInbox(page);
  await page.getByRole("button", { name: /events skipped as not enablement/ }).click();
  await expect(page.getByText("Enablement team standup")).toBeVisible();
  await expect(page.getByText("Event was cancelled")).toBeVisible();
  await expect(page.getByRole("region", { name: "Likely sessions" }).getByText("standup")).toHaveCount(0);
});

test("persona switch shows another engineer's inbox while history is shared", async ({ page }) => {
  await waitForInbox(page);
  await page.getByRole("button", { name: /Switch persona/ }).click();
  await page.getByRole("menuitem", { name: /Priya Nair/ }).click();
  await expect(page.getByText("Now viewing as Priya Nair")).toBeVisible();
  await expect(page.locator("[data-candidate-id]").first()).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("Meridian Clinical — Angular migration hands-on (part 2)")).toBeVisible();
});
