import { ArrowUpRight, MessageSquare, Trash2 } from "lucide-react";
import { request } from "@/lib/api/client";
import { AssistantPlanPanel } from "./AssistantPlanPanel";
import { AssistantInputMode, type InputMode } from "./AssistantInputMode";
import { useState, useEffect } from "react";
import { today, monthEnd } from "@/lib/dates";
import type { Plan } from "./types";
import { useWorkAssistant } from "./useWorkAssistant";
import type { State } from "@/types";

export default function WorkAssistant({
  data,
  cid,
  onRefresh,
  onInvoice,
  initialPlanId,
}: {
  data: State;
  cid: number;
  onRefresh: () => Promise<void>;
  onInvoice: (id: number) => void;
  initialPlanId?: string;
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
  const [notice, setNotice] = useState("");
  function deleteConversation(conversation: Plan) {
    if (
      !confirm(
        "Delete this saved conversation? It will disappear from this list. Work entries, invoices, and linked billing review history will be retained. Your current work-description notes will not be cleared.",
      )
    )
      return;
    run(async () => {
      setNotice("");
      await request(
        `/assistant/plans/${encodeURIComponent(conversation.id)}?confirmed=true&version=${conversation.version}`,
        undefined,
        "DELETE",
      );
      if (plan?.id === conversation.id) {
        setPlan(undefined);
        setConfirmed(false);
      }
      await reload();
      setNotice("Conversation deleted. Work entries and invoices were preserved.");
    });
  }
  useEffect(() => {
    if (!initialPlanId) return;
    let active = true;
    request<Plan>(`/assistant/plans/${initialPlanId}`)
      .then((saved) => {
        if (active) {
          setPlan(saved);
          setConfirmed(false);
        }
      })
      .catch((failure) => active && setError(failure.message));
    return () => {
      active = false;
    };
  }, [initialPlanId]);
  const [inputMode, setInputMode] = useState<InputMode>("auto");
  const narrativeOnly = !provider && inputMode === "narrative";
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
        {!provider && (
          <AssistantInputMode
            history={history}
            source={source}
            mode={inputMode}
            onChange={setInputMode}
            busy={busy}
          />
        )}
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
            checked={prepareInvoice && !narrativeOnly}
            disabled={busy || narrativeOnly}
            onChange={(event) => setPrepareInvoice(event.target.checked)}
          />
          Prepare an invoice after logging the reviewed work
        </label>
        {prepareInvoice && !narrativeOnly && (
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
                input_mode: inputMode,
                prepare_invoice: prepareInvoice && !narrativeOnly,
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
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {!!history?.plans.length && (
        <section className="panel">
          <h2>Saved conversations</h2>
          <p className="small muted">
            Your latest {history.limits?.history ?? 50} plans. Source messages, edits, and receipts
            are stored in your database. Deleting a conversation keeps its saved work, invoices, and
            linked billing review history.
          </p>
          <div className="assistant-history">
            {history.plans.map((p) => (
              <div
                className={`assistant-history-row ${plan?.id === p.id ? "selected" : ""}`}
                key={p.id}
              >
                <button
                  className="assistant-history-item"
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
                    {p.status === "proposed" && !p.actions.some((action) => action._invoice_id)
                      ? " · Plan saved; no invoice yet — open to review or create draft"
                      : p.actions.some((action) => action._invoice_id)
                        ? " · Invoice draft linked"
                        : ""}
                  </small>
                </button>
                <button
                  className="text-btn assistant-history-delete"
                  disabled={busy}
                  aria-label={`Delete conversation: ${p.source.slice(0, 110)}`}
                  onClick={() => deleteConversation(p)}
                >
                  <Trash2 size={16} aria-hidden="true" /> Delete
                </button>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
