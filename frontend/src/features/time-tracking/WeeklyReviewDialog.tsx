import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { request } from "@/lib/api/client";
import { formData } from "@/lib/forms";
export function WeeklyReviewDialog() {
  const { weeks, modal, setModal, act, cid } = useWorkspaceData();
  return (() => {
    const w = weeks.find((w) => w.week === modal.slice(5))!;
    return (
      <Modal title={"Review week of " + w.week} onClose={() => setModal("")}>
        <p className="callout">
          Acknowledging a shortfall resolves planning review only. It does not add invoice charges.
        </p>
        <form
          onSubmit={(e) => {
            const b = formData(e);
            act(
              () =>
                request("/weekly", {
                  ...b,
                  contract_id: cid,
                  week: w.week,
                }),
              "Weekly review recorded",
            );
          }}
        >
          <label className="field">
            Contractor availability
            <select name="available" defaultValue={w.available ?? 1}>
              <option value="1">Available</option>
              <option value="0">Unavailable</option>
            </select>
          </label>
          <Input
            label="Written exception agreement reference"
            name="exception_ref"
            value={w.exception_ref}
          />
          <Input
            label="Written overtime approval reference"
            name="overtime_ref"
            value={w.overtime_ref}
          />
          <Input
            label="Shortfall / partial-week clarification and planning acknowledgement"
            name="acknowledgement"
            value={w.acknowledgement}
          />
          <Button type="submit">Save weekly review</Button>
        </form>
      </Modal>
    );
  })();
}
