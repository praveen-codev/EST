import { expect, test, type Page } from "@playwright/test";

const API = process.env.API_URL ?? "http://localhost:8080";

/** The API holds state now: every test starts from a freshly re-seeded local database and a signed-in persona. */
test.beforeEach(async ({ page, request }) => {
  await request.post(`${API}/api/v1/dev/reset`);
  await signInAs(page, "alex@cognition.ai");
});

async function signInAs(page: Page, email: string) {
  await page.goto("/sign-in");
  await page.getByRole("button", { name: `Sign in as ${email}` }).click({ timeout: 20_000 });
  await page.waitForURL((u) => !u.pathname.startsWith("/sign-in"), { timeout: 20_000 });
}

async function switchPersona(page: Page, name: RegExp) {
  await page.getByRole("button", { name: /Account menu/ }).click();
  await page.getByRole("menuitem", { name }).click();
}

async function waitForInbox(page: Page) {
  await page.goto("/inbox");
  await expect(page.locator("[data-candidate-id]").first()).toBeVisible({ timeout: 30_000 });
}

async function timelineSessionCount(page: Page) {
  await page.goto("/timeline");
  const text = await page.getByText(/\d+ sessions/).first().textContent();
  return Number(text!.match(/(\d+) sessions/)![1]);
}

test.describe.configure({ mode: "serial" });

test("capture workflow: sync → one-click confirm → timeline → re-sync without duplicates → manual edit survives", async ({ page }) => {
  // Fresh database ⇒ the initial calendar sync runs automatically on first load.
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
  await page.getByRole("button", { name: /Acme Platform — Devin 101 session/ }).first().click();
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
  await switchPersona(page, /Priya Nair/);
  await expect(page.getByText("Now viewing as Priya Nair")).toBeVisible({ timeout: 15_000 });
  await expect(page.locator("[data-candidate-id]").first()).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("Meridian Clinical — 201 session (part 2)")).toBeVisible({ timeout: 20_000 });
});

test("only the team's formats count; admins manage the catalogue, members read it", async ({ page }) => {
  await waitForInbox(page);
  // A generic "enablement workshop" with a real client is dialled down to Needs a look.
  const review = page.getByRole("region", { name: "Needs a look" });
  await expect(review.getByText("Helios Grid — enablement workshop")).toBeVisible();
  await expect(page.getByRole("region", { name: "Likely sessions" }).getByText("enablement workshop")).toHaveCount(0);

  // Members see the catalogue read-only.
  await page.goto("/admin");
  await expect(page.getByText(/Read-only/)).toBeVisible();
  await expect(page.getByRole("textbox", { name: "format label", exact: true }).first()).toBeDisabled();

  // The admin can add a format.
  await switchPersona(page, /Dana Whitfield/);
  await page.goto("/admin");
  await expect(page.getByText(/Editing as Dana Whitfield/)).toBeVisible({ timeout: 30_000 });
  await page.getByRole("textbox", { name: "New format label" }).fill("Lunch & learn");
  await page.getByRole("textbox", { name: "New format keywords" }).fill("lunch and learn");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "format label", exact: true }).last()).toHaveValue("Lunch & learn");
  await page.getByRole("button", { name: "Archive format" }).last().click();
  await expect(page.getByText(/1 archived/)).toBeVisible();
});

test("home shows only today's sessions; the CTA opens a prefilled feedback form with use cases and outcome", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("button", { name: /Enter feedback/ }).first()).toBeVisible({ timeout: 30_000 });
  // Only today's two Mercedes sessions, nothing else.
  await expect(page.getByText(/2 sessions today/)).toBeVisible();
  await expect(page.getByText("Mercedes-Benz Data Engineering — Devin 101 session (Ab Initio squad)")).toBeVisible();
  await expect(page.getByText("Vantage Claims — Sprint in a day")).toHaveCount(0);

  await page.getByRole("button", { name: /Enter feedback/ }).first().click();
  await expect(page).toHaveURL(/\/sessions\//);
  // Prefilled from the invite + teammates' history.
  await expect(page.locator("#field-clientId").getByText("Mercedes-Benz Tech")).toBeVisible();
  await expect(page.getByRole("radio", { name: "101 session" })).toHaveAttribute("data-state", "on");
  await expect(page.getByRole("spinbutton", { name: "Number of participants" })).toHaveValue("12");
  await expect(page.locator("#field-contacts").getByText("Katrin Vogel", { exact: true })).toBeVisible();

  // Accept the AI-drafted use case (type + technologies come with it), add a manual one, pick an outcome.
  await page.getByRole("button", { name: /^Accept .*Ab Initio/ }).first().click();
  await expect(page.getByRole("textbox", { name: "Use case 1 title" })).toHaveValue(/Ab Initio/);
  await expect(page.getByRole("combobox", { name: "Use case 1 type" })).toContainText("ETL tool migration");
  await page.getByRole("button", { name: "Add use case" }).click();
  const title2 = page.getByRole("textbox", { name: "Use case 2 title" });
  await expect(title2).toBeFocused();
  await title2.fill("Fleet KPI pack to Power BI");
  await page.getByRole("combobox", { name: "Use case 2 type" }).click();
  await page.getByRole("option", { name: "Excel to dashboard" }).click();
  await page.getByRole("radio", { name: "PR raised" }).click();
  await expect(page.getByText("All changes saved")).toBeVisible();

  await page.goto("/");
  await expect(page.getByText("Feedback complete")).toBeVisible();
  await expect(page.getByText(/1 with feedback in/)).toBeVisible();
});

