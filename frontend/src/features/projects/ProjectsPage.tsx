import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { FolderKanban, Plus } from "lucide-react";
export function ProjectsPage() {
  const { setModal, projects, actual } = useWorkspaceData();
  return (
    <>
      <div className="section-head">
        <h2>Client projects</h2>
        <Button onClick={() => setModal("project")}>
          <Plus size={16} />
          New project
        </Button>
      </div>
      <div className="project-grid">
        {projects.map((p) => (
          <section className="panel" key={p.id}>
            <FolderKanban size={24} className="project-icon" />
            <h2>{p.name}</h2>
            <p className="muted small">{p.repository || "Add a repository reference"}</p>
            <strong>
              {(
                actual
                  .filter((e) => e.project_id === p.id)
                  .reduce((a, e) => a + (e.seconds || 0), 0) / 3600
              ).toFixed(2)}{" "}
              hrs
            </strong>
            <small className="muted"> in selected month</small>
          </section>
        ))}
      </div>
    </>
  );
}
