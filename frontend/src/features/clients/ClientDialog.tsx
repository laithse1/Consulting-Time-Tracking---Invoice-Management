import { request } from "@/lib/api/client";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function ClientDialog() {
  const { data, modal, setModal, act } = useWorkspaceData();
  return (() => {
    const c = data.clients.find((c) => c.id === Number(modal.slice(7)));
    return (
      <Modal title={c ? "Edit client" : "Add client"} onClose={() => setModal("")}>
        <form
          onSubmit={(e) => {
            const b = formData(e);
            act(() => request("/clients", { ...b, id: c?.id || 0 }));
          }}
        >
          <Input label="Company name" name="name" value={c?.name} required />
          <Input
            label="Invoice submission email"
            name="email"
            type="email"
            value={c?.email}
            required
          />
          <Input label="Billing address" name="address" value={c?.address} />
          <Button type="submit">Save client</Button>
        </form>
      </Modal>
    );
  })();
}
