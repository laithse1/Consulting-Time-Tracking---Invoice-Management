import { FileText, ArrowUpRight } from "lucide-react";
import type { Invoice } from "../../types";
import { usd } from "../../api";
import { Badge } from "../ui/Badge";
export function InvoiceTable({
  invoices,
  onOpen,
}: {
  invoices: Invoice[];
  onOpen: (id: number) => void;
}) {
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
            <th />
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
                <button className="text-btn" onClick={() => onOpen(i.id)}>
                  Review <ArrowUpRight size={14} />
                </button>
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
