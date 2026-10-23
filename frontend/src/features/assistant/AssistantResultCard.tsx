import { usd } from "@/lib/money";
import { Clock } from "lucide-react";
import { titles } from "./actionTitles";
import type { Result } from "./types";
interface Props {
  result: Result;
  onInvoice: (id: number) => void;
}
export function AssistantResultCard({ result, onInvoice }: Props) {
  return (
    <div className="assistant-result">
      <strong>{titles[result.type]}</strong>
      {result.entry && (
        <p>
          Entry #{result.entry.id} saved ·{" "}
          {result.entry.draft
            ? "Draft, excluded from billing"
            : `${Number(result.entry.seconds) / 3600} actual hours`}
        </p>
      )}
      {result.invoice && (
        <>
          <p>{result.invoice.number} · Draft</p>
          {result.preview && (
            <>
              <p>
                {Number(result.preview.hours).toFixed(2)} billable hours ·{" "}
                {usd(result.preview.total)} total
              </p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Work description</th>
                      <th>Hours</th>
                      <th>Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.preview.entries.map((entry) => (
                      <tr key={entry.id}>
                        <td>{entry.work_date}</td>
                        <td>{entry.description}</td>
                        <td>{(Number(entry.seconds) / 3600).toFixed(2)}</td>
                        <td>{usd(entry.amount || 0)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="small muted">
                Services {usd(result.preview.subtotal)} · Expenses{" "}
                {usd(result.preview.expenseTotal)}. Resolve warnings and finalize in invoice review
                before submission.
              </p>
            </>
          )}
          <button className="btn secondary" onClick={() => onInvoice(result.invoice!.id)}>
            Review invoice
          </button>
          {result.warnings?.map((w) => (
            <p className="small muted" key={w}>
              {w}
            </p>
          ))}
        </>
      )}
      {result.type.endsWith("_timer") && (
        <p>
          <Clock size={14} /> Timer action saved. Open Time Tracker to view the live timer or
          stopped draft.
        </p>
      )}
      {result.report && (
        <>
          <p>
            {Number(result.report.actualHours).toFixed(2)} actual hours ·{" "}
            {Number(result.report.billableHours).toFixed(2)} billable ·{" "}
            {usd(result.report.servicesValue)} services value
          </p>
          <table>
            <thead>
              <tr>
                <th>Project</th>
                <th>Hours</th>
              </tr>
            </thead>
            <tbody>
              {result.report.projects.map((p) => (
                <tr key={p.project}>
                  <td>{p.project}</td>
                  <td>{Number(p.hours).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="small muted">{result.report.note}</p>
        </>
      )}
      {result.weeks?.map((w) => (
        <p key={w.week}>
          Week of {w.week}: {Number(w.hours).toFixed(2)} hours · {w.status}
        </p>
      ))}
      {result.email && (
        <>
          <p>
            To: {result.email.to} · CC: {result.email.cc || "—"}
          </p>
          <p>{result.email.subject}</p>
          <textarea
            aria-label="Prepared submission email"
            readOnly
            value={result.email.body}
            rows={8}
          />
        </>
      )}
    </div>
  );
}
