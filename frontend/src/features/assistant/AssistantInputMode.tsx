import type { History } from "./types";

export type InputMode = "auto" | "lines" | "narrative";

export function AssistantInputMode({
  history,
  source,
  mode,
  onChange,
  busy,
}: {
  history?: History;
  source: string;
  mode: InputMode;
  onChange: (mode: InputMode) => void;
  busy: boolean;
}) {
  const lines = source.split(/\r?\n/).filter((line) => line.trim()).length;
  const limit = history?.limits?.actions ?? 30;
  const hasProvider = history?.providers.some((provider) => provider.configured);
  return (
    <div className="assistant-input-help">
      <label className="field">
        Local input style
        <select
          aria-label="Local input style"
          value={mode}
          disabled={busy}
          onChange={(event) => onChange(event.target.value as InputMode)}
        >
          <option value="auto">Automatic · tables, task lines, and retained notes</option>
          <option value="lines">Task lines · dates and actual durations</option>
          <option value="narrative">Narrative notes · no time entries or invoice</option>
        </select>
      </label>
      <p className={hasProvider ? "small muted" : "notice"}>
        Automatic mode recognizes allocation tables without treating proposed hours as actual time.
        Local rules do not reason over arbitrary prose. Task-line mode supports up to {limit}{" "}
        actions including invoice/report steps. Automatic mode preserves oversized messages as
        nonbillable notes without dropping lines or assuming hours.
      </p>
      {lines > limit && (
        <p className="notice" role="status">
          This message has {lines} nonempty lines. References and instructions can count as actions
          in task-line mode. Use Automatic or Narrative notes to keep the full text, or a configured
          provider to organize it into work items.
        </p>
      )}
      <p className="small muted">
        {hasProvider
          ? "For narrative interpretation and professional invoice wording, select an available provider in Processing above."
          : "For narrative interpretation, enable a profile in config/assistant-providers.json and reload this page. No configured provider is currently available; local rules remain offline."}
      </p>
    </div>
  );
}
