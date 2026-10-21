import { categories } from "@/config/workCategories";
import type { Project } from "@/types";
import { titles } from "./actionTitles";
import type { Action } from "./types";
interface Props {
  action: Action;
  index: number;
  projects: Project[];
  locked: boolean;
  onEdit: (index: number, patch: Partial<Action>) => void;
}
export function AssistantActionCard({ action, index, projects, locked, onEdit }: Props) {
  return (
    <article className="assistant-action">
      <h3>
        {index + 1}. {titles[action.type] || action.type}
      </h3>
      <fieldset disabled={locked}>
        {action.type === "log_work" || action.type === "start_timer" ? (
          <>
            <label className="field">
              Task description
              <textarea
                value={action.description}
                maxLength={10000}
                onChange={(e) => onEdit(index, { description: e.target.value })}
              />
            </label>
            <div className="form-grid">
              <label className="field">
                Project
                <select
                  value={action.project_id || 0}
                  onChange={(e) => onEdit(index, { project_id: +e.target.value })}
                >
                  <option value={0}>Choose project</option>
                  {projects.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Category
                <select
                  value={action.category}
                  onChange={(e) => onEdit(index, { category: e.target.value })}
                >
                  {categories.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </label>
              {action.type === "start_timer" && (
                <label className="field">
                  Timer work date
                  <input
                    type="date"
                    value={action.work_date || ""}
                    onChange={(e) => onEdit(index, { work_date: e.target.value })}
                  />
                </label>
              )}
              {action.type === "log_work" && (
                <>
                  <label className="field">
                    Actual work date
                    <input
                      type="date"
                      value={action.work_date || ""}
                      onChange={(e) => onEdit(index, { work_date: e.target.value })}
                    />
                  </label>
                  <label className="field">
                    Actual duration (hours)
                    <input
                      type="number"
                      min="0"
                      max="24"
                      step="any"
                      value={action.seconds ? action.seconds / 3600 : ""}
                      onChange={(e) =>
                        onEdit(index, {
                          seconds: Math.round(Number(e.target.value) * 3600),
                        })
                      }
                    />
                  </label>
                  <label className="field">
                    Clock start (optional)
                    <input
                      type="time"
                      value={action.start_time || ""}
                      onChange={(e) => onEdit(index, { start_time: e.target.value })}
                    />
                  </label>
                  <label className="field">
                    Reference
                    <input
                      value={action.reference || ""}
                      onChange={(e) => onEdit(index, { reference: e.target.value })}
                    />
                  </label>
                </>
              )}
            </div>
            {action.type === "log_work" && (
              <>
                <label className="assistant-check">
                  <input
                    type="checkbox"
                    checked={action.billable === 1}
                    onChange={(e) => onEdit(index, { billable: +e.target.checked })}
                  />
                  Billable work
                </label>
                <label className="assistant-check">
                  <input
                    type="checkbox"
                    checked={action.draft === 0}
                    disabled={locked || !action.work_date || !action.seconds || !action.project_id}
                    onChange={(e) => onEdit(index, { draft: e.target.checked ? 0 : 1 })}
                  />
                  This is completed work with the actual date and duration. Include it in timesheets
                  and billing.
                </label>
                <span className="badge">
                  {action.draft === 0 ? "Confirmed work" : "Draft · excluded from billing"}
                </span>
              </>
            )}
          </>
        ) : (
          <p>{action.description}</p>
        )}
        {action.period_start !== undefined && (
          <div className="form-grid">
            <label className="field">
              Period start
              <input
                type="date"
                value={action.period_start}
                onChange={(e) => onEdit(index, { period_start: e.target.value })}
              />
            </label>
            <label className="field">
              Period end
              <input
                type="date"
                value={action.period_end}
                onChange={(e) => onEdit(index, { period_end: e.target.value })}
              />
            </label>
          </div>
        )}
      </fieldset>
      {!!action.questions?.length && (
        <ul className="assistant-questions">
          {action.questions.map((q) => (
            <li key={q}>{q}</li>
          ))}
        </ul>
      )}
    </article>
  );
}
