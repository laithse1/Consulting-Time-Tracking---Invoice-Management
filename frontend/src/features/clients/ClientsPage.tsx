import { usd } from "@/lib/money";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Stat } from "@/components/ui/Stat";
import { Building2 } from "lucide-react";
export function ClientsPage() {
  const { setModal, data, contract } = useWorkspaceData();
  return (
    <>
      <div className="section-head">
        <h2>Clients</h2>
        <div className="actions">
          <Button secondary onClick={() => setModal("client")}>
            Add client
          </Button>
          <Button onClick={() => setModal("new-contract")}>Add contract</Button>
        </div>
      </div>
      <div className="project-grid">
        {data.clients.map((c) => (
          <section className="panel" key={c.id}>
            <Building2 size={24} />
            <h2>{c.name}</h2>
            <p>{c.email}</p>
            <p className="muted">{c.address || "Address not entered"}</p>
            <button className="text-btn" onClick={() => setModal("client:" + c.id)}>
              Edit client
            </button>
          </section>
        ))}
      </div>
      <section className="panel">
        <div className="section-head">
          <h2>Active contract settings</h2>
          <Button secondary onClick={() => setModal("contract")}>
            Edit contract
          </Button>
        </div>
        <div className="stats">
          <Stat label="Hourly rate" value={usd(contract?.rate)} detail="USD" />
          <Stat
            label="Weekly range"
            value={`${contract?.minimum_hours}–${contract?.maximum_hours}`}
            detail="Billable hours"
          />
          <Stat
            label="Payment terms"
            value={`${contract?.payment_days} days`}
            detail="Approved invoices, from receipt"
          />
        </div>
        <p>
          Consulting start: {contract?.start_date} · Week starts:{" "}
          {
            ["", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][
              contract?.week_start || 1
            ]
          }
        </p>
        <p className="callout">
          The weekly minimum is an assignment obligation. Actual work and approved adjustments are
          recorded separately.
        </p>
      </section>
    </>
  );
}
