import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { request } from "@/lib/api/client";
import { formData } from "@/lib/forms";
export function ExpenseDialog() {
  const { data, modal, setModal, act, cid } = useWorkspaceData();
  return (() => {
    const expense = data.expenses.find((e) => e.id === Number(modal.slice(8)));
    return (
      <Modal title="Record reimbursable expense" onClose={() => setModal("")}>
        <form
          onSubmit={(e) => {
            const b = formData(e);
            act(() =>
              request("/expenses", {
                ...b,
                id: expense?.id || 0,
                contract_id: cid,
              }),
            );
          }}
        >
          <Input
            label="Expense date"
            name="expense_date"
            value={expense?.expense_date}
            type="date"
            required
          />
          <Input label="Description" name="description" value={expense?.description} required />
          <Input
            label="Amount USD"
            name="amount"
            value={expense?.amount}
            type="number"
            min="0.01"
            step="0.01"
            required
          />
          <Input
            label="Prior written approval reference"
            name="approval_ref"
            value={expense?.approval_ref}
            required
          />
          <Button type="submit">Save expense</Button>
        </form>
      </Modal>
    );
  })();
}
