import { chromium, expect } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const base = process.env.E2E_BASE_URL;
if (!base) throw new Error("Set E2E_BASE_URL to the disposable assistant verification server.");
const token = "e2e-isolated-test-token-not-a-real-secret";
const response = await fetch(base + "/api/state", {
  headers: { Authorization: "Bearer " + token },
});
if (!response.ok)
  throw new Error("Screenshot server does not accept the isolated test credential.");
const state = await response.json();
if (state.entries.length < 9 || state.invoices.length !== 1) {
  throw new Error("Run the assistant verification workflow on this disposable database first.");
}

const directory = resolve("../docs/images");
await mkdir(directory, { recursive: true });
const browser = await chromium.launch({
  channel:
    process.env.E2E_BROWSER_CHANNEL === "bundled"
      ? undefined
      : process.env.E2E_BROWSER_CHANNEL || "chrome",
  headless: true,
});
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  await page.goto(base);
  await page.getByLabel("Local access token").fill(token);
  await page.getByRole("button", { name: /Unlock workspace/ }).click();
  await expect(page.getByText("A clear view of your work.")).toBeVisible();
  await page.getByLabel("Reporting month").fill(state.invoices[0].period_start.slice(0, 7));
  await page.screenshot({ path: resolve(directory, "dashboard.png") });
  await page.getByRole("button", { name: "Toggle theme" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.screenshot({ path: resolve(directory, "dashboard-dark.png") });
  await page.getByRole("button", { name: "Toggle theme" }).click();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  const source =
    "2026-09-15: Spent 2h refactoring geospatial-mcp-server, PR #42.\n2026-09-16: Spent 30m documenting Audiom.\nDraft invoice September 2026\nGenerate report September 2026";
  await expect(page.getByLabel("Work description", { exact: true })).toBeEnabled();
  await page.getByLabel("Work description", { exact: true }).fill(source);
  await page.getByRole("button", { name: "Review action plan" }).click();
  await expect(page.getByText("4 proposed actions")).toBeVisible();
  await page.setViewportSize({ width: 1440, height: 1300 });
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: resolve(directory, "assistant.png") });
  await page
    .locator("nav")
    .getByRole("button", { name: /^Invoices/ })
    .click();
  await page
    .getByRole("button", { name: /Review/ })
    .first()
    .click();
  await expect(page.getByRole("heading", { name: state.invoices[0].number })).toBeVisible();
  await page.setViewportSize({ width: 1600, height: 1100 });
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: resolve(directory, "invoice.png") });
  await page.getByRole("button", { name: "Dashboard", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => window.scrollTo(0, 0));
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: resolve(directory, "mobile.png") });
  console.log("Captured five README screenshots from isolated demonstration data.");
} finally {
  await browser.close();
}
