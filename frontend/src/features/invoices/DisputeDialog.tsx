import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function DisputeDialog() {
  const { setModal, act, mutateInvoice } = useWorkspaceData();
  return (
    <Modal title="Record dispute" onClose={() => setModal("")}>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(
            () =>
              mutateInvoice("/status", {
                state: "Disputed",
                notes: b.notes,
              }),
            "Dispute recorded",
          );
        }}
      >
        <Input label="Dispute details and references" name="notes" required />
        <Button type="submit">Save dispute</Button>
      </form>
    </Modal>
  );
}
