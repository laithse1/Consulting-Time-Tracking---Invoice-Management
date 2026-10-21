import { download } from "@/lib/api/client";
import { usd } from "@/lib/money";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
export function ReportsPage() {
  const { month, act, entries, actual, data, contract } = useWorkspaceData();
  return (
    <>
      <section className="panel">
        <div className="section-head">
          <h2>Actual work · {month}</h2>
          <Button
            secondary
            onClick={() =>
              act(() => download("/history.csv", "bookkeeping.csv"), "History exported")
            }
          >
            Export invoice & payment history
          </Button>
        </div>
        <p className="muted">
          Confirmed work only. Values are estimates until invoiced; invoice totals use rounded
          individual lines.
        </p>
        {(["client", "project", "category", "month"] as const).map((group) => {
          const rows = new Map<string, { seconds: number; billable: number }>();
          for (const e of group === "month" ? entries.filter((e) => !e.draft) : actual) {
            const key =
              group === "client"
                ? data.clients.find((c) => c.id === contract?.client_id)?.name || "Unknown"
                : group === "project"
                  ? data.projects.find((p) => p.id === e.project_id)?.name || "Unassigned"
                  : group === "month"
                    ? e.work_date?.slice(0, 7) || "Unknown"
                    : e.category;
            const old = rows.get(key) || {
              seconds: 0,
              billable: 0,
            };
            old.seconds += e.seconds || 0;
            old.billable += e.billable ? e.seconds || 0 : 0;
            rows.set(key, old);
          }
          return (
            <div key={group}>
              <h3>By {group}</h3>
              <table>
                <thead>
                  <tr>
                    <th>{group}</th>
                    <th>Actual hours</th>
                    <th>Billable hours</th>
                    <th>Services value</th>
                  </tr>
                </thead>
                <tbody>
                  {[...rows].map(([k, v]) => (
                    <tr key={k}>
                      <td>{k}</td>
                      <td>{(v.seconds / 3600).toFixed(2)}</td>
                      <td>{(v.billable / 3600).toFixed(2)}</td>
                      <td>{usd((v.billable / 3600) * Number(contract?.rate))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {rows.size === 0 && <p className="muted">No confirmed work in this period.</p>}
            </div>
          );
        })}
      </section>
    </>
  );
}
