import { Check } from "lucide-react";
import { request } from "@/lib/api/client";
import { AssistantActionCard } from "./AssistantActionCard";
import { AssistantResultCard } from "./AssistantResultCard";
import { AssistantConversation } from "./AssistantConversation";
import { AssistantInvoiceDraft } from "./AssistantInvoiceDraft";
import type { Preview } from "@/types";
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
  const notesOnly =
    plan.actions.length > 0 && plan.actions.every((action) => action.type === "save_note");
  const allocations = plan.actions.filter((action) => action.proposed_hours !== undefined);
  const proposedHours = allocations.reduce((sum, action) => sum + Number(action.proposed_hours), 0);
  const plannedHours = allocations
    .filter((action) => action.work_state === "planned")
    .reduce((sum, action) => sum + Number(action.proposed_hours), 0);
  const unresolvedBilling =
    plan.actions.some((action) => action.type === "draft_invoice" && !action.skip) &&
    plan.actions.some((action) => action.requires_resolution && !action.resolved);
  return (
    <section className="panel assistant-plan">
      {plan.deleted_at && (
        <p className="callout">
          This conversation was deleted from saved history. Its linked work and billing review
          information remain available here for invoice review and corrections.
        </p>
      )}
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
      {notesOnly && (
        <div className="notice assistant-intake-notice" role="status">
          Narrative retained as notes only. No time entries or invoice will be created. To organize
          it into work items, select a configured provider above and review a new plan from the
          original notes, or use smaller messages with actual dated task lines.
        </div>
      )}
      {allocations.length > 0 && (
        <div className="notice assistant-intake-notice" role="status">
          <strong>
            {proposedHours} proposed hours, including {plannedHours} planned hours.
          </strong>
          <p>
            {proposedHours - plannedHours} hours are allocated to earlier periods; they remain
            unconfirmed. No allocation has been converted to actual time. The original table and its
            references are retained. Tables are processed locally without a provider request.
          </p>
          <p>
            Confirm actual dates, hours, and projects below. Keep future work as notes. Resolve the
            billing questions and include invoice preparation when ready.
          </p>
        </div>
      )}
      {!notesOnly && (
        <AssistantInvoiceDraft
          key={`${plan.id}-${plan.version}`}
          plan={plan}
          busy={busy}
          onOpen={onInvoice}
          onCreate={(from, to) =>
            run(async () => {
              const saved =
                plan.status === "applied"
                  ? plan
                  : await request<Plan>(
                      `/assistant/plans/${plan.id}`,
                      { version: plan.version, actions: plan.actions },
                      "PUT",
                    );
              setPlan(saved);
              setConfirmed(false);
              const result = await request<{ plan: Plan; preview: Preview }>(
                `/assistant/plans/${plan.id}/invoice`,
                { version: saved.version, period_start: from, period_end: to },
              );
              setPlan(result.plan);
              await reload(result.plan);
              await onRefresh();
              onInvoice(result.preview.invoice.id);
            })
          }
        />
      )}
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
          onSplit={
            plan.status === "proposed"
              ? (index) =>
                  run(async () => {
                    const saved = await request<Plan>(
                      `/assistant/plans/${plan.id}`,
                      { version: plan.version, actions: plan.actions },
                      "PUT",
                    );
                    setPlan(saved);
                    setConfirmed(false);
                    const split = await request<Plan>(`/assistant/plans/${plan.id}/split`, {
                      version: saved.version,
                      action_index: index,
                    });
                    setPlan(split);
                    await reload(split);
                  })
              : undefined
          }
        />
      ))}
      {plan.review && !notesOnly && (
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
          {unresolvedBilling && (
            <p className="notice" role="alert">
              Resolve the allocation and billing questions below, or exclude invoice preparation to
              save drafts first.
            </p>
          )}
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
            {notesOnly
              ? "Keep the original narrative in this conversation as nonbillable notes."
              : "I reviewed this plan and confirm the facts. Apply these actions to the active contract."}
          </label>
          <button
            className="btn"
            disabled={busy || !confirmed || unresolvedBilling}
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
            {busy ? "Applying…" : notesOnly ? "Save narrative notes" : "Apply reviewed plan"}
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
