import { duration } from "@/lib/time";
import { request } from "@/lib/api/client";
import { weekStart } from "@/lib/dates";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { EntryTable } from "@/components/tables/EntryTable";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Pause, Play, Search, Square } from "lucide-react";
export function TimeTrackingPage() {
  const {
    timer,
    tick,
    act,
    setModal,
    page,
    weeks,
    query,
    setQuery,
    filter,
    setFilter,
    period,
    setPeriod,
    sort,
    setSort,
    entries,
    contract,
    currentWeek,
    from,
    to,
    data,
    entryForm,
  } = useWorkspaceData();
  return (
    <>
      <section className="panel timer-panel">
        <div>
          <span className="eyebrow">{timer ? "ACTIVE TIMER" : "READY WHEN YOU ARE"}</span>
          <h2>{timer?.description || "Make every hour count."}</h2>
          <p className="muted small">Stop creates a draft for you to review before billing.</p>
        </div>
        <div className="actions">
          {timer ? (
            <>
              <strong className="timer-display">
                {duration(
                  timer.elapsed +
                    (timer.started
                      ? Math.max(0, Math.floor((tick - Date.parse(timer.started)) / 1000))
                      : 0),
                )}
              </strong>
              <Button
                secondary
                onClick={() =>
                  act(() => request("/timer/" + (timer.started ? "pause" : "resume"), {}))
                }
              >
                {timer.started ? <Pause size={16} /> : <Play size={16} />}{" "}
                {timer.started ? "Pause" : "Resume"}
              </Button>
              <Button onClick={() => act(() => request("/timer/stop", {}), "Timer saved as draft")}>
                <Square size={14} />
                Stop
              </Button>
            </>
          ) : (
            <Button onClick={() => setModal("timer")}>
              <Play size={16} />
              Start timer
            </Button>
          )}
        </div>
      </section>
      {page === "Timesheets" && (
        <section className="panel">
          <div className="section-head">
            <h2>Weekly compliance</h2>
            <span className="muted small">Minimum is never automatically billed.</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Week starting</th>
                  <th>Actual billable</th>
                  <th>Minimum</th>
                  <th>Difference</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {weeks.map((w) => (
                  <tr key={w.week}>
                    <td>
                      {w.week}
                      {w.partial && <small>Partial contract week</small>}
                    </td>
                    <td>{Number(w.hours).toFixed(2)} hrs</td>
                    <td>{w.minimum} hrs</td>
                    <td>{Number(w.difference).toFixed(2)} hrs</td>
                    <td>
                      <Badge>{w.status}</Badge>
                    </td>
                    <td>
                      <button className="text-btn" onClick={() => setModal("week:" + w.week)}>
                        Review
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <section className="panel">
        <div className="section-head">
          <h2>Work entries</h2>
          <div className="actions">
            <Search size={16} />
            <input
              aria-label="Search entries"
              placeholder="Search work, references…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <select
              aria-label="Entry filter"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            >
              <option value="all">All entries</option>
              <option value="draft">Drafts</option>
              <option value="billable">Billable</option>
              <option value="nonbillable">Nonbillable</option>
            </select>
            <select
              aria-label="Timesheet period"
              value={period}
              onChange={(e) => setPeriod(e.target.value)}
            >
              <option value="month">Selected month + drafts</option>
              <option value="week">Current week + drafts</option>
              <option value="all">All dates</option>
            </select>
            <select
              aria-label="Sort entries"
              value={sort}
              onChange={(e) => setSort(e.target.value)}
            >
              <option value="date">Newest date</option>
              <option value="duration">Longest duration</option>
              <option value="project">Project</option>
            </select>
          </div>
        </div>
        <EntryTable
          entries={entries
            .filter(
              (e) =>
                (e.description + " " + e.repository + " " + e.reference + " " + e.category)
                  .toLowerCase()
                  .includes(query.toLowerCase()) &&
                (filter === "all" ||
                  (filter === "draft" && !!e.draft) ||
                  (filter === "billable" && !!e.billable) ||
                  (filter === "nonbillable" && !e.billable)) &&
                (period === "all" ||
                  e.draft ||
                  (period === "week"
                    ? e.work_date &&
                      weekStart(e.work_date, contract?.week_start || 1) === currentWeek
                    : e.work_date && e.work_date >= from && e.work_date <= to)),
            )
            .sort((a, b) =>
              sort === "duration"
                ? (b.seconds || 0) - (a.seconds || 0)
                : sort === "project"
                  ? (a.project_id || 0) - (b.project_id || 0)
                  : (b.work_date || "").localeCompare(a.work_date || ""),
            )}
          projects={data.projects}
          onEdit={entryForm}
        />
      </section>
    </>
  );
}
