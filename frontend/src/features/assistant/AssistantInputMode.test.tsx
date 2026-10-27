import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AssistantInputMode } from "./AssistantInputMode";
import { AssistantPlanPanel } from "./AssistantPlanPanel";
import { useWorkAssistant } from "./useWorkAssistant";

afterEach(cleanup);

it("keeps proposed hours out of actual duration and blocks invoicing until questions are resolved", () => {
  const edit = vi.fn();
  const assistant = {
    plan: {
      id: "allocation",
      source: "table",
      version: 1,
      status: "proposed",
      actions: [
        {
          type: "log_work",
          description: "Repository preparation",
          proposed_hours: "5",
          work_state: "proposed",
          work_date: "",
          seconds: 0,
          draft: 1,
          billable: 1,
        },
        {
          type: "save_note",
          description: "Future testing",
          proposed_hours: "16",
          work_state: "planned",
        },
        {
          type: "save_note",
          description: "Resolve partial-week billing",
          requires_resolution: true,
          resolved: false,
        },
        { type: "draft_invoice", description: "Reviewed work only", skip: false },
      ],
      results: [],
    },
    source: "table",
    busy: false,
    confirmed: true,
    projects: [],
    provider: "",
    locked: false,
    edit,
    setConfirmed: vi.fn(),
    run: vi.fn(),
    setPlan: vi.fn(),
    reload: vi.fn(),
  } as unknown as ReturnType<typeof useWorkAssistant>;
  render(<AssistantPlanPanel assistant={assistant} onRefresh={vi.fn()} onInvoice={vi.fn()} />);
  expect(screen.getByText("21 proposed hours, including 16 planned hours.")).toBeVisible();
  expect(screen.getByLabelText("Actual duration (hours)")).toHaveValue(null);
  expect(screen.getByRole("button", { name: "Apply reviewed plan" })).toBeDisabled();
  expect(
    screen.getAllByRole("button", { name: "Add another actual work date for this task" }),
  ).toHaveLength(1);
  fireEvent.click(screen.getByLabelText(/I resolved the billing questions/));
  expect(edit).toHaveBeenCalledWith(2, { resolved: true });
  fireEvent.click(screen.getByLabelText("Prepare an invoice from the included confirmed work"));
  expect(edit).toHaveBeenCalledWith(3, { skip: true });
});

it("explains long-message recovery using the configured limit without discarding text", () => {
  const source = Array.from({ length: 40 }, (_, index) => `Reference #${index}`).join("\n");
  const change = vi.fn();
  render(
    <AssistantInputMode
      history={{
        draft: { source },
        plans: [],
        providers: [],
        limits: { actions: 12, sourceCharacters: 10000, history: 50 },
      }}
      source={source}
      mode="auto"
      onChange={change}
      busy={false}
    />,
  );
  expect(screen.getByText(/40 nonempty lines/)).toBeInTheDocument();
  expect(screen.getByText(/up to 12 actions/)).toBeInTheDocument();
  expect(screen.getByText(/No configured provider/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Local input style"), { target: { value: "narrative" } });
  expect(change).toHaveBeenCalledWith("narrative");
});

it("makes a notes-only plan visibly different from an invoice and requires confirmation", () => {
  const assistant = {
    plan: {
      id: "note",
      source: "Do not bill future work",
      version: 1,
      status: "proposed",
      actions: [
        {
          type: "save_note",
          description: "Retain original notes",
          source_text: "Do not bill future work",
        },
      ],
      results: [],
    },
    source: "Do not bill future work",
    busy: false,
    confirmed: false,
    projects: [],
    provider: "",
    locked: false,
    setConfirmed: vi.fn(),
    run: vi.fn(),
    setPlan: vi.fn(),
    reload: vi.fn(),
    edit: vi.fn(),
  } as unknown as ReturnType<typeof useWorkAssistant>;
  render(<AssistantPlanPanel assistant={assistant} onRefresh={vi.fn()} onInvoice={vi.fn()} />);
  expect(screen.getByText(/No time entries or invoice will be created/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Save narrative notes" })).toBeDisabled();
  expect(screen.queryByLabelText("Actual duration (hours)")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("Correction message")).not.toBeInTheDocument();
});
