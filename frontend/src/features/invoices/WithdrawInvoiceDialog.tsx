import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function WithdrawInvoiceDialog() {
  const { setModal, act, mutateInvoice } = useWorkspaceData();
  return (
    <Modal title="Withdraw unpaid invoice for correction" onClose={() => setModal("")}>
      <p className="callout">
        The original snapshot stays in your records. Its balance will be withdrawn, and its time and
        expenses will be released for correction. Notify accounting yourself if it was submitted.
      </p>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          if (confirm("Withdraw this unpaid invoice and release its work for correction?"))
            act(
              () =>
                mutateInvoice("/withdraw", {
                  confirmed: true,
                  reason: b.reason,
                }),
              "Invoice withdrawn for correction",
            );
        }}
      >
        <Input label="Documented reason for withdrawal" name="reason" required />
        <Button type="submit">Confirm withdrawal</Button>
      </form>
    </Modal>
  );
}
