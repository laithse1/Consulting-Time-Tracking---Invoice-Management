import { useState, useEffect, useRef } from "react";
import { applicationConfig } from "../config/application";
import type { WorkspacePage } from "../app/navigation";
import { request } from "@/lib/api/client";
import { today, monthEnd, weekStart } from "@/lib/dates";
import type { State, Entry, Week, Preview } from "../types";

/** Coordinates authenticated workspace loading and serialized mutations. */
export function useWorkspaceController() {
  const [token, setToken] = useState(sessionStorage.getItem("token") || "");
  const [connected, setConnected] = useState(false);
  const [data, setData] = useState<State>();
  const [page, setPage] = useState<WorkspacePage>("Dashboard");
  const [month, setMonth] = useState(today().slice(0, 7));
  const [cid, setCid] = useState(1);
  const [weeks, setWeeks] = useState<Week[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [dark, setDark] = useState(localStorage.getItem("theme") === "dark");
  const [modal, setModal] = useState("");
  const [edit, setEdit] = useState<Entry>();
  const [selected, setSelected] = useState<Preview>();
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("date");
  const [filter, setFilter] = useState("all");
  const [period, setPeriod] = useState("month");
  const [busy, setBusy] = useState(false);
  const actionPending = useRef(false);
  const refreshSequence = useRef(0);
  const [tick, setTick] = useState(Date.now());
  const from = month + "-01",
    to = month + "-" + monthEnd(month);
  const contract = data?.contracts.find((c) => c.id === cid);
  const projects = data?.projects.filter((p) => p.client_id === contract?.client_id) || [];
  async function refresh() {
    const sequence = ++refreshSequence.current;
    const authorization = sessionStorage.getItem("token");
    const d = await request<State>("/state");
    const w = await request<Week[]>(`/compliance?contract=${cid}&from=${from}&to=${to}`);
    if (
      !authorization ||
      sessionStorage.getItem("token") !== authorization ||
      sequence !== refreshSequence.current
    )
      return;
    setData(d);
    setConnected(true);
    setWeeks(w);
  }
  async function act(work: () => Promise<unknown>, message = "Saved") {
    if (actionPending.current) return;
    actionPending.current = true;
    setError("");
    setBusy(true);
    try {
      await work();
      await refresh();
      setNotice(message);
      setModal("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
      actionPending.current = false;
    }
  }
  useEffect(() => {
    if (token) {
      sessionStorage.setItem("token", token);
      refresh().catch((e) => setError(e.message));
    }
  }, [token, cid, month]);
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    localStorage.setItem("theme", dark ? "dark" : "light");
  }, [dark]);
  useEffect(() => {
    const h = setInterval(() => setTick(Date.now()), applicationConfig.timerTickMs);
    return () => clearInterval(h);
  }, []);
  useEffect(() => {
    if (notice) {
      const h = setTimeout(() => setNotice(""), applicationConfig.noticeDurationMs);
      return () => clearTimeout(h);
    }
  }, [notice]);
  const entries = data?.entries.filter((e) => e.contract_id === cid) || [];
  const actual = entries.filter(
    (e) => !e.draft && e.work_date && e.work_date >= from && e.work_date <= to,
  );
  const billable = actual.filter((e) => e.billable);
  const invoices = data?.invoices.filter((i) => i.contract_id === cid) || [];
  const monthlyHours = actual.reduce((a, e) => a + (e.seconds || 0) / 3600, 0);
  const currentWeek = weekStart(today(), contract?.week_start || 1);
  const weekHours = entries
    .filter(
      (e) =>
        !e.draft &&
        e.work_date &&
        weekStart(e.work_date, contract?.week_start || 1) === currentWeek,
    )
    .reduce((a, e) => a + (e.seconds || 0) / 3600, 0);
  const outstanding = invoices
    .filter((i) => i.snapshot && !["Replaced", "Withdrawn"].includes(i.state))
    .reduce((a, i) => a + Number(i.balance), 0);
  const timer = data?.timer[0];
  async function openInvoice(id: number) {
    try {
      setSelected(await request<Preview>("/invoices/" + id));
      setPage("Invoices");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function mutateInvoice(path: string, b: unknown) {
    if (!selected) return;
    const id = selected.invoice.id;
    await request("/invoices/" + id + path, b);
    setSelected(await request<Preview>("/invoices/" + id));
  }
  function entryForm(e?: Entry) {
    setEdit(e);
    setModal("entry");
  }

  return {
    token,
    setToken,
    connected,
    setConnected,
    data,
    setData,
    page,
    setPage,
    month,
    setMonth,
    cid,
    setCid,
    weeks,
    setWeeks,
    error,
    setError,
    notice,
    setNotice,
    dark,
    setDark,
    modal,
    setModal,
    edit,
    setEdit,
    selected,
    setSelected,
    query,
    setQuery,
    sort,
    setSort,
    filter,
    setFilter,
    period,
    setPeriod,
    busy,
    setBusy,
    actionPending,
    refreshSequence,
    tick,
    setTick,
    from,
    to,
    contract,
    projects,
    refresh,
    act,
    entries,
    actual,
    billable,
    invoices,
    monthlyHours,
    currentWeek,
    weekHours,
    outstanding,
    timer,
    openInvoice,
    mutateInvoice,
    entryForm,
  };
}
