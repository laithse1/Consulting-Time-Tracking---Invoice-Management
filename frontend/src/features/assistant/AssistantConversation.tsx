import { useEffect, useState } from "react";
import type { Plan } from "./types";

interface Props {
  plan: Plan;
  provider: string;
  busy: boolean;
  onRevise: (instruction: string, consent: boolean) => Promise<boolean>;
  onReopen: () => void;
}

export function AssistantConversation({ plan, provider, busy, onRevise, onReopen }: Props) {
  const [instruction, setInstruction] = useState("");
  const [consent, setConsent] = useState(false);
  useEffect(() => {
    setInstruction("");
    setConsent(false);
  }, [plan.id]);
  useEffect(() => {
    setConsent(false);
  }, [provider]);
  return (
    <section className="assistant-conversation" aria-label="Plan corrections">
      <h3>Ask for corrections</h3>
      {plan.revisions?.map((revision) => (
        <div className="assistant-turn" key={revision.version}>
          <p>
            <strong>You:</strong> {revision.instruction}
          </p>
          <p>
            <strong>Assistant:</strong> {revision.reply}
          </p>
          <small className="muted">
            Saved revision {revision.version} · {revision.engine}
          </small>
        </div>
      ))}
      {plan.status === "applied" ? (
        <div>
          <p className="small muted">
            Reopen this saved draft to request corrections. Reapplying updates its existing work and
            invoice. Issued invoices and timer receipts cannot be reopened.
          </p>
          <button className="btn secondary" disabled={busy} onClick={onReopen}>
            Revise saved draft
          </button>
        </div>
      ) : (
        <>
          {plan.actions.some((action) => action._entry_id) && (
            <button className="btn secondary" disabled={busy} onClick={onReopen}>
              Reload saved work and discard pending edits
            </button>
          )}
          <p className="small muted">
            {provider
              ? "Describe a wording or factual correction and identify the task. Changes remain drafts for your review."
              : "Local corrections: Task 1 description: Refactored the server; Task 1 duration: 90m; Task 1 date: 2026-09-15. Send one correction at a time. Select a configured provider above for conversational rewriting."}
          </p>
          <label className="field">
            Correction message
            <textarea
              aria-label="Correction message"
              rows={3}
              maxLength={10000}
              value={instruction}
              disabled={busy}
              onChange={(event) => setInstruction(event.target.value)}
              placeholder={
                provider
                  ? "Make task 1 more concise and keep the PR reference."
                  : "Task 1 description: Refactored the server; PR #42."
              }
            />
          </label>
          {provider && (
            <label className="assistant-check">
              <input
                type="checkbox"
                checked={consent}
                disabled={busy}
                onChange={(event) => setConsent(event.target.checked)}
              />
              Send this plan, original notes, correction history, and this message to the selected
              provider.
            </label>
          )}
          <button
            className="btn secondary"
            disabled={busy || !instruction.trim() || (!!provider && !consent)}
            onClick={async () => {
              if (await onRevise(instruction, consent)) {
                setInstruction("");
                setConsent(false);
              }
            }}
          >
            Propose correction
          </button>
        </>
      )}
    </section>
  );
}
