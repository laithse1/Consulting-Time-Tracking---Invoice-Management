import { chromium, expect } from "@playwright/test";
import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { randomUUID } from "node:crypto";
import { once } from "node:events";

// Isolated disposable database: this never touches the user's financial records.
const root = resolve("..");
await mkdir(resolve(root, "tmp"), { recursive: true });
const externalBase = globalThis.process.env.E2E_BASE_URL;
const base = externalBase || "http://127.0.0.1:18081";
const token = "e2e-isolated-test-token-not-a-real-secret";
const java = globalThis.process.env.JAVA_HOME
  ? resolve(
      globalThis.process.env.JAVA_HOME,
      "bin",
      globalThis.process.platform === "win32" ? "java.exe" : "java",
    )
  : "java";
const process = externalBase
  ? undefined
  : spawn(
      java,
      [
        "-jar",
        resolve(root, "backend/target/invoice-manager-1.0.0.jar"),
        "--debug=false",
      ],
      {
        cwd: root,
        env: {
          ...globalThis.process.env,
          DEBUG: "",
          APP_PORT: "18081",
          BIND_ADDRESS: "127.0.0.1",
          DB_PATH: resolve(root, "tmp", `e2e-${randomUUID()}.db`),
          APP_TOKEN: token,
        },
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"],
      },
    );
