import { chromium, expect } from "@playwright/test";
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve("..");
await mkdir(resolve(root, "tmp"), { recursive: true });
const base = "http://127.0.0.1:18084";
const token = "e2e-isolated-test-token-not-a-real-secret";
const server = spawn(
  process.env.JAVA_HOME
    ? resolve(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java")
    : "java",
  [
    "-jar",
    process.env.E2E_JAR || resolve(root, "backend/target/invoice-manager-1.0.0.jar"),
    "--debug=false",
    "--logging.level.root=WARN",
  ],
  {
    cwd: root,
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"],
    env: {
      ...process.env,
      APP_PORT: "18084",
      APP_TOKEN: token,
      BIND_ADDRESS: "127.0.0.1",
      DB_PATH: resolve(root, "tmp", `draft-lifecycle-${randomUUID()}.db`),
      ASSISTANT_PROVIDER_CONFIG: resolve(root, "tmp/no-test-provider.json"),
    },
  },
);
let logs = "",
  browser,
  page;
server.stdout.on("data", (data) => {
  logs += data;
});
server.stderr.on("data", (data) => {
  logs += data;
});
async function api(path, body) {
  const response = await fetch(base + "/api" + path, {
    method: body === undefined ? "GET" : "POST",
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw Error(await response.text());
  return response.json().catch(() => undefined);
}
try {
  let ready = false;
  for (let attempt = 0; attempt < 120; attempt++) {
    try {
      if ((await fetch(base)).ok) {
        ready = true;
        break;
      }
    } catch {}
    await new Promise((done) => setTimeout(done, 500));
  }
  if (!ready) throw Error("Disposable draft lifecycle backend did not start");
  expect((await api("/state")).invoices).toHaveLength(0);
  const source = await readFile(
    resolve(root, "backend/src/test/resources/assistant/october-proposed-work.txt"),
    "utf8",
  );
  const plan = await api("/assistant/plan", {
    contract_id: 1,
    source,
    reference_date: "2026-10-25",
  });
  await api(`/assistant/plans/${plan.id}/apply`, { version: plan.version, confirmed: true });
  browser = await chromium.launch({
    channel:
      process.env.E2E_BROWSER_CHANNEL === "bundled"
        ? undefined
        : process.env.E2E_BROWSER_CHANNEL || "chrome",
    headless: true,
  });
  page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto(base);
  await page.getByLabel("Local access token").fill(token);
  await page.getByRole("button", { name: /Unlock workspace/ }).click();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  await page.getByRole("button", { name: /^October 1–30 spans/ }).click();
  await expect(page.getByText("Work saved · create or open its invoice")).toBeVisible();
  await page.getByRole("button", { name: "Create invoice draft", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Pending work · excluded from charges" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Confirm & finalize" })).toBeDisabled();
  expect((await api("/state")).invoices).toHaveLength(1);
  expect((await api("/state")).entries).toHaveLength(20);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.getByRole("button", { name: "Edit invoice draft" }).click();
  await page.getByLabel("Service period end", { exact: true }).fill("2026-10-29");
  await page.getByLabel("Invoice notes").fill("Awaiting actual-hour confirmation");
  await page.getByRole("button", { name: "Save invoice changes" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.getByText("Awaiting actual-hour confirmation", { exact: true })).toBeVisible();
  await expect(page.locator(".invoice-dates")).toContainText("2026-10-01 – 2026-10-29");
  await page.screenshot({ path: resolve(root, "docs/images/invoice-pending-review.png") });
  await page.getByRole("button", { name: "Delete invoice draft" }).click();
  await expect(page.getByRole("heading", { name: "All invoices" })).toBeVisible();
  expect((await api("/state")).invoices).toHaveLength(0);
  expect((await api("/state")).entries).toHaveLength(20);
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  await page.getByRole("button", { name: /^October 1–30 spans/ }).click();
  await page.getByRole("button", { name: "Create invoice draft", exact: true }).click();
  await expect(page.getByRole("heading", { name: "LA-2026-10-002" })).toBeVisible();
  await page.getByRole("button", { name: /All invoices/ }).click();
  await page.getByRole("button", { name: "Edit invoice LA-2026-10-002", exact: true }).click();
  await page.getByLabel("Service period end", { exact: true }).fill("2026-10-28");
  await page.getByLabel("Invoice notes").fill("Edited directly from the invoice list");
  await page.getByRole("button", { name: "Save invoice changes" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(page.getByRole("heading", { name: "All invoices" })).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: "LA-2026-10-002" })).toContainText(
    "2026-10-28",
  );
  await page.reload();
  await page.getByRole("button", { name: /^Invoices\b/ }).click();
  await expect(page.getByRole("row").filter({ hasText: "LA-2026-10-002" })).toContainText(
    "2026-10-28",
  );
  const editedInvoice = (await api("/state")).invoices.find(
    (invoice) => invoice.number === "LA-2026-10-002",
  );
  expect((await api(`/invoices/${editedInvoice.id}`)).invoice.notes).toBe(
    "Edited directly from the invoice list",
  );
  await page.screenshot({ path: resolve(root, "docs/images/invoice-list-actions.png") });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.getByRole("button", { name: "Delete invoice LA-2026-10-002", exact: true }).click();
  await expect(page.getByRole("heading", { name: "All invoices" })).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: "LA-2026-10-002" })).toHaveCount(0);
  await page.reload();
  expect((await api("/state")).invoices).toHaveLength(0);
  expect((await api("/state")).entries).toHaveLength(20);
  await api("/expenses", {
    contract_id: 1,
    expense_date: "2026-10-20",
    description: "Browser expense fixture",
    amount: "5",
    approval_ref: "Approved fixture",
  });
  await page.reload();
  await page.getByRole("button", { name: /^Invoices\b/ }).click();
  await page
    .getByRole("row")
    .filter({ hasText: "Browser expense fixture" })
    .getByRole("button", { name: "Review" })
    .click();
  await page.getByRole("button", { name: "Delete expense" }).click();
  await expect(page.getByText("Browser expense fixture", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Clients & Contracts", exact: true }).click();
  await page.getByRole("button", { name: "Archive client", exact: true }).click();
  await expect(page.getByRole("button", { name: "Restore client", exact: true })).toBeVisible();
  expect((await api("/state")).contracts[0].archived).toBe(true);
  await page.getByRole("button", { name: "Restore client", exact: true }).click();
  await page.getByRole("button", { name: "Archive contract", exact: true }).click();
  await expect(page.getByRole("button", { name: "Restore contract", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Restore contract", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.getByRole("button", { name: "Projects", exact: true }).click();
  await page.getByRole("button", { name: "Archive project", exact: true }).first().click();
  await expect(page.getByRole("button", { name: "Restore project", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Restore project", exact: true }).click();
  await page.getByRole("button", { name: "Edit project", exact: true }).first().click();
  await page.getByLabel("Repository reference").fill("https://example.test/repository");
  await page.getByRole("button", { name: "Save project" }).click();
  await expect(page.getByText("https://example.test/repository", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  await page.getByRole("button", { name: /^October 1–30 spans/ }).click();
  const workBeforeConversationDelete = (await api("/state")).entries;
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page
    .locator("section.panel")
    .filter({
      has: page.getByRole("heading", { name: "Saved conversations", exact: true }),
    })
    .screenshot({ path: resolve(root, "docs/images/conversation-delete.png") });
  await page.getByRole("button", { name: /^Delete conversation:/ }).click();
  await expect(
    page.getByText("Conversation deleted. Work entries and invoices were preserved."),
  ).toBeVisible();
  await expect(page.getByRole("heading", { name: "Saved conversations" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Create invoice draft", exact: true })).toHaveCount(
    0,
  );
  await page.reload();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Tell your work story." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Saved conversations" })).toHaveCount(0);
  expect((await api("/state")).entries).toEqual(workBeforeConversationDelete);
  expect((await api(`/assistant/plans/${plan.id}`)).deleted_at).toBeTruthy();
  expect((await api("/assistant?contract=1")).plans).toHaveLength(0);
  expect(errors).toEqual([]);
  console.log(
    "PASS: applied-work invoice draft, pending-work separation, preview and list editing/deletion, persisted dates/notes after reload, expense deletion, client/contract/project archive and restore, project editing, conversation deletion persisted after reload, mobile layout, and preserved work history.",
  );
} catch (error) {
  if (page) {
    await page.screenshot({ path: resolve(root, "tmp/draft-lifecycle-browser-failure.png") });
    console.log(
      await page.evaluate(() => ({
        width: innerWidth,
        documentWidth: document.documentElement.scrollWidth,
        overflow: [...document.querySelectorAll("body *")]
          .filter((element) => element.getBoundingClientRect().right > innerWidth + 1)
          .slice(0, 20)
          .map((element) => ({
            tag: element.tagName,
            className: element.className,
            right: element.getBoundingClientRect().right,
            width: element.getBoundingClientRect().width,
          })),
      })),
    );
  }
  await writeFile(resolve(root, "tmp/draft-lifecycle-e2e.log"), logs);
  throw error;
} finally {
  await browser?.close();
  server.kill();
}
