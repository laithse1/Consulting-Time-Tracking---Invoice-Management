import { today } from "@/lib/dates";
import { usd } from "@/lib/money";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { EntryTable } from "@/components/tables/EntryTable";
import { Badge } from "@/components/ui/Badge";
import { Stat } from "@/components/ui/Stat";
import { Step } from "@/components/ui/Step";
import { ArrowUpRight, Clock } from "lucide-react";
export function DashboardPage() {
  const {
    weekHours,
    contract,
    monthlyHours,
    billable,
    outstanding,
    invoices,
    weeks,
    setPage,
    entries,
    data,
    entryForm,
  } = useWorkspaceData();
  return (
    <>
      <div className="stats">
        <Stat
          label="Hours this week"
          value={weekHours.toFixed(2)}
          suffix="hrs"
          detail={`${contract?.minimum_hours}–${contract?.maximum_hours} billable hours expected`}
        />
        <Stat
          label="Hours this month"
          value={monthlyHours.toFixed(2)}
          suffix="hrs"
          detail={`${billable.reduce((a, e) => a + (e.seconds || 0) / 3600, 0).toFixed(2)} billable hours`}
        />
        <Stat
          label="Monthly billable value"
          value={usd(
            billable.reduce((a, e) => a + ((e.seconds || 0) / 3600) * Number(contract?.rate), 0),
          )}
          detail={`${usd(contract?.rate)} / hour · actual work only`}
        />
        <Stat
          label="Outstanding receivables"
          value={usd(outstanding)}
          detail={`${invoices.filter((i) => i.overdue).length} overdue invoices`}
        />
      </div>
      <div className="dashboard-grid">
        <section className="panel">
          <div className="section-head">
            <div>
              <h2>Weekly contract progress</h2>
              <p className="muted small">
                Actual billable hours · full weeks, including month boundaries
              </p>
            </div>
            <Badge>
              {contract?.minimum_hours}–{contract?.maximum_hours} hrs / week
            </Badge>
          </div>
          {weeks.map((w) => (
            <div className="week-row" key={w.week}>
              <div className="section-head">
                <strong>Week of {w.week}</strong>
                <span>
                  {Number(w.hours).toFixed(2)} <span className="muted">/ {w.maximum} hrs</span>
                </span>
              </div>
              <div className="progress">
                <div
                  style={{
                    width: Math.min(100, (Number(w.hours) / w.maximum) * 100) + "%",
                    background: w.unresolved ? "var(--amber)" : "var(--green)",
                  }}
                />
              </div>
              <div className="section-head small">
                <span className="muted">{w.status}</span>
                {w.unresolved && (
                  <button
                    className="text-btn"
                    onClick={() => {
                      setPage("Timesheets");
                    }}
                  >
                    Review <ArrowUpRight size={12} />
                  </button>
                )}
              </div>
            </div>
          ))}
          <p className="callout">
            Assignment shortfalls are planning issues. They never become billable time
            automatically.
          </p>
        </section>
        <section className="panel workflow">
          <div className="section-head">
            <h2>Your next steps</h2>
            <Clock size={18} />
          </div>
          <Step
            number="01"
            title="Confirm your draft activities"
            text={`${entries.filter((e) => e.draft).length} activities need dates and actual durations.`}
            onClick={() => setPage("Time Tracker")}
          />
          <Step
            number="02"
            title="Review weekly hours"
            text={`${weeks.filter((w) => w.unresolved).length} weeks need clarification or approval.`}
            onClick={() => setPage("Timesheets")}
          />
          <Step
            number="03"
            title="Prepare your monthly invoice"
            text={`${invoices.filter((i) => i.state === "Draft" || i.state === "Ready for Review").length} invoices awaiting submission.`}
            onClick={() => setPage("Invoices")}
          />
          <div className="paid">
            <span className="muted small">PAID YEAR TO DATE</span>
            <strong>
              {usd(
                data.payments
                  .filter(
                    (p) =>
                      p.received.startsWith(today().slice(0, 4)) &&
                      invoices.some((i) => i.id === p.invoice_id),
                  )
                  .reduce((a, p) => a + Number(p.amount), 0),
              )}
            </strong>
            <span className="muted small">Recurring consulting payments</span>
          </div>
        </section>
      </div>
      <section className="panel">
        <div className="section-head">
          <h2>Recent activity</h2>
          <button className="text-btn" onClick={() => setPage("Time Tracker")}>
            View all time <ArrowUpRight size={14} />
          </button>
        </div>
        <EntryTable entries={entries.slice(0, 5)} projects={data.projects} onEdit={entryForm} />
      </section>
    </>
  );
}
