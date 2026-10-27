import { useState } from "react";
import type { Plan } from "./types";

export function AssistantInvoiceDraft({
  plan,
  busy,
  onCreate,
  onOpen,
}: {
  plan: Plan;
  busy: boolean;
  onCreate: (from: string, to: string) => void;
  onOpen: (id: number) => void;
}) {
  const invoice = plan.actions.find((action) => action.type === "draft_invoice");
  const month = (plan.reference_date || new Date().toISOString().slice(0, 10)).slice(0, 7);
  const [from, setFrom] = useState(invoice?.period_start || `${month}-01`);
  const [to, setTo] = useState(
    invoice?.period_end ||
      `${month}-${new Date(Number(month.slice(0, 4)), Number(month.slice(5)), 0).getDate()}`,
  );
  if (invoice?._invoice_id)
    return (
      <div className="notice assistant-intake-notice">
        <strong>Invoice draft created</strong>
        <p>
          Its billable totals include only eligible confirmed work. Pending and planned work remain
          separate.
        </p>
        <button
          className="btn secondary"
          disabled={busy}
          onClick={() => onOpen(invoice._invoice_id!)}
        >
          Open invoice draft
        </button>
      </div>
    );
  if (!["proposed", "applied"].includes(plan.status)) return null;
  return (
    <section className="notice assistant-intake-notice" aria-label="Invoice draft preparation">
      <strong>
        {plan.status === "applied"
          ? "Work saved · create or open its invoice"
          : "Saved plan · invoice not created yet"}
      </strong>
      <p>
        Create an editable invoice draft now. Pending work will appear for review without becoming a
        charge. Confirm actual work and resolve billing questions before finalizing. An existing
        invoice from this conversation is reused.
      </p>
      <div className="form-grid">
        <label className="field">
          Draft service period start
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            disabled={busy}
          />
        </label>
        <label className="field">
          Draft service period end
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} disabled={busy} />
        </label>
      </div>
      <button
        className="btn"
        disabled={busy || !from || !to || to < from}
        onClick={() => onCreate(from, to)}
      >
        Create invoice draft
      </button>
    </section>
  );
}
