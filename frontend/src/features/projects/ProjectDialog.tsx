import { request } from "@/lib/api/client";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function ProjectDialog() {
  const { setModal, act, contract } = useWorkspaceData();
  return (
    <Modal title="Add project" onClose={() => setModal("")}>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(() =>
            request("/projects", {
              ...b,
              client_id: contract?.client_id,
            }),
          );
        }}
      >
        <Input label="Project name" name="name" required />
        <Input label="Repository reference" name="repository" />
        <Button type="submit">Save project</Button>
      </form>
    </Modal>
  );
}
