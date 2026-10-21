function integerSetting(
  name: string,
  value: string | undefined,
  fallback: number,
  minimum: number,
  maximum: number,
) {
  if (value === undefined || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name} must be an integer between ${minimum} and ${maximum}`);
  }
  return parsed;
}

const environment = import.meta.env;

/** Public build settings only. Credentials belong to the backend configuration. */
export const applicationConfig = Object.freeze({
  title: environment.VITE_APP_TITLE || "Consulting Invoice Manager",
  workspaceLabel: environment.VITE_WORKSPACE_LABEL || "Independent consulting",
  apiBaseUrl: (environment.VITE_API_BASE_URL || "").replace(/\/$/, ""),
  assistantAutosaveMs: integerSetting(
    "VITE_ASSISTANT_AUTOSAVE_MS",
    environment.VITE_ASSISTANT_AUTOSAVE_MS,
    600,
    100,
    10000,
  ),
  timerTickMs: integerSetting(
    "VITE_TIMER_TICK_MS",
    environment.VITE_TIMER_TICK_MS,
    1000,
    100,
    10000,
  ),
  noticeDurationMs: integerSetting(
    "VITE_NOTICE_DURATION_MS",
    environment.VITE_NOTICE_DURATION_MS,
    5000,
    1000,
    60000,
  ),
});