test("every dropdown accepts an ad-hoc 'Other…' value: saved to the reference table, selected, dropdown closed", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: /Enter feedback/ }).first().click({ timeout: 30_000 });
  await expect(page).toHaveURL(/\/sessions\//);

  // Toggle group (outcome): Enter saves and selects.
  await page.locator("#field-outcomeId").getByRole("button", { name: "Other…" }).click();
  await page.getByRole("textbox", { name: "New outcome, e.g. Demo recorded" }).fill("Demo recorded");
  await page.keyboard.press("Enter");
  await expect(page.getByRole("radio", { name: "Demo recorded" })).toHaveAttribute("data-state", "on");

  // Combobox (technology): the check icon saves, popover closes, chip appears.
  await page.locator("#field-technologyIds").getByRole("button", { name: "+ technology" }).click();
  await page.getByRole("option", { name: "Other…" }).click();
  await page.getByRole("textbox", { name: "New technology, e.g. Ab Initio GDE" }).fill("Ab Initio GDE");
  await page.getByRole("button", { name: "Save new value" }).click();
  await expect(page.getByRole("textbox", { name: "New technology, e.g. Ab Initio GDE" })).toHaveCount(0);
  await expect(page.locator("#field-technologyIds").getByText("Ab Initio GDE")).toBeVisible();

  // Select (use-case type) and contact role.
  await page.getByRole("button", { name: "Add use case" }).click();
  await page.getByRole("combobox", { name: "Use case 1 type" }).click();
  await page.getByRole("option", { name: "Other…" }).click();
  await page.getByRole("textbox", { name: "New use-case type…" }).fill("Test automation");
  await page.keyboard.press("Enter");
  await expect(page.getByRole("combobox", { name: "Use case 1 type" })).toContainText("Test automation");
  await page.locator("#field-contacts").getByRole("combobox", { name: /Role for Katrin Vogel/ }).click();
  await page.getByRole("option", { name: "Other…" }).click();
  await page.getByRole("textbox", { name: "New role…" }).fill("Data lead");
  await page.keyboard.press("Enter");
  await expect(page.locator("#field-contacts").getByRole("combobox", { name: /Role for Katrin Vogel/ })).toContainText("Data lead");

  // Required fields carry the red asterisk.
  await expect(page.locator("#field-clientId").getByTitle("Required")).toBeVisible();
  await expect(page.locator("#field-outcomeId").getByTitle("Required")).toBeVisible();

  // The new values are real rows in the admin reference tables.
  await page.goto("/admin");
  await page.getByRole("tab", { name: "Session outcomes" }).click();
  await expect(page.locator('input[value="Demo recorded"]')).toHaveCount(1);
  await page.getByRole("tab", { name: "Technologies" }).click();
  await expect(page.locator('input[value="Ab Initio GDE"]')).toHaveCount(1);
  await page.getByRole("tab", { name: "Contact roles" }).click();
  await expect(page.locator('input[value="Data lead"]')).toHaveCount(1);
});

test("sign-in gate, linked calendars (partner account), sign out", async ({ page }) => {
  // A signed-in member can link a second Google account; in the local profile "Google" is simulated.
  await page.goto("/settings/calendars");
  await expect(page.getByRole("heading", { name: "Linked calendars" })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("alex@cognition.ai", { exact: false }).first()).toBeVisible();
  await page.getByRole("button", { name: "Add Google account" }).click();
  await page.waitForURL(/settings\/calendars\?linked=/, { timeout: 20_000 });
  await expect(page.getByText("Calendar linked")).toBeVisible();
  await expect(page.getByText("partner@cognition.ai", { exact: true })).toBeVisible({ timeout: 10_000 });

  // Unlink keeps the page consistent and never shows any token material.
  page.once("dialog", (d) => d.accept());
  await page.getByRole("button", { name: "Unlink partner@cognition.ai" }).click();
  await expect(page.getByText(/events already imported were kept/)).toBeVisible({ timeout: 10_000 });
  const html = await page.content();
  expect(html).not.toMatch(/mock-refresh|refresh_token/);

  // Sign out → protected routes bounce to sign-in; signing back in restores the session.
  await page.getByRole("button", { name: /Account menu/ }).click();
  await page.getByRole("menuitem", { name: "Sign out" }).click();
  await page.waitForURL(/\/sign-in/, { timeout: 15_000 });
  await page.goto("/inbox");
  await page.waitForURL(/\/sign-in\?next=/, { timeout: 15_000 });
  await signInAs(page, "dana@cognition.ai");
  await expect(page.getByRole("button", { name: /Signed in as Dana Whitfield/ })).toBeVisible({ timeout: 20_000 });
});
