import { categories } from "@/config/workCategories";
import { request } from "@/lib/api/client";
import { today } from "@/lib/dates";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { formData } from "@/lib/forms";
import { Play } from "lucide-react";
export function TimerDialog() {
  const { setModal, act, cid, projects } = useWorkspaceData();
  return (
    <Modal title="Start a work timer" onClose={() => setModal("")}>
      <form
        onSubmit={(e) => {
          const b = formData(e);
          act(() => request("/timer/start", { ...b, contract_id: cid }), "Timer started");
        }}
      >
        <Input label="Task description" name="description" required />
        <Input label="Local date worked" name="work_date" type="date" value={today()} required />
        <label className="field">
          Project
          <select name="project_id">
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Category
          <select name="category">
            {categories.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </label>
        <Button type="submit">
          <Play size={16} />
          Start timer
        </Button>
      </form>
    </Modal>
  );
}
