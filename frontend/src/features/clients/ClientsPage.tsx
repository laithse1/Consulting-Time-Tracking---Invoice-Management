import { usd } from "@/lib/money";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { Button } from "@/components/ui/Button";
import { Stat } from "@/components/ui/Stat";
import { Building2 } from "lucide-react";
import { RecordArchiveButton } from "./RecordArchiveButton";
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
            {c.archived && <span className="badge">Archived</span>}
            <p>{c.email}</p>
            <p className="muted">{c.address || "Address not entered"}</p>
            <button className="text-btn" onClick={() => setModal("client:" + c.id)}>
              Edit client
            </button>
            <RecordArchiveButton type="clients" record={c} />
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
      <section className="panel">
        <h2>All contracts</h2>
        <p className="small muted">
          Archive records to stop new work while preserving history. Archived contracts remain
          selectable for historical review.
        </p>
        {data.contracts.map((item) => (
          <div className="total-row record-management-row" key={item.id}>
            <span>
              {data.clients.find((client) => client.id === item.client_id)?.name} ·{" "}
              {item.contractor} · {item.start_date}
              {item.archived ? " · Archived" : ""}
            </span>
            <div className="actions">
              <Button secondary onClick={() => setModal(`contract:${item.id}`)}>
                Edit contract
              </Button>
              <RecordArchiveButton type="contracts" record={item} />
            </div>
          </div>
        ))}
      </section>
    </>
  );
}
