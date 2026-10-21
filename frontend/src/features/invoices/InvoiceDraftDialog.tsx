import { request } from "@/lib/api/client";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
import type { Preview } from "@/types";
export function InvoiceDraftDialog() {
  const { modal, setModal, act, cid, selected, setSelected, from, to } = useWorkspaceData();
  return (
    <Modal
      title={modal === "replacement" ? "Documented invoice replacement" : "Create monthly invoice"}
      onClose={() => setModal("")}
    >
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(async () => {
            const p = await request<Preview>("/invoices", {
              ...b,
              contract_id: cid,
              replacement_for: modal === "replacement" ? selected?.invoice.id : undefined,
            });
            setSelected(p);
          }, "Draft invoice created");
        }}
      >
        <div className="form-grid">
          <Input
            label="Service period start"
            name="period_start"
            type="date"
            value={modal === "replacement" ? selected?.invoice.period_start : from}
            required
          />
          <Input
            label="Service period end"
            name="period_end"
            type="date"
            value={modal === "replacement" ? selected?.invoice.period_end : to}
            required
          />
        </div>
        <label className="field">
          Presentation
          <select name="presentation">
            <option value="itemized">Itemized</option>
            <option value="grouped">Grouped with full dated work detail</option>
          </select>
        </label>
        {modal === "replacement" && (
          <Input label="Reason for replacement (required)" name="revision_reason" required />
        )}
        <details>
          <summary>Explicit contractual adjustment (optional)</summary>
          <p className="muted small">
            Never generated from missing hours. Enter only an independently authorized monetary
            adjustment.
          </p>
          <Input
            label="Adjustment USD"
            name="adjustment"
            type="number"
            step="0.01"
            min="0"
            value="0"
          />
          <Input label="Documented reason" name="adjustment_reason" />
          <Input label="Manual approval reference" name="adjustment_approval" />
        </details>
        <Button type="submit">Create draft for review</Button>
      </form>
    </Modal>
  );
}
