import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { InvoiceTable } from "./InvoiceTable";
import { BusyContext } from "../ui/BusyContext";
import type { Invoice } from "@/types";

afterEach(cleanup);
const draft = {
  id: 1,
  number: "TEST-001",
  state: "Draft",
  snapshot: null,
  period_start: "2026-10-01",
  period_end: "2026-10-30",
  paid: "0",
} as Invoice;

describe("invoice row actions", () => {
  it("allows draft changes while keeping issued invoices on the review workflow", () => {
    const onEdit = vi.fn(),
      onDelete = vi.fn(),
      onOpen = vi.fn();
    render(
      <InvoiceTable
        invoices={[
          draft,
          { ...draft, id: 2, number: "TEST-002", state: "Finalized", snapshot: "{}" },
        ]}
        onOpen={onOpen}
        onEdit={onEdit}
        onDelete={onDelete}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Edit invoice TEST-001" }));
    fireEvent.click(screen.getByRole("button", { name: "Delete invoice TEST-001" }));
    expect(onEdit).toHaveBeenCalledWith(1);
    expect(onDelete).toHaveBeenCalledWith(draft);
    expect(screen.queryByRole("button", { name: "Edit invoice TEST-002" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Delete invoice TEST-002" })).toBeNull();
    expect(screen.getAllByRole("button", { name: "Review" })).toHaveLength(2);
  });

  it("blocks row actions during another workspace operation", () => {
    const onEdit = vi.fn();
    render(
      <BusyContext.Provider value={true}>
        <InvoiceTable invoices={[draft]} onOpen={vi.fn()} onEdit={onEdit} onDelete={vi.fn()} />
      </BusyContext.Provider>,
    );
    for (const button of screen.getAllByRole("button")) expect(button).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Edit invoice TEST-001" }));
    expect(onEdit).not.toHaveBeenCalled();
  });
});
