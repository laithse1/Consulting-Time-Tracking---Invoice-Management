import { categories } from "@/config/workCategories";
import { request } from "@/lib/api/client";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
import type { Preview } from "@/types";
export function EntryDialog() {
  const { edit, setModal, act, cid, projects, selected, setSelected } = useWorkspaceData();
  return (
    <Modal title={edit ? "Review work entry" : "Log work"} onClose={() => setModal("")}>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(async () => {
            await request("/entries", {
              ...b,
              id: edit?.id || 0,
              contract_id: cid,
              seconds: Math.round(Number(b.hours) * 3600),
            });
            if (selected) setSelected(await request<Preview>(`/invoices/${selected.invoice.id}`));
          }, "Work entry saved");
        }}
      >
        <div className="form-grid">
          <Input label="Date worked" name="work_date" type="date" value={edit?.work_date} />
          <Input label="Local start time" name="start_time" type="time" value={edit?.start_time} />
          <Input
            label="Actual hours (decimal)"
            name="hours"
            type="number"
            min="0.0001"
            max="24"
            step="0.0001"
            value={edit?.seconds ? edit.seconds / 3600 : undefined}
          />
          <label className="field">
            Project
            <select name="project_id" defaultValue={edit?.project_id || ""}>
              <option value="">Select project</option>
              {projects
                .filter((p) => !p.archived || p.id === edit?.project_id)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
            </select>
          </label>
          <label className="field">
            Category
            <select name="category" defaultValue={edit?.category || categories[0]}>
              {categories.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          <label className="field">
            Billing
            <select name="billable" defaultValue={edit?.billable ?? 1}>
              <option value="1">Billable</option>
              <option value="0">Nonbillable</option>
            </select>
          </label>
        </div>
        <Input
          label="Description of services"
          name="description"
          value={edit?.description}
          required
        />
        <Input label="Repository reference" name="repository" value={edit?.repository} />
        <Input
          label="GitHub issue, PR, commit or attachment URL"
          name="reference"
          value={edit?.reference}
        />
        <label className="field">
          Notes / supporting references
          <textarea name="notes" defaultValue={edit?.notes} />
        </label>
        <label className="field">
          Entry status
          <select name="draft" defaultValue={edit?.draft ?? 1}>
            <option value="1">Draft / incomplete — excluded from billing</option>
            <option value="0">Confirmed actual work</option>
          </select>
        </label>
        <div className="actions">
          <Button type="submit">Save entry</Button>
          {edit && (
            <Button
              secondary
              onClick={() => {
                if (confirm("Delete this entry? Its prior contents remain in the audit history."))
                  act(async () => {
                    await request("/entries/" + edit.id + "?confirmed=true", undefined, "DELETE");
                    if (selected)
                      setSelected(await request<Preview>(`/invoices/${selected.invoice.id}`));
                  }, "Entry deleted");
              }}
            >
              Delete entry
            </Button>
          )}
        </div>
      </form>
    </Modal>
  );
}