let logs = "";
process?.stdout.on("data", (d) => (logs += d));
process?.stderr.on("data", (d) => (logs += d));
let browser;
async function api(path, body) {
  const r = await fetch(base + "/api" + path, {
    method: body === undefined ? "GET" : "POST",
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) throw new Error(path + ": " + (await r.text()));
  return r.json().catch(() => undefined);
}
try {
  let ready = false;
  for (let i = 0; i < 90; i++) {
    try {
      if ((await fetch(base)).ok) {
        ready = true;
        break;
      }
    } catch {}
    await new Promise((r) => setTimeout(r, 500));
  }
  if (!ready) throw new Error("Application failed to start: " + logs);
  expect((await fetch(base + "/api/state")).status).toBe(401);
  expect((await api("/state")).entries).toHaveLength(7);
  browser = await chromium.launch({ channel: "chrome", headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1100 },
    permissions: ["clipboard-read", "clipboard-write"],
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(base);
  await page.getByLabel("Local access token").fill(token);
  await page.getByRole("button", { name: /Unlock workspace/ }).click();
  await expect(page.getByText("A clear view of your work.")).toBeVisible();
  await page.getByLabel("Reporting month").fill("2026-09");
  await expect(page.getByText("Week of 2026-08-31")).toBeVisible();
  await page.screenshot({
    path: resolve(root, "docs/dashboard.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Toggle theme" }).click();
  await page.screenshot({
    path: resolve(root, "tmp/dashboard-dark.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Toggle theme" }).click();
  await page.getByRole("button", { name: "Time Tracker", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: "Draft / incomplete" }),
  ).toHaveCount(7);
  await page.getByRole("button", { name: "Log time", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Date worked").fill("2026-09-02");
  await dialog.getByLabel("Local start time").fill("09:00");
  await dialog.getByLabel("Actual hours (decimal)").fill("1");
  await dialog.locator('select[name="project_id"]').selectOption("2");
  await dialog
    .getByLabel("Description of services")
    .fill("Isolated E2E fixture: one hour of test services");
  await dialog.getByLabel("Entry status").selectOption("0");
  await dialog.getByRole("button", { name: "Save entry" }).click();
  await expect(dialog).toHaveCount(0);
  await expect(
    page.getByText("Isolated E2E fixture: one hour of test services"),
  ).toBeVisible();
  await page.getByRole("button", { name: "Timesheets", exact: true }).click();
  const weeks = await api(
    "/compliance?contract=1&from=2026-09-01&to=2026-09-30",
  );
  // Exercise weekly review through the UI for every partial/below-minimum week.
  for (const week of weeks) {
    await page
      .getByRole("row")
      .filter({ hasText: week.week })
      .getByRole("button", { name: "Review" })
      .click();
    await dialog
      .getByLabel(
        "Shortfall / partial-week clarification and planning acknowledgement",
      )
      .fill("Isolated test planning review; bill actual work only.");
    await dialog.getByRole("button", { name: "Save weekly review" }).click();
    await expect(dialog).toHaveCount(0);
  }
  await page
    .getByRole("button", { name: "Invoices", exact: false })
    .filter({ has: page.locator("svg") })
    .first()
    .click();
  await page
    .getByRole("button", { name: "Create invoice", exact: true })
    .click();
  await dialog.getByRole("button", { name: "Create draft for review" }).click();
  await expect(page.getByText("Validation passed")).toBeVisible();
  page.once("dialog", (d) => d.accept());
  await page.getByRole("button", { name: "Confirm & finalize" }).click();
  await expect(
    page.getByText("Ready for Review", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Withdraw unpaid invoice for correction" })
    .click();
  await dialog
    .getByLabel("Documented reason for withdrawal")
    .fill("Correct actual test duration");
  page.once("dialog", (d) => d.accept());
  await dialog.getByRole("button", { name: "Confirm withdrawal" }).click();
  await expect(page.getByText("Withdrawn", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Time Tracker", exact: true }).click();
  await page
    .getByRole("row")
    .filter({ hasText: "Isolated E2E fixture: one hour of test services" })
    .getByRole("button", { name: "Review" })
    .click();
  await dialog.getByLabel("Actual hours (decimal)").fill("2");
  await dialog.getByRole("button", { name: "Save entry" }).click();
  await expect(dialog).toHaveCount(0);
  await page
    .getByRole("button", { name: "Invoices", exact: false })
    .filter({ has: page.locator("svg") })
    .first()
    .click();
  await page.getByRole("button", { name: "Review", exact: true }).click();
  await page
    .getByRole("button", { name: "Create documented replacement" })
    .click();
  await dialog
    .getByLabel("Reason for replacement (required)")
    .fill("Corrected actual test duration after withdrawal");
  await dialog.getByRole("button", { name: "Create draft for review" }).click();
  await expect(page.getByText("Validation passed")).toBeVisible();
  page.once("dialog", (d) => d.accept());
  await page.getByRole("button", { name: "Confirm & finalize" }).click();
  await expect(
    page.getByText("Ready for Review", { exact: true }),
  ).toBeVisible();
  const state = await api("/state");
  const invoice = state.invoices[0];
  expect(invoice.total).toBe("150.00");
  expect(state.invoices[1].state).toBe("Replaced");
  expect(state.invoices[1].balance).toBe(0);
  for (const format of ["pdf", "xlsx", "csv"]) {
    const r = await fetch(
      base + `/api/invoices/${invoice.id}/export/${format}`,
      { headers: { Authorization: "Bearer " + token } },
    );
    expect(r.status).toBe(200);
    expect((await r.arrayBuffer()).byteLength).toBeGreaterThan(100);
  }
  await page.getByRole("button", { name: "Prepare submission email" }).click();
  await expect(dialog.getByLabel("Subject", { exact: true })).toHaveValue(
    /LA|Laith/,
  );
  await dialog
    .getByLabel("Optional CC to Brandon (enter address)")
    .fill("fixture@example.test");
  await dialog
    .getByRole("button", { name: "Copy email subject & body" })
    .click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain(
    "CC: fixture@example.test",
  );
  await dialog.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Mark as sent" }).click();
  await dialog.getByRole("button", { name: "Confirm sent" }).click();
  await expect(page.getByText("Sent", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Mark approved" }).click();
  await expect(page.getByText("Approved", { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: "Record payment", exact: true })
    .click();
  await dialog.getByLabel("Amount received USD").fill("25");
  await dialog.getByLabel("Payment method").fill("Test ACH");
  await dialog
    .getByLabel("Actual date received")
    .fill(new Date().toLocaleDateString("en-CA"));
  await dialog
    .getByLabel("Payment / bank reference")
    .fill("Isolated test receipt");
  await dialog.getByRole("button", { name: "Confirm receipt" }).click();
  await expect(page.getByText("Partially Paid", { exact: true })).toBeVisible();
  expect((await api("/state")).invoices[0].balance).toBe(125);
  await page.getByRole("button", { name: "Payments", exact: true }).click();
  await expect(page.getByText("Receipt not confirmed.")).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Dashboard", exact: true }).click();
  await page.screenshot({
    path: resolve(root, "tmp/dashboard-mobile.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(errors).toEqual([]);
  console.log(
    "PASS: login, seeded drafts, manual entry, weekly review, invoice finalization, withdrawal/correction/replacement, PDF/XLSX/CSV, editable email/CC, submission, approval, partial payment, historical isolation, dark/mobile layouts.",
  );
} finally {
  await browser?.close();
  if (process && process.exitCode === null) {
    const exited = once(process, "exit");
    process.kill();
    await exited;
  }
  await writeFile(resolve(root, "tmp/e2e-server.log"), logs);
}
