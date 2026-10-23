import { Check } from "lucide-react";
import { request } from "@/lib/api/client";
import { AssistantActionCard } from "./AssistantActionCard";
import { AssistantResultCard } from "./AssistantResultCard";
import { AssistantConversation } from "./AssistantConversation";
import type { Plan } from "./types";
import type { useWorkAssistant } from "./useWorkAssistant";

interface Props {
  assistant: ReturnType<typeof useWorkAssistant>;
  onRefresh: () => Promise<void>;
  onInvoice: (id: number) => void;
}

export function AssistantPlanPanel({ assistant, onRefresh, onInvoice }: Props) {
  const {
    plan,
    source,
    busy,
    projects,
    locked,
    edit,
    provider,
    confirmed,
    setConfirmed,
    run,
    setPlan,
    reload,
  } = assistant;
  if (!plan) return null;
  return (
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
      {plan.review && (
        <div className="notice">
          <div>
            <strong>
              {Number(plan.review.hours).toFixed(2)} confirmed hours · $
              {Number(plan.review.servicesValue).toFixed(2)} in proposed services
            </strong>
            <p className="small">
              {plan.review.incompleteTasks} task(s) still need confirmation. {plan.review.note}
            </p>
            <p className="small">
              Save reviewed edits or request a correction to refresh these server-calculated
              figures.
            </p>
          </div>
        </div>
      )}
      <AssistantConversation
        key={plan.id}
        plan={plan}
        provider={provider}
        busy={busy}
        onReopen={() =>
          run(async () => {
            const reopened = await request<Plan>(`/assistant/plans/${plan.id}/reopen`, {
              version: plan.version,
            });
            setPlan(reopened);
            setConfirmed(false);
            await reload(reopened);
          })
        }
        onRevise={async (instruction, correctionConsent) => {
          let succeeded = false;
          await run(async () => {
            const savedPlan = await request<Plan>(
              `/assistant/plans/${plan.id}`,
              { version: plan.version, actions: plan.actions },
              "PUT",
            );
            setPlan(savedPlan);
            setConfirmed(false);
            const revised = await request<Plan>(`/assistant/plans/${plan.id}/revise`, {
              version: savedPlan.version,
              instruction,
              provider_id: provider,
              provider_consent: correctionConsent,
            });
            setPlan(revised);
            await reload(revised);
            succeeded = true;
          });
          return succeeded;
        }}
      />
      {plan.status !== "applied" ? (
        <>
          <button
            className="btn secondary"
            disabled={busy}
            onClick={() =>
              run(async () => {
                const savedPlan = await request<Plan>(
                  `/assistant/plans/${plan.id}`,
                  { version: plan.version, actions: plan.actions },
                  "PUT",
                );
                setPlan(savedPlan);
                setConfirmed(false);
                await reload(savedPlan);
              })
            }
          >
            Save reviewed edits
          </button>
          <label className="assistant-check">
            <input
              type="checkbox"
              aria-label="Confirm reviewed action plan"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              disabled={busy}
            />
            I reviewed this plan and confirm the facts. Apply these actions to the active contract.
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
  );
}
