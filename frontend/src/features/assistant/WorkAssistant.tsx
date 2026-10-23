import { ArrowUpRight, MessageSquare } from "lucide-react";
import { request } from "@/lib/api/client";
import { AssistantPlanPanel } from "./AssistantPlanPanel";
import { useState } from "react";
import { today, monthEnd } from "@/lib/dates";
import type { Plan } from "./types";
import { useWorkAssistant } from "./useWorkAssistant";
import type { State } from "@/types";

export default function WorkAssistant({
  data,
  cid,
  onRefresh,
  onInvoice,
}: {
  data: State;
  cid: number;
  onRefresh: () => Promise<void>;
  onInvoice: (id: number) => void;
}) {
  const assistant = useWorkAssistant(data, cid);
  const {
    sourceLimit,
    history,
    source,
    setSource,
    setSaved,
    reference,
    setReference,
    provider,
    setProvider,
    consent,
    setConsent,
    plan,
    setPlan,
    setConfirmed,
    busy,
    error,
    setError,
    saveState,
    setSaveState,
    save,
    run,
    reload,
  } = assistant;
  const [prepareInvoice, setPrepareInvoice] = useState(true);
  const [periodStart, setPeriodStart] = useState(today().slice(0, 7) + "-01");
  const [periodEnd, setPeriodEnd] = useState(
    today().slice(0, 7) + "-" + monthEnd(today().slice(0, 7)),
  );
  return (
    <div className="assistant-layout">
      <section className="panel assistant-composer">
        <div className="section-head">
          <div>
            <span className="eyebrow">WORK ASSISTANT</span>
            <h2>Tell your work story.</h2>
          </div>
          <MessageSquare size={24} />
        </div>
        <p className="muted">
          Describe completed work, then review one plan to log time, prepare invoices, and generate
          reports. Include actual dates, durations, and project names. A configured language
          provider can suggest professional invoice wording; local rules provide basic cleanup
          offline.
        </p>
        <label className="field">
          Work description
          <textarea
            aria-label="Work description"
            rows={9}
            maxLength={sourceLimit}
            disabled={!history || busy}
            value={source}
            onChange={(e) => setSource(e.target.value)}
            placeholder={`2026-09-15: Spent 2h refactoring geospatial-mcp-server, PR #42.\n2026-09-16: Spent 30m documenting Audiom.\nDraft invoice September 2026\nGenerate report September 2026`}
          />
        </label>
        <div className="section-head small muted">
          <span role="status">
            {saveState || (history ? "Notes saved to database" : "Loading saved notes…")}
          </span>
          <span>
            {source.length.toLocaleString()} / {sourceLimit.toLocaleString()}
          </span>
        </div>
        <div className="form-grid">
          <label className="field">
            Date for “today” and “yesterday”
            <input
              aria-label="Assistant reference date"
              type="date"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              disabled={busy}
            />
          </label>
          <label className="field">
            Processing
            <select
              aria-label="Processing mode"
              value={provider}
              disabled={busy}
              onChange={(e) => {
                setProvider(e.target.value);
                setConsent(false);
              }}
            >
              <option value="">Local rules · no AI or network</option>
              {history?.providers.map((p) => (
                <option key={p.id} value={p.id} disabled={!p.configured}>
                  {p.name} · {p.model}
                  {!p.configured && " (unavailable)"}
                </option>
              ))}
            </select>
          </label>
        </div>
        {provider && (
          <label className="assistant-check">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
            />
            Send this description, project names, and categories to the selected provider. I will
            review its draft suggestions.
          </label>
        )}
        <label className="assistant-check">
          <input
            type="checkbox"
            checked={prepareInvoice}
            disabled={busy}
            onChange={(event) => setPrepareInvoice(event.target.checked)}
          />
          Prepare an invoice after logging the reviewed work
        </label>
        {prepareInvoice && (
          <div className="form-grid">
            <label className="field">
              Invoice period start
              <input
                aria-label="Invoice period start"
                type="date"
                value={periodStart}
                disabled={busy}
                onChange={(event) => setPeriodStart(event.target.value)}
              />
            </label>
            <label className="field">
              Invoice period end
              <input
                aria-label="Invoice period end"
                type="date"
                value={periodEnd}
                disabled={busy}
                onChange={(event) => setPeriodEnd(event.target.value)}
              />
            </label>
          </div>
        )}
        <button
          className="btn"
          disabled={!source.trim() || !reference || busy || !history || (!!provider && !consent)}
          onClick={() =>
            run(async () => {
              await save(source);
              setSaved(source);
              setSaveState("Notes saved to database");
              const next = await request<Plan>("/assistant/plan", {
                contract_id: cid,
                source,
                reference_date: reference,
                provider_id: provider,
                provider_consent: consent,
                prepare_invoice: prepareInvoice,
                period_start: periodStart,
                period_end: periodEnd,
              });
              setPlan(next);
              setConfirmed(false);
              await reload(next);
            })
          }
        >
          {busy ? "Working…" : "Review action plan"}
          <ArrowUpRight size={16} />
        </button>
        <details className="assistant-help">
          <summary>What can I say?</summary>
          <p>
            Use one task per line. “Today I spent 1h 30m testing Audiom.” A clock range such as
            “09:00–11:00” is optional. “Project: Audiom” and “Date: 2026-09-15” can set context for
            following lines.
          </p>
          <p>
            Commands: “Start timer for testing Audiom”, “Pause timer”, “Resume timer”, “Stop timer”,
            “Draft invoice September 2026”, “Generate report September 2026”, “Review week September
            2026”, or “Prepare email for LA-2026-09-001”.
          </p>
          <p>
            Missing facts and estimates remain draft work. Invoice drafts use eligible unbilled
            work; finalization, payment recording, and sending emails use their existing review
            screens.
          </p>
        </details>
      </section>
      {error && (
        <div className="error" role="alert">
          {error}
          <button
            className="btn secondary"
            disabled={busy}
            onClick={() =>
              run(async () => {
                await reload(plan);
                await onRefresh();
                setError("");
              })
            }
          >
            Reload saved plan
          </button>
        </div>
      )}
      <AssistantPlanPanel assistant={assistant} onRefresh={onRefresh} onInvoice={onInvoice} />
      {!!history?.plans.length && (
        <section className="panel">
          <h2>Saved conversations</h2>
          <p className="small muted">
            Your latest {history.limits?.history ?? 50} plans. Source messages, edits, and receipts
            are stored in your database.
          </p>
          <div className="assistant-history">
            {history.plans.map((p) => (
              <button
                className={`assistant-history-item ${plan?.id === p.id ? "selected" : ""}`}
                key={p.id}
                disabled={busy}
                onClick={() => {
                  setPlan(p);
                  setConfirmed(false);
                  setError("");
                }}
              >
                <span>{p.source.slice(0, 110)}</span>
                <small>
                  {p.status} · {new Date(p.created).toLocaleString()}
                </small>
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
