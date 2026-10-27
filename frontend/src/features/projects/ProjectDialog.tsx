import { request } from "@/lib/api/client";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
export function ProjectDialog() {
  const { setModal, act, contract, modal, data } = useWorkspaceData();
  const project = modal.startsWith("project:")
    ? data.projects.find((item) => item.id === Number(modal.slice(8)))
    : undefined;
  return (
    <Modal title={project ? "Edit project" : "Add project"} onClose={() => setModal("")}>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(() =>
            request("/projects", {
              ...b,
              id: project?.id || 0,
              client_id: contract?.client_id,
            }),
          );
        }}
      >
        <Input label="Project name" name="name" value={project?.name} required />
        <Input label="Repository reference" name="repository" value={project?.repository} />
        <Button type="submit">Save project</Button>
      </form>
    </Modal>
  );
}
