import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
export function SettingsPage() {
  const { setDark, dark, data } = useWorkspaceData();
  return (
    <>
      <section className="panel">
        <h2>Workspace preferences</h2>
        <Button secondary onClick={() => setDark(!dark)}>
          {dark ? "Use light theme" : "Use dark theme"}
        </Button>
        <p>
          Week start and payment instructions are configured per contract under Clients & Contracts.
        </p>
        <p className="muted">
          Timestamps use UTC. Work dates and start times use the local dates you confirm. Database
          backups and restore instructions are in the README.
        </p>
      </section>
      <section className="panel">
        <h2>Audit history</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Time (UTC)</th>
                <th>Action</th>
                <th>Record</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {data.audit.map((a) => (
                <tr key={a.id}>
                  <td>{a.occurred}</td>
                  <td>{a.action}</td>
                  <td>{a.entity}</td>
                  <td>
                    <details>
                      <summary>View</summary>
                      <pre>{a.detail}</pre>
                    </details>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
