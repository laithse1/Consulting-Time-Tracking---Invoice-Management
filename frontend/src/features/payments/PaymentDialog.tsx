import { request } from "@/lib/api/client";
import { today } from "@/lib/dates";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function PaymentDialog() {
  const { modal, setModal, act, mutateInvoice, selected } = useWorkspaceData();
  return (
    <Modal
      title={
        modal === "historical" ? "Confirm historical payment receipt" : "Record payment received"
      }
      onClose={() => setModal("")}
    >
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(
            () =>
              modal === "historical" ? request("/historical", b) : mutateInvoice("/payments", b),
            "Receipt recorded",
          );
        }}
      >
        {modal === "payment" && (
          <>
            <Input
              label="Amount received USD"
              name="amount"
              type="number"
              step="0.01"
              min="0.01"
              max={selected?.invoice.balance}
              required
            />
            <Input label="Payment method" name="method" required />
          </>
        )}
        <Input label="Actual date received" name="received" type="date" max={today()} required />
        <Input label="Payment / bank reference" name="reference" required />
        <Button type="submit">Confirm receipt</Button>
      </form>
    </Modal>
  );
}
