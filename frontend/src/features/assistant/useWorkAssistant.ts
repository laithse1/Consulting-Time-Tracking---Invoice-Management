import { request } from "@/lib/api/client";
import { today } from "@/lib/dates";
import { applicationConfig } from "@/config/application";
import type { State } from "@/types";
import { useEffect, useRef, useState } from "react";
import type { Action, History, Plan } from "./types";

export function useWorkAssistant(data: State, cid: number) {
  const [history, setHistory] = useState<History>();
  const [source, setSource] = useState("");
  const [saved, setSaved] = useState("");
  const [reference, setReference] = useState(today());
  const [provider, setProvider] = useState("");
  const [consent, setConsent] = useState(false);
  const [plan, setPlan] = useState<Plan>();
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saveState, setSaveState] = useState("");
  const pending = useRef(false);
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const contract = data.contracts.find((c) => c.id === cid);
  const projects = data.projects.filter((p) => p.client_id === contract?.client_id);
  useEffect(() => {
    let active = true;
    request<History>(`/assistant?contract=${cid}`)
      .then((h) => {
        if (!active) return;
        setHistory(h);
        setSource(h.draft.source);
        setSaved(h.draft.source);
        // The first ready profile is the configured default. Sending still requires consent.
        setProvider(h.providers.find((candidate) => candidate.configured)?.id ?? "");
        setConsent(false);
      })
      .catch((e) => active && setError(e.message));
    return () => {
      active = false;
    };
  }, [cid]);
  // Serialize saves so an older slow request cannot overwrite newer wording.
  function save(text: string) {
    const result = queue.current
      .catch(() => undefined)
      .then(() => request("/assistant/draft", { contract_id: cid, source: text }));
    queue.current = result;
    return result;
  }
  useEffect(() => {
    if (!history || source === saved) return;
    setSaveState("Saving notes…");
    let active = true;
    const timeout = setTimeout(() => {
      save(source)
        .then(() => {
          if (active) {
            setSaved(source);
            setSaveState("Notes saved to database");
          }
        })
        .catch((e) => {
          if (active) setSaveState("Save failed: " + e.message);
        });
    }, applicationConfig.assistantAutosaveMs);
    return () => {
      active = false;
      clearTimeout(timeout);
    };
  }, [source, saved, history]);
  async function run(work: () => Promise<void>) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      await work();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  async function reload(selected?: Plan) {
    const h = await request<History>(`/assistant?contract=${cid}`);
    setHistory(h);
    if (selected) setPlan(h.plans.find((p) => p.id === selected.id) || selected);
  }
  function edit(index: number, patch: Partial<Action>) {
    setConfirmed(false);
    setPlan(
      (p) =>
        p && {
          ...p,
          actions: p.actions.map((a, i) => (i === index ? { ...a, ...patch } : a)),
        },
    );
  }
  const locked = busy || plan?.status === "applied";

  const sourceLimit = history?.limits?.sourceCharacters ?? 10000;
  return {
    sourceLimit,
    history,
    setHistory,
    source,
    setSource,
    saved,
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
    setBusy,
    error,
    setError,
    saveState,
    setSaveState,
    pending,
    queue,
    contract,
    projects,
    save,
    run,
    reload,
    edit,
    locked,
  };
}
