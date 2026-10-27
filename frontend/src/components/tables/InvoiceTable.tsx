import { FileText, ArrowUpRight } from "lucide-react";
import type { Invoice } from "../../types";
import { usd } from "../../api";
import { Badge } from "../ui/Badge";
import { useContext } from "react";
import { BusyContext } from "../ui/BusyContext";
export function InvoiceTable({
  invoices,
  onOpen,
  onEdit,
  onDelete,
}: {
  invoices: Invoice[];
  onOpen: (id: number) => void;
  onEdit?: (id: number) => void;
  onDelete?: (invoice: Invoice) => void;
}) {
  const busy = useContext(BusyContext);
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Invoice</th>
            <th>Service period</th>
            <th>Amount</th>
            <th>Paid</th>
            <th>Balance</th>
            <th>Due</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {invoices.map((i) => (
            <tr key={i.id}>
              <td>
                <strong>{i.number}</strong>
                <small>{i.client_name}</small>
                {i.submitted && <small>Submitted {i.submitted}</small>}
              </td>
              <td>
                {i.period_start} – {i.period_end}
              </td>
              <td>{i.snapshot ? usd(i.total) : "Draft"}</td>
              <td>{usd(i.paid)}</td>
              <td>{i.snapshot ? usd(i.balance) : "—"}</td>
              <td>{i.due || "Not submitted"}</td>
              <td>
                <Badge>{i.state}</Badge>
                {i.workflow_state && i.workflow_state !== i.state && (
                  <small>Review: {i.workflow_state}</small>
                )}
              </td>
              <td>
                <div className="invoice-row-actions">
                  <button className="text-btn" disabled={busy} onClick={() => onOpen(i.id)}>
                    Review <ArrowUpRight size={14} />
                  </button>
                  {!i.snapshot && i.state === "Draft" && onEdit && (
                    <button
                      className="text-btn"
                      disabled={busy}
                      aria-label={`Edit invoice ${i.number}`}
                      onClick={() => onEdit(i.id)}
                    >
                      Edit
                    </button>
                  )}
                  {!i.snapshot && i.state === "Draft" && onDelete && (
                    <button
                      className="text-btn"
                      disabled={busy}
                      aria-label={`Delete invoice ${i.number}`}
                      onClick={() => onDelete(i)}
                    >
                      Delete
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!invoices.length && (
        <div className="empty">
          <FileText size={28} />
          <h3>Your next invoice starts here</h3>
          <p className="muted">Create an invoice from confirmed work for the selected period.</p>
        </div>
      )}
    </div>
  );
}
