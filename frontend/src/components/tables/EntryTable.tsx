import { Clock } from "lucide-react";
import type { Entry } from "../../types";
import { Badge } from "../ui/Badge";
export function EntryTable({
  entries,
  projects,
  onEdit,
  readonly = false,
}: {
  entries: Entry[];
  projects: { id: number; name: string }[];
  onEdit: (e: Entry) => void;
  readonly?: boolean;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Work & project</th>
            <th>Category</th>
            <th>Hours</th>
            <th>Status</th>
            {!readonly && <th />}
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <tr key={e.id}>
              <td className="nowrap">
                {e.work_date || "Unconfirmed"}
                <small>{e.start_time || (e.work_date ? "Duration only" : "Date needed")}</small>
              </td>
              <td>
                <strong className="entry-description">{e.description}</strong>
                <small>
                  {projects.find((p) => p.id === e.project_id)?.name ||
                    e.project ||
                    "Project needed"}
                  {e.repository && " · " + e.repository}
                </small>
              </td>
              <td>
                <span className="muted small">{e.category}</span>
              </td>
              <td className="nowrap">{e.seconds ? (e.seconds / 3600).toFixed(4) : "—"}</td>
              <td>
                <Badge>
                  {e.draft ? "Draft / incomplete" : e.billable ? "Billable" : "Nonbillable"}
                </Badge>
              </td>
              {!readonly && (
                <td>
                  <button className="text-btn" onClick={() => onEdit(e)}>
                    Review
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {entries.length === 0 && (
        <div className="empty">
          <Clock size={26} />
          <h3>No confirmed work yet</h3>
          <p className="muted">Log actual work or confirm an incomplete activity to get started.</p>
        </div>
      )}
    </div>
  );
}
