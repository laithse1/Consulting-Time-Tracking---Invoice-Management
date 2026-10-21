import { request } from "@/lib/api/client";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function ContractDialog() {
  const { modal, contract, setModal, act, data } = useWorkspaceData();
  return (() => {
    const c = modal === "contract" ? contract : undefined;
    return (
      <Modal title={c ? "Edit contract" : "Add contract"} onClose={() => setModal("")}>
        <form
          onSubmit={(e) => {
            const b = formData(e);
            act(() => request("/contracts", { ...b, id: c?.id || 0 }));
          }}
        >
          <label className="field">
            Client
            <select name="client_id" defaultValue={c?.client_id}>
              {data.clients.map((cl) => (
                <option value={cl.id} key={cl.id}>
                  {cl.name}
                </option>
              ))}
            </select>
          </label>
          <Input label="Contractor name" name="contractor" value={c?.contractor} required />
          <Input
            label="Contractor address"
            name="contractor_address"
            value={c?.contractor_address}
          />
          <div className="form-grid">
            <Input
              label="Start date"
              name="start_date"
              type="date"
              value={c?.start_date}
              required
            />
            <Input label="End date (optional)" name="end_date" type="date" value={c?.end_date} />
            <Input
              label="Hourly rate USD"
              name="rate"
              type="number"
              step="0.01"
              min="0.01"
              value={c?.rate}
              required
            />
            <Input
              label="Minimum hours per week"
              name="minimum_hours"
              type="number"
              min="0"
              value={c?.minimum_hours}
              required
            />
            <Input
              label="Maximum hours without approval"
              name="maximum_hours"
              type="number"
              min="0"
              value={c?.maximum_hours}
              required
            />
            <Input
              label="Payment days from receipt"
              name="payment_days"
              type="number"
              min="1"
              value={c?.payment_days}
              required
            />
            <Input
              label="Dispute period days"
              name="dispute_days"
              type="number"
              min="1"
              value={c?.dispute_days}
              required
            />
            <label className="field">
              Week starts
              <select name="week_start" defaultValue={c?.week_start || 1}>
                {["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"].map(
                  (day, i) => (
                    <option key={day} value={i + 1}>
                      {day}
                    </option>
                  ),
                )}
              </select>
            </label>
          </div>
          <Input
            label="Payment instructions (optional)"
            name="payment_instructions"
            value={c?.payment_instructions}
          />
          <Button type="submit">Save contract</Button>
        </form>
      </Modal>
    );
  })();
}
