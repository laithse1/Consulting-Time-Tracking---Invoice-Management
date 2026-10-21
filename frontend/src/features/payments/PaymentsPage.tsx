import { download } from "@/lib/api/client";
import { usd } from "@/lib/money";
import { useWorkspaceData } from "@/app/WorkspaceContext";
import { InvoiceTable } from "@/components/tables/InvoiceTable";
import { Button } from "@/components/ui/Button";
import { Stat } from "@/components/ui/Stat";
import { Download } from "lucide-react";
export function PaymentsPage() {
  const { outstanding, data, invoices, act, openInvoice, setModal } = useWorkspaceData();
  return (
    <>
      <div className="stats">
        <Stat
          label="Outstanding balance"
          value={usd(outstanding)}
          detail="Excludes replaced invoices"
        />
        <Stat
          label="Received"
          value={usd(
            data.payments
              .filter((p) => invoices.some((i) => i.id === p.invoice_id))
              .reduce((a, p) => a + Number(p.amount), 0),
          )}
          detail="Recurring invoices"
        />
        <Stat
          label="Overdue"
          value={String(invoices.filter((i) => i.overdue).length)}
          detail="Based on actual submission date"
        />
      </div>
      <section className="panel">
        <div className="section-head">
          <h2>Invoice balances</h2>
          <Button
            secondary
            onClick={() =>
              act(() => download("/history.csv", "bookkeeping.csv"), "History exported")
            }
          >
            <Download size={16} />
            Export history
          </Button>
        </div>
        <InvoiceTable invoices={invoices} onOpen={openInvoice} />
      </section>
      <section className="panel">
        <h2>Payment history</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Received</th>
                <th>Invoice</th>
                <th>Amount</th>
                <th>Method</th>
                <th>Reference</th>
              </tr>
            </thead>
            <tbody>
              {data.payments
                .filter((p) => invoices.some((i) => i.id === p.invoice_id))
                .map((p) => (
                  <tr key={p.id}>
                    <td>{p.received}</td>
                    <td>{invoices.find((i) => i.id === p.invoice_id)?.number}</td>
                    <td>{usd(p.amount)}</td>
                    <td>{p.method}</td>
                    <td>{p.reference}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </section>
      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Historical prior-work payment</h2>
            <p className="muted">
              Separate from recurring consulting invoices and dashboard totals.
            </p>
          </div>
          <strong>{usd(data.historical[0]?.amount)}</strong>
        </div>
        <p>
          {data.historical[0]?.received
            ? `Received ${data.historical[0].received} · ${data.historical[0].reference}`
            : "Receipt not confirmed."}
        </p>
        {!data.historical[0]?.received && (
          <Button secondary onClick={() => setModal("historical")}>
            Record actual receipt
          </Button>
        )}
      </section>
    </>
  );
}
