import { useWorkspaceData } from "@/app/WorkspaceContext";
import { request } from "@/lib/api/client";
import { formData } from "@/lib/forms";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import type { Preview } from "@/types";

export function InvoiceEditDialog({
  preview,
  onClose,
  onSaved,
}: {
  preview?: Preview;
  onClose?: () => void;
  onSaved?: (updated: Preview) => void;
} = {}) {
  const { selected, setSelected, setModal, act } = useWorkspaceData();
  const current = preview ?? selected;
  if (!current) return null;
  const invoice = current.invoice;
  return (
    <Modal title="Edit invoice draft" onClose={onClose ?? (() => setModal(""))}>
      <form
        onSubmit={(event) => {
          const values = formData(event);
          act(async () => {
            const updated = await request<Preview>(
              `/invoices/${invoice.id}`,
              { ...values, expected_token: current.edit_token },
              "PUT",
            );
            if (onSaved) onSaved(updated);
            else setSelected(updated);
          }, "Invoice draft updated");
        }}
      >
        <p className="small muted">
          Invoice number {invoice.number} stays reserved. Billable work follows confirmed entries in
          the selected service period.
        </p>
        <div className="form-grid">
          <Input
            label="Service period start"
            name="period_start"
            type="date"
            value={invoice.period_start}
            required
          />
          <Input
            label="Service period end"
            name="period_end"
            type="date"
            value={invoice.period_end}
            required
          />
          <Input
            label="Invoice date"
            name="invoice_date"
            type="date"
            value={invoice.invoice_date}
            required
          />
        </div>
        <label className="field">
          Presentation
          <select name="presentation" defaultValue={invoice.presentation}>
            <option value="itemized">Itemized</option>
            <option value="grouped">Grouped with dated work detail</option>
          </select>
        </label>
        <Input
          label="Adjustment USD"
          name="adjustment"
          type="number"
          min="0"
          step="0.01"
          value={invoice.adjustment}
        />
        <Input
          label="Adjustment reason"
          name="adjustment_reason"
          value={invoice.adjustment_reason}
        />
        <Input
          label="Adjustment approval reference"
          name="adjustment_approval"
          value={invoice.adjustment_approval}
        />
        <label className="field">
          Invoice notes
          <textarea name="notes" defaultValue={invoice.notes} />
        </label>
        <Button type="submit">Save invoice changes</Button>
      </form>
    </Modal>
  );
}
