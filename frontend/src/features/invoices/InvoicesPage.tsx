import { download, request } from "@/lib/api/client";
import { usd } from "@/lib/money";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { EntryTable } from "@/components/tables/EntryTable";
import { InvoiceTable } from "@/components/tables/InvoiceTable";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ArrowUpRight, Check, Download, Plus } from "lucide-react";
import { useState } from "react";
import type { Invoice, Preview } from "@/types";
import { InvoiceEditDialog } from "./InvoiceEditDialog";
export function InvoicesPage() {
  const [editingInvoice, setEditingInvoice] = useState<Preview>();
  const { selected, setSelected, data, act, mutateInvoice, setModal, invoices, openInvoice, cid } =
    useWorkspaceData();
  const { entryForm, setPage, setAssistantPlanId } = useWorkspaceData();
  function editFromList(id: number) {
    act(async () => {
      setEditingInvoice(await request<Preview>(`/invoices/${id}`));
    }, "Invoice draft ready to edit");
  }
  function deleteFromList(invoice: Invoice) {
    if (
      !confirm(
        `Delete draft invoice ${invoice.number}? Its number and audit history will be retained. Work entries and expenses will remain.`,
      )
    )
      return;
    act(async () => {
      const current = await request<Preview>(`/invoices/${invoice.id}`);
      await request(
        `/invoices/${invoice.id}?confirmed=true&expected_token=${encodeURIComponent(current.edit_token ?? "")}`,
        undefined,
        "DELETE",
      );
    }, "Invoice draft deleted");
  }
  return (
    <>
      {editingInvoice && (
        <InvoiceEditDialog
          preview={editingInvoice}
          onClose={() => setEditingInvoice(undefined)}
          onSaved={() => setEditingInvoice(undefined)}
        />
      )}
      {selected ? (
        <>
          <div className="section-head">
            <button className="text-btn" onClick={() => setSelected(undefined)}>
              ← All invoices
            </button>
            <Badge>{selected.invoice.state}</Badge>
          </div>
          <div className="dashboard-grid">
            <section className="panel invoice-preview">
              <div className="invoice-mast">
                <div>
                  <div className="eyebrow">
                    {selected.invoice.snapshot ? "INVOICE" : "DRAFT INVOICE"}
                  </div>
                  <h2>{selected.invoice.number}</h2>
                  <p>{selected.contract.contractor}</p>
                  <p className="muted small">{selected.contract.contractor_address}</p>
                </div>
                <div>
                  <strong>{selected.client.name}</strong>
                  <p className="small">{selected.client.email}</p>
                  <p className="small">{selected.client.address}</p>
                </div>
              </div>
              <div className="invoice-dates">
                <div>
                  <small>INVOICE DATE</small>
                  {selected.invoice.invoice_date}
                </div>
                <div>
                  <small>SERVICE PERIOD</small>
                  {selected.invoice.period_start} – {selected.invoice.period_end}
                </div>
              </div>
              <EntryTable
                entries={selected.entries}
                projects={data.projects}
                onEdit={entryForm}
                readonly={!!selected.invoice.snapshot}
              />
              {!!selected.pendingWork?.length && (
                <section className="validation pending-work">
                  <h3>Pending work · excluded from charges</h3>
                  <p>
                    Confirm actual work in Work Assistant. Proposed hours are planning figures, not
                    billable time.
                  </p>
                  <details>
                    <summary>Review {selected.pendingWork.length} pending tasks</summary>
                    <ul>
                      {selected.pendingWork.map((work, index) => (
                        <li key={index}>
                          {work.description}
                          {work.proposed_hours
                            ? ` — ${work.proposed_hours} proposed hours`
                            : " — actual hours needed"}
                        </li>
                      ))}
                    </ul>
                  </details>
                </section>
              )}
              {!!selected.plannedWork?.length && (
                <details>
                  <summary>
                    {selected.plannedWork.length} planned tasks · excluded from charges
                  </summary>
                  <ul>
                    {selected.plannedWork.map((work, index) => (
                      <li key={index}>{work.description}</li>
                    ))}
                  </ul>
                </details>
              )}
              {!!selected.assistantPlans?.length && (
                <Button
                  secondary
                  onClick={() => {
                    setAssistantPlanId(selected.assistantPlans![0]);
                    setPage("Work Assistant");
                  }}
                >
                  Review linked work in Work Assistant
                </Button>
              )}
              {selected.invoice.notes && <p className="small">{selected.invoice.notes}</p>}
              {selected.expenses.map((e) => (
                <div className="total-row" key={e.id}>
                  <span>
                    {e.description} · approved {e.approval_ref}
                  </span>
                  <strong>{usd(e.amount)}</strong>
                </div>
              ))}
              <div className="totals">
                <div className="total-row">
                  <span>Billable hours</span>
                  <strong>{Number(selected.hours).toFixed(4)}</strong>
                </div>
                <div className="total-row">
                  <span>Professional services · {usd(selected.contract.rate)}/hr</span>
                  <strong>{usd(selected.subtotal)}</strong>
                </div>
                <div className="total-row">
                  <span>Approved expenses</span>
                  <strong>{usd(selected.expenseTotal)}</strong>
                </div>
                {Number(selected.invoice.adjustment) > 0 && (
                  <div className="total-row">
                    <span>Approved adjustment · {selected.invoice.adjustment_reason}</span>
                    <strong>{usd(selected.invoice.adjustment)}</strong>
                  </div>
                )}
                <div className="total-row grand">
                  <span>Total due</span>
                  <strong>{usd(selected.total)}</strong>
                </div>
              </div>
              <p className="muted small">
                Approved invoices payable within {selected.contract.payment_days} days of receipt.
                Dispute period: {selected.contract.dispute_days} days.
              </p>
              <p className="small">{selected.contract.payment_instructions}</p>
            </section>
            <section className="panel">
              <h2>Review & submission</h2>
              {selected.warnings.length ? (
                <div className="validation">
                  <strong>Needs your attention</strong>
                  <ul>
                    {selected.warnings.map((w) => (
                      <li key={w}>{w}</li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="notice">
                  <Check size={16} />
                  Validation passed
                </p>
              )}
              <div className="stack">
                {["pdf", "xlsx", "csv"].map((f) => (
                  <Button
                    secondary
                    key={f}
                    onClick={() =>
                      act(
                        () =>
                          download(
                            `/invoices/${selected.invoice.id}/export/${f}`,
                            selected.invoice.number + "." + f,
                          ),
                        "Export downloaded",
                      )
                    }
                  >
                    <Download size={16} />
                    {f === "xlsx" ? "Excel" : f.toUpperCase()} download
                  </Button>
                ))}
                <Button
                  secondary
                  onClick={() =>
                    act(
                      () => download(`/invoices/${selected.invoice.id}/export/pdf`, "", true),
                      "PDF opened",
                    )
                  }
                >
                  Preview PDF <ArrowUpRight size={16} />
                </Button>
                {!selected.invoice.snapshot ? (
                  <>
                    <Button secondary onClick={() => setModal("edit-invoice")}>
                      Edit invoice draft
                    </Button>
                    <Button
                      secondary
                      onClick={() => {
                        if (
                          confirm(
                            "Delete this draft invoice? Its number and audit history will be retained. Work entries and expenses will remain.",
                          )
                        )
                          act(async () => {
                            await request(
                              `/invoices/${selected.invoice.id}?confirmed=true&expected_token=${selected.edit_token}`,
                              undefined,
                              "DELETE",
                            );
                            setSelected(undefined);
                          }, "Invoice draft deleted");
                      }}
                    >
                      Delete invoice draft
                    </Button>
                    <Button
                      disabled={selected.warnings.length > 0}
                      onClick={() => {
                        if (
                          confirm(
                            "Finalize this invoice? Its contents will be locked. Confirm you reviewed all work, expenses and approvals.",
                          )
                        )
                          act(
                            () =>
                              mutateInvoice("/finalize", {
                                confirmed: true,
                              }),
                            "Invoice finalized",
                          );
                      }}
                    >
                      Confirm & finalize
                    </Button>
                  </>
                ) : (
                  <>
                    {!["Replaced", "Withdrawn"].includes(selected.invoice.state) && (
                      <>
                        <Button secondary onClick={() => setModal("email")}>
                          Prepare submission email
                        </Button>
                        {!selected.invoice.submitted ? (
                          <Button onClick={() => setModal("sent")}>Mark as sent</Button>
                        ) : (
                          <>
                            <Button
                              secondary
                              onClick={() =>
                                act(
                                  () =>
                                    mutateInvoice("/status", {
                                      state: "Approved",
                                    }),
                                  "Approval recorded",
                                )
                              }
                            >
                              Mark approved
                            </Button>
                            <Button secondary onClick={() => setModal("dispute")}>
                              Record dispute
                            </Button>
                            <Button onClick={() => setModal("payment")}>Record payment</Button>
                          </>
                        )}
                        <Button secondary onClick={() => setModal("withdraw")}>
                          Withdraw unpaid invoice for correction
                        </Button>
                      </>
                    )}
                    {selected.invoice.state === "Withdrawn" && (
                      <>
                        <p className="callout">
                          Original snapshot preserved. Correct the source entries under Time
                          Tracker, then create a replacement. Notify accounting manually if this
                          invoice was already submitted.
                        </p>
                        <Button secondary onClick={() => setModal("replacement")}>
                          Create documented replacement
                        </Button>
                      </>
                    )}
                  </>
                )}
              </div>
              <p className="muted small">
                Invoices are sent manually. Download the PDF, attach it to your email, then record
                the actual submission date.
              </p>
            </section>
          </div>
        </>
      ) : (
        <>
          <div className="section-head">
            <h2>All invoices</h2>
            <div className="actions">
              <Button secondary onClick={() => setModal("expense:0")}>
                Add expense
              </Button>
              <Button onClick={() => setModal("invoice")}>
                <Plus size={16} />
                Create invoice
              </Button>
            </div>
          </div>
          <section className="panel">
            <InvoiceTable
              invoices={invoices}
              onOpen={openInvoice}
              onEdit={editFromList}
              onDelete={deleteFromList}
            />
          </section>
          <section className="panel">
            <h2>Reimbursable expenses</h2>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Description</th>
                    <th>Amount</th>
                    <th>Prior approval reference</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.expenses
                    .filter((e) => e.contract_id === cid)
                    .map((e) => (
                      <tr key={e.id}>
                        <td>{e.expense_date}</td>
                        <td>{e.description}</td>
                        <td>{usd(e.amount)}</td>
                        <td>{e.approval_ref || "Unapproved — blocks finalization"}</td>
                        <td>
                          <button className="text-btn" onClick={() => setModal("expense:" + e.id)}>
                            Review
                          </button>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </>
  );
}
