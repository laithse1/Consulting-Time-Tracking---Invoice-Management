import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import WorkAssistant from "./WorkAssistant";
import type { State } from "@/types";
const data = {
  contracts: [{ id: 1, client_id: 1 }],
  projects: [{ id: 1, client_id: 1, name: "Audiom" }],
} as State;
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it("restores notes and requires an explicit plan confirmation before applying", async () => {
  const plan = {
    id: "plan",
    source: "Today 2h testing Audiom",
    status: "proposed",
    version: 1,
    created: "2026-09-15T10:00:00Z",
    engine: "local-rules",
    actions: [
      {
        type: "log_work",
        description: "Today 2h testing Audiom",
        work_date: "2026-09-15",
        seconds: 7200,
        project_id: 1,
        category: "Testing and Debugging",
        billable: 1,
        draft: 0,
      },
    ],
    results: [],
  };
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => ({
    ok: true,
    headers: new Headers(),
    json: async () =>
      url.endsWith("/plan")
        ? plan
        : options?.method === "PUT"
          ? { ...plan, version: 2 }
          : url.endsWith("/apply")
            ? {
                ...plan,
                status: "applied",
                results: [
                  {
                    type: "log_work",
                    entry: { id: 8, seconds: 7200, draft: 0 },
                  },
                ],
              }
            : url.includes("?contract")
              ? { draft: { source: plan.source }, plans: [], providers: [] }
              : undefined,
  }));
  vi.stubGlobal("fetch", fetcher);
  render(<WorkAssistant data={data} cid={1} onRefresh={vi.fn()} onInvoice={vi.fn()} />);
  await waitFor(() => expect(screen.getByLabelText("Work description")).toHaveValue(plan.source));
  fireEvent.click(screen.getByRole("button", { name: "Review action plan" }));
  const apply = await screen.findByRole("button", {
    name: "Apply reviewed plan",
  });
  expect(apply).toBeDisabled();
  const proposedRequest = fetcher.mock.calls.find(([url]) => url.endsWith("/plan"));
  expect(JSON.parse(String(proposedRequest?.[1]?.body))).toMatchObject({ prepare_invoice: true });
  expect(fetcher.mock.calls.some(([url]) => url.endsWith("/apply"))).toBe(false);
  fireEvent.click(screen.getByLabelText("Confirm reviewed action plan"));
  fireEvent.click(apply);
  expect(await screen.findByText(/Entry #8 saved/)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Apply reviewed plan" })).not.toBeInTheDocument();
});
it("keeps unsaved notes visible when database saving fails", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (_url: string, options?: RequestInit) =>
      options?.method === "POST"
        ? { ok: false, json: async () => ({ message: "Database unavailable" }) }
        : {
            ok: true,
            headers: new Headers(),
            json: async () => ({
              draft: { source: "Saved wording" },
              plans: [],
              providers: [],
            }),
          },
    ),
  );
  render(<WorkAssistant data={data} cid={1} onRefresh={vi.fn()} onInvoice={vi.fn()} />);
  await waitFor(() =>
    expect(screen.getByLabelText("Work description")).toHaveValue("Saved wording"),
  );
  fireEvent.change(screen.getByLabelText("Work description"), {
    target: { value: "New unfinished work notes" },
  });
  expect(await screen.findByText("Save failed: Database unavailable")).toBeInTheDocument();
  expect(screen.getByLabelText("Work description")).toHaveValue("New unfinished work notes");
});
