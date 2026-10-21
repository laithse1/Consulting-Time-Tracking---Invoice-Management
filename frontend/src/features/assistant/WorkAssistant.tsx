import { ArrowUpRight, Check, MessageSquare } from "lucide-react";
import { request } from "@/lib/api/client";
import { AssistantActionCard } from "./AssistantActionCard";
import { AssistantResultCard } from "./AssistantResultCard";
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
    confirmed,
    setConfirmed,
    busy,
    error,
    setError,
    saveState,
    setSaveState,
    projects,
    save,
    run,
    reload,
    edit,
    locked,
  } = useWorkAssistant(data, cid);
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
          reports. Include actual dates, durations, and project names.
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
      {plan && (
        <section className="panel assistant-plan">
          <div className="section-head">
            <div>
              <span className="eyebrow">
                {plan.status === "applied" ? "SAVED RECEIPT" : "REVIEW BEFORE APPLYING"}
              </span>
              <h2>
                {plan.actions.length} proposed action
                {plan.actions.length === 1 ? "" : "s"}
              </h2>
            </div>
            <span className="badge">{plan.engine}</span>
          </div>
          {plan.source !== source && (
            <details>
              <summary>Original message for this plan</summary>
              <pre className="assistant-original">{plan.source}</pre>
            </details>
          )}
          {plan.actions.map((a, index) => (
            <AssistantActionCard
              key={index}
              action={a}
              index={index}
              projects={projects}
              locked={locked}
              onEdit={edit}
            />
          ))}
          {plan.status !== "applied" ? (
            <>
              <label className="assistant-check">
                <input
                  type="checkbox"
                  aria-label="Confirm reviewed action plan"
                  checked={confirmed}
                  onChange={(e) => setConfirmed(e.target.checked)}
                  disabled={busy}
                />
                I reviewed this plan and confirm the facts. Apply these actions to the active
                contract.
              </label>
              <button
                className="btn"
                disabled={busy || !confirmed}
                onClick={() =>
                  run(async () => {
                    const savedPlan = await request<Plan>(
                      `/assistant/plans/${plan.id}`,
                      { version: plan.version, actions: plan.actions },
                      "PUT",
                    );
                    setPlan(savedPlan);
                    const applied = await request<Plan>(`/assistant/plans/${plan.id}/apply`, {
                      version: savedPlan.version,
                      confirmed: true,
                    });
                    setPlan(applied);
                    setConfirmed(false);
                    await reload(applied);
                    await onRefresh();
                  })
                }
              >
                <Check size={16} />
                {busy ? "Applying…" : "Apply reviewed plan"}
              </button>
              <p className="small muted">
                The entire plan saves together. Retrying an applied plan returns its saved receipt.
              </p>
            </>
          ) : (
            <div className="notice">
              <Check size={16} />
              Plan applied. Saved records are shown below.
            </div>
          )}
          {plan.results?.map((r, i) => (
            <AssistantResultCard key={i} result={r} onInvoice={onInvoice} />
          ))}
        </section>
      )}
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
