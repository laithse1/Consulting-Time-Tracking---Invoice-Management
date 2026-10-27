import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AssistantInvoiceDraft } from "./AssistantInvoiceDraft";
import type { Plan } from "./types";

afterEach(cleanup);

it("offers invoice creation for work that was already applied", () => {
  const create = vi.fn();
  render(
    <AssistantInvoiceDraft
      plan={{ ...plan, status: "applied" }}
      busy={false}
      onCreate={create}
      onOpen={vi.fn()}
    />,
  );
  expect(screen.getByText("Work saved · create or open its invoice")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Create invoice draft" }));
  expect(create).toHaveBeenCalledWith("2026-10-01", "2026-10-30");
});
const plan = {
  id: "review",
  status: "proposed",
  reference_date: "2026-10-25",
  actions: [
    {
      type: "draft_invoice",
      description: "Invoice",
      period_start: "2026-10-01",
      period_end: "2026-10-30",
      skip: true,
    },
  ],
} as Plan;

it("makes invoice creation explicit and preserves the exact editable service period", () => {
  const create = vi.fn();
  render(<AssistantInvoiceDraft plan={plan} busy={false} onCreate={create} onOpen={vi.fn()} />);
  expect(screen.getByText("Saved plan · invoice not created yet")).toBeVisible();
  expect(screen.getByLabelText("Draft service period end")).toHaveValue("2026-10-30");
  fireEvent.click(screen.getByRole("button", { name: "Create invoice draft" }));
  expect(create).toHaveBeenCalledWith("2026-10-01", "2026-10-30");
  fireEvent.change(screen.getByLabelText("Draft service period end"), {
    target: { value: "2026-09-30" },
  });
  expect(screen.getByRole("button", { name: "Create invoice draft" })).toBeDisabled();
});

it("opens an existing draft instead of creating a duplicate", () => {
  const open = vi.fn();
  render(
    <AssistantInvoiceDraft
      plan={{ ...plan, actions: [{ ...plan.actions[0], _invoice_id: 42 }] }}
      busy={false}
      onCreate={vi.fn()}
      onOpen={open}
    />,
  );
  expect(screen.queryByRole("button", { name: "Create invoice draft" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Open invoice draft" }));
  expect(open).toHaveBeenCalledWith(42);
});
