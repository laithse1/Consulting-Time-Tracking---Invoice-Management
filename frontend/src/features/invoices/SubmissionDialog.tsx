import { today } from "@/lib/dates";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function SubmissionDialog() {
  const { setModal, act, mutateInvoice } = useWorkspaceData();
  return (
    <Modal title="Record invoice submission" onClose={() => setModal("")}>
      <p>Confirm you sent the invoice and attached the PDF.</p>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(
            () =>
              mutateInvoice("/status", {
                state: "Sent",
                submitted: b.submitted,
              }),
            "Submission recorded",
          );
        }}
      >
        <Input
          label="Actual submission / receipt date"
          name="submitted"
          type="date"
          value={today()}
          max={today()}
          required
        />
        <Button type="submit">Confirm sent</Button>
      </form>
    </Modal>
  );
}
