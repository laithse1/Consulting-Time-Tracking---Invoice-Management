import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup, fireEvent, act } from "@testing-library/react";
import App, { Badge } from "./App";
import { weekStart, monthEnd } from "@/lib/dates";
import { duration } from "@/lib/time";
afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.restoreAllMocks();
});
describe("local workspace", () => {
  it("stays locked when a previously authorized request completes late", async () => {
    const state = {
      clients: [{ id: 1, name: "Test client", email: "fixture@example.test" }],
      contracts: [
        {
          id: 1,
          client_id: 1,
          contractor: "Test contractor",
          rate: "75",
          minimum_hours: 10,
          maximum_hours: 16,
          week_start: 1,
          start_date: "2026-09-01",
        },
      ],
      entries: [],
      projects: [],
      invoices: [],
      expenses: [],
      historical: [],
      payments: [],
      audit: [],
      timer: [],
    };
    let count = 0;
    let complete: ((value: unknown) => void) | undefined;
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.endsWith("/state")) {
          count++;
          if (count === 2)
            return new Promise((resolve) => {
              complete = resolve;
            });
          return Promise.resolve({
            ok: true,
            headers: new Headers(),
            json: async () => state,
          });
        }
        return Promise.resolve({
          ok: true,
          headers: new Headers(),
          json: async () => [],
        });
      }),
    );
    sessionStorage.setItem("token", "test-local-token");
    render(<App />);
    expect(await screen.findByText("A clear view of your work.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Reporting month"), {
      target: { value: "2020-01" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Lock workspace" }));
    await act(async () => {
      complete?.({ ok: true, headers: new Headers(), json: async () => state });
    });
    expect(screen.getByLabelText("Local access token")).toBeInTheDocument();
    expect(screen.queryByText("Outstanding receivables")).not.toBeInTheDocument();
  });
  it("requires a token before showing financial information", () => {
    render(<App />);
    expect(screen.getByLabelText("Local access token")).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: /Unlock workspace/ })).toBeInTheDocument();
    expect(screen.queryByText("Outstanding receivables")).not.toBeInTheDocument();
  });
  it("shows authentication failures without unlocking", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({ message: "Unauthorized" }),
      }),
    );
    render(<App />);
    fireEvent.change(screen.getByLabelText("Local access token"), {
      target: { value: "wrong-token" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Unlock workspace/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Unauthorized");
    expect(screen.queryByText("Outstanding receivables")).not.toBeInTheDocument();
  });
  it("handles month boundaries and configurable week starts", () => {
    expect(weekStart("2026-09-01", 1)).toBe("2026-08-31");
    expect(weekStart("2026-09-01", 7)).toBe("2026-08-30");
    expect(monthEnd("2028-02")).toBe(29);
    expect(duration(90000)).toBe("25:00:00");
  });
  it("distinguishes payment and approval-required states", () => {
    render(
      <>
        <Badge>Paid</Badge>
        <Badge>Above maximum — written approval required</Badge>
      </>,
    );
    expect(screen.getByText("Paid")).toHaveClass("green");
    expect(screen.getByText(/written approval required/)).toHaveClass("red");
  });
});
