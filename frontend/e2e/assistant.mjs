import { chromium, expect } from "@playwright/test";
import { resolve } from "node:path";
import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
const base = process.env.E2E_BASE_URL || "http://127.0.0.1:18082";
const token = "e2e-isolated-test-token-not-a-real-secret";
const root = resolve("..");
await mkdir(resolve(root, "tmp"), { recursive: true });
const java = process.env.JAVA_HOME
  ? resolve(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java")
  : "java";
const server = process.env.E2E_BASE_URL
  ? undefined
  : spawn(
      java,
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
          APP_PORT: "18082",
          BIND_ADDRESS: "127.0.0.1",
          APP_TOKEN: token,
          DB_PATH: resolve(root, "tmp", `assistant-e2e-${randomUUID()}.db`),
          ASSISTANT_PROVIDER_CONFIG: resolve(root, "tmp", "no-test-provider.json"),
          APP_ALLOWED_ORIGINS: "https://example.test",
        },
      },
    );
let logs = "";
server?.stdout.on("data", (data) => {
  logs += data;
});
server?.stderr.on("data", (data) => {
  logs += data;
});
async function api(path, body) {
  const response = await fetch(base + "/api" + path, {
    method: body === undefined ? "GET" : "POST",
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw Error(path + ": " + (await response.text()));
  return response.json().catch(() => undefined);
}
let browser;
try {
  browser = await chromium.launch({
    channel:
      process.env.E2E_BROWSER_CHANNEL === "bundled"
        ? undefined
        : process.env.E2E_BROWSER_CHANNEL || "chrome",
    headless: true,
  });
  let ready = false;
  for (let i = 0; i < 60; i++) {
    try {
      if ((await fetch(base)).ok) {
        ready = true;
        break;
      }
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  if (!ready) throw Error("Isolated assistant test backend did not start");
  // Only run against an explicitly isolated fresh instance with the test credential.
  expect((await api("/state")).entries).toHaveLength(7);
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(base);
  await page.getByLabel("Local access token").fill(token);
  await page.getByRole("button", { name: /Unlock workspace/ }).click();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  const source =
    "2026-09-15: Spent 2h refactoring geospatial-mcp-server, PR #42.\n2026-09-16: Spent 30m documenting Audiom.\nDraft invoice September 2026\nGenerate report September 2026";
  await page.getByLabel("Work description", { exact: true }).fill(source);
  await expect.poll(async () => (await api("/assistant?contract=1")).draft.source).toBe(source);
  await page.getByRole("button", { name: "Review action plan" }).click();
  await expect(page.getByText("4 proposed actions")).toBeVisible();
  await expect(page.getByRole("button", { name: "Apply reviewed plan" })).toBeDisabled();
  expect((await api("/state")).entries).toHaveLength(7);
  await page.screenshot({ path: resolve("../docs/work-assistant.png"), fullPage: true });
  await page.getByLabel("Confirm reviewed action plan").check();
  await page.getByRole("button", { name: "Apply reviewed plan" }).click();
  await expect(page.getByText("Plan applied. Saved records are shown below.")).toBeVisible();
  await expect(page.getByText(/2.50 actual hours/)).toBeVisible();
  const state = await api("/state");
  expect(state.entries).toHaveLength(9);
  expect(state.invoices).toHaveLength(1);
  expect(state.invoices[0].state).toBe("Draft");
  expect(Number(state.invoices[0].total)).toBe(0); // Draft totals calculated by preview, frozen at finalization.
  const history = await api("/assistant?contract=1");
  const plan = history.plans[0];
  expect(Number(plan.results[3].report.servicesValue)).toBe(187.5);
  await api(`/assistant/plans/${plan.id}/apply`, { version: 1, confirmed: true });
  expect((await api("/state")).entries).toHaveLength(9);
  await page.reload();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  await expect(page.getByLabel("Work description", { exact: true })).toHaveValue(source);
  await page.getByRole("button", { name: /2026-09-15: Spent 2h/ }).click();
  await expect(page.getByText("Plan applied. Saved records are shown below.")).toBeVisible();
  await page.getByRole("button", { name: "Review invoice" }).click();
  await expect(page.getByRole("heading", { name: state.invoices[0].number })).toBeVisible();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  await page.getByRole("button", { name: /2026-09-15: Spent 2h/ }).click();
  await page.getByRole("button", { name: "Revise saved draft" }).click();
  await page.getByLabel("Correction message").fill("Task 1 duration: 90m");
  await page.getByRole("button", { name: "Propose correction" }).click();
  await expect(page.getByText(/Updated 1 task/)).toBeVisible();
  expect((await api("/state")).entries).toHaveLength(9);
  const firstTask = page.locator(".assistant-action").first();
  await expect(firstTask.getByLabel("Actual duration (hours)")).toHaveValue("1.5");
  await firstTask.getByLabel(/This is completed work/).check();
  await page.getByLabel("Confirm reviewed action plan").check();
  await page.getByRole("button", { name: "Apply reviewed plan" }).click();
  await expect(page.getByText("Plan applied. Saved records are shown below.")).toBeVisible();
  expect((await api("/state")).entries).toHaveLength(9);
  expect((await api("/state")).invoices).toHaveLength(1);
  await expect(page.getByText(/2.00 billable hours/)).toBeVisible();
  await page
    .locator(".assistant-conversation")
    .screenshot({ path: resolve("../docs/images/assistant-corrections.png") });
  await page.reload();
  await page.getByRole("button", { name: "Work Assistant", exact: true }).click();
  await page.getByRole("button", { name: /2026-09-15: Spent 2h/ }).click();
  await expect(page.getByText(/Task 1 duration: 90m/).last()).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Work description", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(errors).toEqual([]);
  const preflight = await fetch(base + "/api/state", {
    method: "OPTIONS",
    headers: {
      Origin: "https://example.test",
      "Access-Control-Request-Method": "GET",
      "Access-Control-Request-Headers": "authorization",
    },
  });
  expect(preflight.status).toBe(204);
  expect(preflight.headers.get("access-control-allow-origin")).toBe("https://example.test");
  const denied = await fetch(base + "/api/state", {
    headers: { Origin: "https://untrusted.test", Authorization: "Bearer " + token },
  });
  expect(denied.status).toBe(403);
  for (const command of [
    "Start timer for testing Audiom",
    "Pause timer",
    "Resume timer",
    "Stop timer",
  ]) {
    const proposed = await api("/assistant/plan", {
      contract_id: 1,
      source: command,
      reference_date: "2026-09-15",
    });
    await api(`/assistant/plans/${proposed.id}/apply`, {
      version: proposed.version,
      confirmed: true,
    });
  }
  const afterTimer = await api("/state");
  expect(afterTimer.timer).toHaveLength(0);
  expect(afterTimer.entries).toHaveLength(10);
  expect(afterTimer.entries.at(-1).draft).toBe(1);
  console.log(
    "Assistant browser workflow passed: local parsing, autosave, atomic apply, invoice preview, saved corrections, draft invoice reuse, exact reports, retry safety, reload, mobile, and CORS.",
  );
} catch (error) {
  await writeFile(resolve(root, "tmp", "assistant-e2e-server.log"), logs);
  throw error;
} finally {
  await browser?.close();
  server?.kill();
}
