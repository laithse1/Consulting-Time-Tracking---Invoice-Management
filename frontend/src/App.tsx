import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import * as Dialog from "@radix-ui/react-dialog";
import {
  LayoutDashboard,
  Timer,
  CalendarDays,
  FolderKanban,
  FileText,
  Wallet,
  ChartNoAxesCombined,
  Building2,
  Settings,
  Plus,
  ArrowUpRight,
  Play,
  Pause,
  Square,
  Sun,
  Moon,
  Check,
  Download,
  Search,
  LogOut,
  X,
  Clock,
  ChevronRight,
} from "lucide-react";
import {
  request,
  download,
  today,
  monthEnd,
  usd,
  weekStart,
  categories,
  duration,
} from "./api";
import type { State, Entry, Week, Preview, Invoice } from "./types";
const nav = [
  ["Dashboard", LayoutDashboard],
  ["Time Tracker", Timer],
  ["Timesheets", CalendarDays],
  ["Projects", FolderKanban],
  ["Invoices", FileText],
  ["Payments", Wallet],
  ["Reports", ChartNoAxesCombined],
  ["Clients & Contracts", Building2],
  ["Settings", Settings],
] as const;
type Fields = Record<string, string | number | null | undefined>;
const BusyContext = createContext(false);
function formData(e: FormEvent<HTMLFormElement>): Fields {
  e.preventDefault();
  return Object.fromEntries(new FormData(e.currentTarget).entries()) as Fields;
}
function Input({
  label,
  name,
  value,
  type = "text",
  required = false,
  ...rest
}: {
  label: string;
  name: string;
  value?: string | number | null;
  type?: string;
  required?: boolean;
  min?: string;
  max?: string;
  step?: string;
}) {
  return (
    <label className="field">
      {label}
      <input
        name={name}
        type={type}
        defaultValue={value ?? ""}
        required={required}
        {...rest}
      />
    </label>
  );
}
function Button({
  children,
  onClick,
  secondary = false,
  type = "button",
}: {
  children: ReactNode;
  onClick?: () => void;
  secondary?: boolean;
  type?: "button" | "submit";
}) {
  return (
    <button
      type={type}
      disabled={useContext(BusyContext)}
      className={secondary ? "btn secondary" : "btn"}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
export function Badge({ children }: { children: ReactNode }) {
  return (
    <span
      className={
        "badge " +
        (String(children).match(/Paid|Within|Approved/)
          ? "green"
          : String(children).match(/Overdue|Disputed|required/)
            ? "red"
            : "")
      }
    >
      {children}
    </span>
  );
}
function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <Dialog.Root open onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="overlay" />
        <Dialog.Content className="modal">
          <div className="section-head">
            <Dialog.Title>{title}</Dialog.Title>
            <Dialog.Close aria-label="Close" className="icon">
              <X size={20} />
            </Dialog.Close>
          </div>
          <Dialog.Description className="muted">
            Review the details before saving.
          </Dialog.Description>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
export default function App() {
  const [token, setToken] = useState(sessionStorage.getItem("token") || "");
  const [connected, setConnected] = useState(false);
  const [data, setData] = useState<State>();
  const [page, setPage] = useState("Dashboard");
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
  const projects =
    data?.projects.filter((p) => p.client_id === contract?.client_id) || [];
  async function refresh() {
    const sequence = ++refreshSequence.current;
    const authorization = sessionStorage.getItem("token");
    const d = await request<State>("/state");
    const w = await request<Week[]>(
      `/compliance?contract=${cid}&from=${from}&to=${to}`,
    );
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
    const h = setInterval(() => setTick(Date.now()), 1000);
    return () => clearInterval(h);
  }, []);
  useEffect(() => {
    if (notice) {
      const h = setTimeout(() => setNotice(""), 5000);
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
  if (!connected || !data)
    return (
      <div className="login">
        <div className="brandmark">
          ci<span>m</span>
        </div>
        <h1>Consulting Invoice Manager</h1>
        <p className="muted">Your work, accounted for.</p>
        <form
          onSubmit={(e) => {
            const b = formData(e);
            setToken(String(b.token));
          }}
        >
          <Input
            label="Local access token"
            name="token"
            type="password"
            required
          />
          <Button type="submit">
            Unlock workspace <ArrowUpRight size={16} />
          </Button>
        </form>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        <p className="muted small">
          Use APP_TOKEN from your local configuration.
        </p>
      </div>
    );
  return (
    <BusyContext.Provider value={busy}>
      <div className="app">
        <aside>
          <a className="brand" href="#" onClick={() => setPage("Dashboard")}>
            <div className="brandmark">
              ci<span>m</span>
            </div>
            <div>
              Consulting<span>Invoice Manager</span>
            </div>
          </a>
          <div className="workspace">
            <span className="avatar">LA</span>
            <div>
              Laith’s workspace<small>Independent consulting</small>
            </div>
          </div>
          <div className="navlabel">WORKSPACE</div>
          <nav>
            {nav.map(([name, Icon]) => (
              <button
                key={name}
                className={page === name ? "active" : ""}
                onClick={() => {
                  setPage(name);
                  setSelected(undefined);
                }}
              >
                <Icon size={18} />
                {name}
                {name === "Invoices" && (
                  <small>
                    {
                      invoices.filter(
                        (i) =>
                          i.state === "Draft" || i.state === "Ready for Review",
                      ).length
                    }
                  </small>
                )}
              </button>
            ))}
          </nav>
          <div className="aside-bottom">
            <div className="local">
              <span />
              Local workspace <Badge>Private</Badge>
            </div>
            <button
              onClick={() => {
                sessionStorage.removeItem("token");
                setToken("");
                setConnected(false);
                setData(undefined);
              }}
            >
              <LogOut size={16} />
              Lock workspace
            </button>
          </div>
        </aside>
        <main>
          <header>
            <div className="breadcrumb">
              Workspace <ChevronRight size={14} />
              <strong>{page}</strong>
            </div>
            <div className="header-actions">
              <select
                aria-label="Active contract"
                value={cid}
                onChange={(e) => setCid(Number(e.target.value))}
              >
                {data.contracts.map((c) => (
                  <option key={c.id} value={c.id}>
                    {data.clients.find((cl) => cl.id === c.client_id)?.name} ·{" "}
                    {c.contractor}
                  </option>
                ))}
              </select>
              <button
                className="icon"
                aria-label="Toggle theme"
                onClick={() => setDark(!dark)}
              >
                {dark ? <Sun size={19} /> : <Moon size={19} />}
              </button>
              <span className="avatar">LA</span>
            </div>
          </header>
          <div className="content">
            <div className="page-head">
              <div>
                <div className="eyebrow">CONSULTING WORKSPACE</div>
                <h1>
                  {page === "Dashboard" ? "A clear view of your work." : page}
                </h1>
                <p className="muted">
                  {page === "Dashboard"
                    ? "Track your time, stay on top of invoices, and keep business moving."
                    : page === "Invoices"
                      ? "Turn confirmed work into client-ready invoices."
                      : page === "Timesheets"
                        ? "Review actual hours and weekly contract commitments."
                        : "Manage your consulting business with confidence."}
                </p>
              </div>
              <div className="actions">
                <input
                  aria-label="Reporting month"
                  type="month"
                  value={month}
                  onChange={(e) => setMonth(e.target.value)}
                />
                <Button onClick={() => entryForm()}>
                  <Plus size={17} />
                  Log time
                </Button>
              </div>
            </div>
            {error && (
              <div role="alert" className="error">
                {error}
                <button className="icon" onClick={() => setError("")}>
                  <X size={16} />
                </button>
              </div>
            )}
            {notice && (
              <div role="status" className="notice">
                <Check size={16} />
                {notice}
              </div>
            )}
            {busy && (
              <p role="status" className="muted">
                Saving…
              </p>
            )}
            {page === "Dashboard" && (
              <>
                <div className="stats">
                  <Stat
                    label="Hours this week"
                    value={weekHours.toFixed(2)}
                    suffix="hrs"
                    detail={`${contract?.minimum_hours}–${contract?.maximum_hours} billable hours expected`}
                  />
                  <Stat
                    label="Hours this month"
                    value={monthlyHours.toFixed(2)}
                    suffix="hrs"
                    detail={`${billable.reduce((a, e) => a + (e.seconds || 0) / 3600, 0).toFixed(2)} billable hours`}
                  />
                  <Stat
                    label="Monthly billable value"
                    value={usd(
                      billable.reduce(
                        (a, e) =>
                          a +
                          ((e.seconds || 0) / 3600) * Number(contract?.rate),
                        0,
                      ),
                    )}
                    detail={`${usd(contract?.rate)} / hour · actual work only`}
                  />
                  <Stat
                    label="Outstanding receivables"
                    value={usd(outstanding)}
                    detail={`${invoices.filter((i) => i.overdue).length} overdue invoices`}
                  />
                </div>
                <div className="dashboard-grid">
                  <section className="panel">
                    <div className="section-head">
                      <div>
                        <h2>Weekly contract progress</h2>
                        <p className="muted small">
                          Actual billable hours · full weeks, including month
                          boundaries
                        </p>
                      </div>
                      <Badge>
                        {contract?.minimum_hours}–{contract?.maximum_hours} hrs
                        / week
                      </Badge>
                    </div>
                    {weeks.map((w) => (
                      <div className="week-row" key={w.week}>
                        <div className="section-head">
                          <strong>Week of {w.week}</strong>
                          <span>
                            {Number(w.hours).toFixed(2)}{" "}
                            <span className="muted">/ {w.maximum} hrs</span>
                          </span>
                        </div>
                        <div className="progress">
                          <div
                            style={{
                              width:
                                Math.min(
                                  100,
                                  (Number(w.hours) / w.maximum) * 100,
                                ) + "%",
                              background: w.unresolved
                                ? "var(--amber)"
                                : "var(--green)",
                            }}
                          />
                        </div>
                        <div className="section-head small">
                          <span className="muted">{w.status}</span>
                          {w.unresolved && (
                            <button
                              className="text-btn"
                              onClick={() => {
                                setPage("Timesheets");
                              }}
                            >
                              Review <ArrowUpRight size={12} />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                    <p className="callout">
                      Assignment shortfalls are planning issues. They never
                      become billable time automatically.
                    </p>
                  </section>
                  <section className="panel workflow">
                    <div className="section-head">
                      <h2>Your next steps</h2>
                      <Clock size={18} />
                    </div>
                    <Step
                      number="01"
                      title="Confirm your draft activities"
                      text={`${entries.filter((e) => e.draft).length} activities need dates and actual durations.`}
                      onClick={() => setPage("Time Tracker")}
                    />
                    <Step
                      number="02"
                      title="Review weekly hours"
                      text={`${weeks.filter((w) => w.unresolved).length} weeks need clarification or approval.`}
                      onClick={() => setPage("Timesheets")}
                    />
                    <Step
                      number="03"
                      title="Prepare your monthly invoice"
                      text={`${invoices.filter((i) => i.state === "Draft" || i.state === "Ready for Review").length} invoices awaiting submission.`}
                      onClick={() => setPage("Invoices")}
                    />
                    <div className="paid">
                      <span className="muted small">PAID YEAR TO DATE</span>
                      <strong>
                        {usd(
                          data.payments
                            .filter(
                              (p) =>
                                p.received.startsWith(today().slice(0, 4)) &&
                                invoices.some((i) => i.id === p.invoice_id),
                            )
                            .reduce((a, p) => a + Number(p.amount), 0),
                        )}
                      </strong>
                      <span className="muted small">
                        Recurring consulting payments
                      </span>
                    </div>
                  </section>
                </div>
                <section className="panel">
                  <div className="section-head">
                    <h2>Recent activity</h2>
                    <button
                      className="text-btn"
                      onClick={() => setPage("Time Tracker")}
                    >
                      View all time <ArrowUpRight size={14} />
                    </button>
                  </div>
                  <EntryTable
                    entries={entries.slice(0, 5)}
                    projects={data.projects}
                    onEdit={entryForm}
                  />
                </section>
              </>
            )}
            {(page === "Time Tracker" || page === "Timesheets") && (
              <>
                <section className="panel timer-panel">
                  <div>
                    <span className="eyebrow">
                      {timer ? "ACTIVE TIMER" : "READY WHEN YOU ARE"}
                    </span>
                    <h2>{timer?.description || "Make every hour count."}</h2>
                    <p className="muted small">
                      Stop creates a draft for you to review before billing.
                    </p>
                  </div>
                  <div className="actions">
                    {timer ? (
                      <>
                        <strong className="timer-display">
                          {duration(
                            timer.elapsed +
                              (timer.started
                                ? Math.max(
                                    0,
                                    Math.floor(
                                      (tick - Date.parse(timer.started)) / 1000,
                                    ),
                                  )
                                : 0),
                          )}
                        </strong>
                        <Button
                          secondary
                          onClick={() =>
                            act(() =>
                              request(
                                "/timer/" +
                                  (timer.started ? "pause" : "resume"),
                                {},
                              ),
                            )
                          }
                        >
                          {timer.started ? (
                            <Pause size={16} />
                          ) : (
                            <Play size={16} />
                          )}{" "}
                          {timer.started ? "Pause" : "Resume"}
                        </Button>
                        <Button
                          onClick={() =>
                            act(
                              () => request("/timer/stop", {}),
                              "Timer saved as draft",
                            )
                          }
                        >
                          <Square size={14} />
                          Stop
                        </Button>
                      </>
                    ) : (
                      <Button onClick={() => setModal("timer")}>
                        <Play size={16} />
                        Start timer
                      </Button>
                    )}
                  </div>
                </section>
                {page === "Timesheets" && (
                  <section className="panel">
                    <div className="section-head">
                      <h2>Weekly compliance</h2>
                      <span className="muted small">
                        Minimum is never automatically billed.
                      </span>
                    </div>
                    <div className="table-wrap">
                      <table>
                        <thead>
                          <tr>
                            <th>Week starting</th>
                            <th>Actual billable</th>
                            <th>Minimum</th>
                            <th>Difference</th>
                            <th>Status</th>
                            <th />
                          </tr>
                        </thead>
                        <tbody>
                          {weeks.map((w) => (
                            <tr key={w.week}>
                              <td>
                                {w.week}
                                {w.partial && (
                                  <small>Partial contract week</small>
                                )}
                              </td>
                              <td>{Number(w.hours).toFixed(2)} hrs</td>
                              <td>{w.minimum} hrs</td>
                              <td>{Number(w.difference).toFixed(2)} hrs</td>
                              <td>
                                <Badge>{w.status}</Badge>
                              </td>
                              <td>
                                <button
                                  className="text-btn"
                                  onClick={() => setModal("week:" + w.week)}
                                >
                                  Review
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </section>
                )}
                <section className="panel">
                  <div className="section-head">
                    <h2>Work entries</h2>
                    <div className="actions">
                      <Search size={16} />
                      <input
                        aria-label="Search entries"
                        placeholder="Search work, references…"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                      />
                      <select
                        aria-label="Entry filter"
                        value={filter}
                        onChange={(e) => setFilter(e.target.value)}
                      >
                        <option value="all">All entries</option>
                        <option value="draft">Drafts</option>
                        <option value="billable">Billable</option>
                        <option value="nonbillable">Nonbillable</option>
                      </select>
                      <select
                        aria-label="Timesheet period"
                        value={period}
                        onChange={(e) => setPeriod(e.target.value)}
                      >
                        <option value="month">Selected month + drafts</option>
                        <option value="week">Current week + drafts</option>
                        <option value="all">All dates</option>
                      </select>
                      <select
                        aria-label="Sort entries"
                        value={sort}
                        onChange={(e) => setSort(e.target.value)}
                      >
                        <option value="date">Newest date</option>
                        <option value="duration">Longest duration</option>
                        <option value="project">Project</option>
                      </select>
                    </div>
                  </div>
                  <EntryTable
                    entries={entries
                      .filter(
                        (e) =>
                          (
                            e.description +
                            " " +
                            e.repository +
                            " " +
                            e.reference +
                            " " +
                            e.category
                          )
                            .toLowerCase()
                            .includes(query.toLowerCase()) &&
                          (filter === "all" ||
                            (filter === "draft" && !!e.draft) ||
                            (filter === "billable" && !!e.billable) ||
                            (filter === "nonbillable" && !e.billable)) &&
                          (period === "all" ||
                            e.draft ||
                            (period === "week"
                              ? e.work_date &&
                                weekStart(
                                  e.work_date,
                                  contract?.week_start || 1,
                                ) === currentWeek
                              : e.work_date &&
                                e.work_date >= from &&
                                e.work_date <= to)),
                      )
                      .sort((a, b) =>
                        sort === "duration"
                          ? (b.seconds || 0) - (a.seconds || 0)
                          : sort === "project"
                            ? (a.project_id || 0) - (b.project_id || 0)
                            : (b.work_date || "").localeCompare(
                                a.work_date || "",
                              ),
                      )}
                    projects={data.projects}
                    onEdit={entryForm}
                  />
                </section>
              </>
            )}
            {page === "Projects" && (
              <>
                <div className="section-head">
                  <h2>Client projects</h2>
                  <Button onClick={() => setModal("project")}>
                    <Plus size={16} />
                    New project
                  </Button>
                </div>
                <div className="project-grid">
                  {projects.map((p) => (
                    <section className="panel" key={p.id}>
                      <FolderKanban size={24} className="project-icon" />
                      <h2>{p.name}</h2>
                      <p className="muted small">
                        {p.repository || "Add a repository reference"}
                      </p>
                      <strong>
                        {(
                          actual
                            .filter((e) => e.project_id === p.id)
                            .reduce((a, e) => a + (e.seconds || 0), 0) / 3600
                        ).toFixed(2)}{" "}
                        hrs
                      </strong>
                      <small className="muted"> in selected month</small>
                    </section>
                  ))}
                </div>
              </>
            )}
            {page === "Invoices" && (
              <>
                {selected ? (
                  <>
                    <div className="section-head">
                      <button
                        className="text-btn"
                        onClick={() => setSelected(undefined)}
                      >
                        ← All invoices
                      </button>
                      <Badge>{selected.invoice.state}</Badge>
                    </div>
                    <div className="dashboard-grid">
                      <section className="panel invoice-preview">
                        <div className="invoice-mast">
                          <div>
                            <div className="eyebrow">
                              {selected.invoice.snapshot
                                ? "INVOICE"
                                : "DRAFT INVOICE"}
                            </div>
                            <h2>{selected.invoice.number}</h2>
                            <p>{selected.contract.contractor}</p>
                            <p className="muted small">
                              {selected.contract.contractor_address}
                            </p>
                          </div>
                          <div>
                            <strong>{selected.client.name}</strong>
                            <p className="small">{selected.client.email}</p>
                            <p className="small">{selected.client.address}</p>
                          </div>
                        </div>
                        <div className="invoice-dates">
                          <div>
                            <small>INVOICE DATE</small>
                            {selected.invoice.invoice_date}
                          </div>
                          <div>
                            <small>SERVICE PERIOD</small>
                            {selected.invoice.period_start} –{" "}
                            {selected.invoice.period_end}
                          </div>
                        </div>
                        <EntryTable
                          entries={selected.entries}
                          projects={data.projects}
                          onEdit={() => {}}
                          readonly
                        />
                        {selected.expenses.map((e) => (
                          <div className="total-row" key={e.id}>
                            <span>
                              {e.description} · approved {e.approval_ref}
                            </span>
                            <strong>{usd(e.amount)}</strong>
                          </div>
                        ))}
                        <div className="totals">
                          <div className="total-row">
                            <span>Billable hours</span>
                            <strong>{Number(selected.hours).toFixed(4)}</strong>
                          </div>
                          <div className="total-row">
                            <span>
                              Professional services ·{" "}
                              {usd(selected.contract.rate)}/hr
                            </span>
                            <strong>{usd(selected.subtotal)}</strong>
                          </div>
                          <div className="total-row">
                            <span>Approved expenses</span>
                            <strong>{usd(selected.expenseTotal)}</strong>
                          </div>
                          {Number(selected.invoice.adjustment) > 0 && (
                            <div className="total-row">
                              <span>
                                Approved adjustment ·{" "}
                                {selected.invoice.adjustment_reason}
                              </span>
                              <strong>
                                {usd(selected.invoice.adjustment)}
                              </strong>
                            </div>
                          )}
                          <div className="total-row grand">
                            <span>Total due</span>
                            <strong>{usd(selected.total)}</strong>
                          </div>
                        </div>
                        <p className="muted small">
                          Approved invoices payable within{" "}
                          {selected.contract.payment_days} days of receipt.
                          Dispute period: {selected.contract.dispute_days} days.
                        </p>
                        <p className="small">
                          {selected.contract.payment_instructions}
                        </p>
                      </section>
                      <section className="panel">
                        <h2>Review & submission</h2>
                        {selected.warnings.length ? (
                          <div className="validation">
                            <strong>Needs your attention</strong>
                            <ul>
                              {selected.warnings.map((w) => (
                                <li key={w}>{w}</li>
                              ))}
                            </ul>
                          </div>
                        ) : (
                          <p className="notice">
                            <Check size={16} />
                            Validation passed
                          </p>
                        )}
                        <div className="stack">
                          {["pdf", "xlsx", "csv"].map((f) => (
                            <Button
                              secondary
                              key={f}
                              onClick={() =>
                                act(
                                  () =>
                                    download(
                                      `/invoices/${selected.invoice.id}/export/${f}`,
                                      selected.invoice.number + "." + f,
                                    ),
                                  "Export downloaded",
                                )
                              }
                            >
                              <Download size={16} />
                              {f === "xlsx" ? "Excel" : f.toUpperCase()}{" "}
                              download
                            </Button>
                          ))}
                          <Button
                            secondary
                            onClick={() =>
                              act(
                                () =>
                                  download(
                                    `/invoices/${selected.invoice.id}/export/pdf`,
                                    "",
                                    true,
                                  ),
                                "PDF opened",
                              )
                            }
                          >
                            Preview PDF <ArrowUpRight size={16} />
                          </Button>
                          {!selected.invoice.snapshot ? (
                            <Button
                              onClick={() => {
                                if (
                                  confirm(
                                    "Finalize this invoice? Its contents will be locked. Confirm you reviewed all work, expenses and approvals.",
                                  )
                                )
                                  act(
                                    () =>
                                      mutateInvoice("/finalize", {
                                        confirmed: true,
                                      }),
                                    "Invoice finalized",
                                  );
                              }}
                            >
                              Confirm & finalize
                            </Button>
                          ) : (
                            <>
                              {!["Replaced", "Withdrawn"].includes(
                                selected.invoice.state,
                              ) && (
                                <>
                                  <Button
                                    secondary
                                    onClick={() => setModal("email")}
                                  >
                                    Prepare submission email
                                  </Button>
                                  {!selected.invoice.submitted ? (
                                    <Button onClick={() => setModal("sent")}>
                                      Mark as sent
                                    </Button>
                                  ) : (
                                    <>
                                      <Button
                                        secondary
                                        onClick={() =>
                                          act(
                                            () =>
                                              mutateInvoice("/status", {
                                                state: "Approved",
                                              }),
                                            "Approval recorded",
                                          )
                                        }
                                      >
                                        Mark approved
                                      </Button>
                                      <Button
                                        secondary
                                        onClick={() => setModal("dispute")}
                                      >
                                        Record dispute
                                      </Button>
                                      <Button
                                        onClick={() => setModal("payment")}
                                      >
                                        Record payment
                                      </Button>
                                    </>
                                  )}
                                  <Button
                                    secondary
                                    onClick={() => setModal("withdraw")}
                                  >
                                    Withdraw unpaid invoice for correction
                                  </Button>
                                </>
                              )}
                              {selected.invoice.state === "Withdrawn" && (
                                <>
                                  <p className="callout">
                                    Original snapshot preserved. Correct the
                                    source entries under Time Tracker, then
                                    create a replacement. Notify accounting
                                    manually if this invoice was already
                                    submitted.
                                  </p>
                                  <Button
                                    secondary
                                    onClick={() => setModal("replacement")}
                                  >
                                    Create documented replacement
                                  </Button>
                                </>
                              )}
                            </>
                          )}
                        </div>
                        <p className="muted small">
                          Invoices are sent manually. Download the PDF, attach
                          it to your email, then record the actual submission
                          date.
                        </p>
                      </section>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="section-head">
                      <h2>All invoices</h2>
                      <div className="actions">
                        <Button secondary onClick={() => setModal("expense:0")}>
                          Add expense
                        </Button>
                        <Button onClick={() => setModal("invoice")}>
                          <Plus size={16} />
                          Create invoice
                        </Button>
                      </div>
                    </div>
                    <section className="panel">
                      <InvoiceTable invoices={invoices} onOpen={openInvoice} />
                    </section>
                    <section className="panel">
                      <h2>Reimbursable expenses</h2>
                      <div className="table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>Date</th>
                              <th>Description</th>
                              <th>Amount</th>
                              <th>Prior approval reference</th>
                              <th />
                            </tr>
                          </thead>
                          <tbody>
                            {data.expenses
                              .filter((e) => e.contract_id === cid)
                              .map((e) => (
                                <tr key={e.id}>
                                  <td>{e.expense_date}</td>
                                  <td>{e.description}</td>
                                  <td>{usd(e.amount)}</td>
                                  <td>
                                    {e.approval_ref ||
                                      "Unapproved — blocks finalization"}
                                  </td>
                                  <td>
                                    <button
                                      className="text-btn"
                                      onClick={() =>
                                        setModal("expense:" + e.id)
                                      }
                                    >
                                      Review
                                    </button>
                                  </td>
                                </tr>
                              ))}
                          </tbody>
                        </table>
                      </div>
                    </section>
                  </>
                )}
              </>
            )}
            {page === "Payments" && (
              <>
                <div className="stats">
                  <Stat
                    label="Outstanding balance"
                    value={usd(outstanding)}
                    detail="Excludes replaced invoices"
                  />
                  <Stat
                    label="Received"
                    value={usd(
                      data.payments
                        .filter((p) =>
                          invoices.some((i) => i.id === p.invoice_id),
                        )
                        .reduce((a, p) => a + Number(p.amount), 0),
                    )}
                    detail="Recurring invoices"
                  />
                  <Stat
                    label="Overdue"
                    value={String(invoices.filter((i) => i.overdue).length)}
                    detail="Based on actual submission date"
                  />
                </div>
                <section className="panel">
                  <div className="section-head">
                    <h2>Invoice balances</h2>
                    <Button
                      secondary
                      onClick={() =>
                        act(
                          () => download("/history.csv", "bookkeeping.csv"),
                          "History exported",
                        )
                      }
                    >
                      <Download size={16} />
                      Export history
                    </Button>
                  </div>
                  <InvoiceTable invoices={invoices} onOpen={openInvoice} />
                </section>
                <section className="panel">
                  <h2>Payment history</h2>
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Received</th>
                          <th>Invoice</th>
                          <th>Amount</th>
                          <th>Method</th>
                          <th>Reference</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.payments
                          .filter((p) =>
                            invoices.some((i) => i.id === p.invoice_id),
                          )
                          .map((p) => (
                            <tr key={p.id}>
                              <td>{p.received}</td>
                              <td>
                                {
                                  invoices.find((i) => i.id === p.invoice_id)
                                    ?.number
                                }
                              </td>
                              <td>{usd(p.amount)}</td>
                              <td>{p.method}</td>
                              <td>{p.reference}</td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </section>
                <section className="panel">
                  <div className="section-head">
                    <div>
                      <h2>Historical prior-work payment</h2>
                      <p className="muted">
                        Separate from recurring consulting invoices and
                        dashboard totals.
                      </p>
                    </div>
                    <strong>{usd(data.historical[0]?.amount)}</strong>
                  </div>
                  <p>
                    {data.historical[0]?.received
                      ? `Received ${data.historical[0].received} · ${data.historical[0].reference}`
                      : "Receipt not confirmed."}
                  </p>
                  {!data.historical[0]?.received && (
                    <Button secondary onClick={() => setModal("historical")}>
                      Record actual receipt
                    </Button>
                  )}
                </section>
              </>
            )}
            {page === "Reports" && (
              <>
                <section className="panel">
                  <div className="section-head">
                    <h2>Actual work · {month}</h2>
                    <Button
                      secondary
                      onClick={() =>
                        act(
                          () => download("/history.csv", "bookkeeping.csv"),
                          "History exported",
                        )
                      }
                    >
                      Export invoice & payment history
                    </Button>
                  </div>
                  <p className="muted">
                    Confirmed work only. Values are estimates until invoiced;
                    invoice totals use rounded individual lines.
                  </p>
                  {(["client", "project", "category", "month"] as const).map(
                    (group) => {
                      const rows = new Map<
                        string,
                        { seconds: number; billable: number }
                      >();
                      for (const e of group === "month"
                        ? entries.filter((e) => !e.draft)
                        : actual) {
                        const key =
                          group === "client"
                            ? data.clients.find(
                                (c) => c.id === contract?.client_id,
                              )?.name || "Unknown"
                            : group === "project"
                              ? data.projects.find((p) => p.id === e.project_id)
                                  ?.name || "Unassigned"
                              : group === "month"
                                ? e.work_date?.slice(0, 7) || "Unknown"
                                : e.category;
                        const old = rows.get(key) || {
                          seconds: 0,
                          billable: 0,
                        };
                        old.seconds += e.seconds || 0;
                        old.billable += e.billable ? e.seconds || 0 : 0;
                        rows.set(key, old);
                      }
                      return (
                        <div key={group}>
                          <h3>By {group}</h3>
                          <table>
                            <thead>
                              <tr>
                                <th>{group}</th>
                                <th>Actual hours</th>
                                <th>Billable hours</th>
                                <th>Services value</th>
                              </tr>
                            </thead>
                            <tbody>
                              {[...rows].map(([k, v]) => (
                                <tr key={k}>
                                  <td>{k}</td>
                                  <td>{(v.seconds / 3600).toFixed(2)}</td>
                                  <td>{(v.billable / 3600).toFixed(2)}</td>
                                  <td>
                                    {usd(
                                      (v.billable / 3600) *
                                        Number(contract?.rate),
                                    )}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                          {rows.size === 0 && (
                            <p className="muted">
                              No confirmed work in this period.
                            </p>
                          )}
                        </div>
                      );
                    },
                  )}
                </section>
              </>
            )}
            {page === "Clients & Contracts" && (
              <>
                <div className="section-head">
                  <h2>Clients</h2>
                  <div className="actions">
                    <Button secondary onClick={() => setModal("client")}>
                      Add client
                    </Button>
                    <Button onClick={() => setModal("new-contract")}>
                      Add contract
                    </Button>
                  </div>
                </div>
                <div className="project-grid">
                  {data.clients.map((c) => (
                    <section className="panel" key={c.id}>
                      <Building2 size={24} />
                      <h2>{c.name}</h2>
                      <p>{c.email}</p>
                      <p className="muted">
                        {c.address || "Address not entered"}
                      </p>
                      <button
                        className="text-btn"
                        onClick={() => setModal("client:" + c.id)}
                      >
                        Edit client
                      </button>
                    </section>
                  ))}
                </div>
                <section className="panel">
                  <div className="section-head">
                    <h2>Active contract settings</h2>
                    <Button secondary onClick={() => setModal("contract")}>
                      Edit contract
                    </Button>
                  </div>
                  <div className="stats">
                    <Stat
                      label="Hourly rate"
                      value={usd(contract?.rate)}
                      detail="USD"
                    />
                    <Stat
                      label="Weekly range"
                      value={`${contract?.minimum_hours}–${contract?.maximum_hours}`}
                      detail="Billable hours"
                    />
                    <Stat
                      label="Payment terms"
                      value={`${contract?.payment_days} days`}
                      detail="Approved invoices, from receipt"
                    />
                  </div>
                  <p>
                    Consulting start: {contract?.start_date} · Week starts:{" "}
                    {
                      [
                        "",
                        "Monday",
                        "Tuesday",
                        "Wednesday",
                        "Thursday",
                        "Friday",
                        "Saturday",
                        "Sunday",
                      ][contract?.week_start || 1]
                    }
                  </p>
                  <p className="callout">
                    The weekly minimum is an assignment obligation. Actual work
                    and approved adjustments are recorded separately.
                  </p>
                </section>
              </>
            )}
            {page === "Settings" && (
              <>
                <section className="panel">
                  <h2>Workspace preferences</h2>
                  <Button secondary onClick={() => setDark(!dark)}>
                    {dark ? "Use light theme" : "Use dark theme"}
                  </Button>
                  <p>
                    Week start and payment instructions are configured per
                    contract under Clients & Contracts.
                  </p>
                  <p className="muted">
                    Timestamps use UTC. Work dates and start times use the local
                    dates you confirm. Database backups and restore instructions
                    are in the README.
                  </p>
                </section>
                <section className="panel">
                  <h2>Audit history</h2>
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Time (UTC)</th>
                          <th>Action</th>
                          <th>Record</th>
                          <th>Details</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.audit.map((a) => (
                          <tr key={a.id}>
                            <td>{a.occurred}</td>
                            <td>{a.action}</td>
                            <td>{a.entity}</td>
                            <td>
                              <details>
                                <summary>View</summary>
                                <pre>{a.detail}</pre>
                              </details>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </section>
              </>
            )}
            <footer>
              Consulting Invoice Manager{" "}
              <span>Actual work. Clear records. Confident invoicing.</span>
            </footer>
          </div>
        </main>
        {modal === "entry" && (
          <Modal
            title={edit ? "Review work entry" : "Log work"}
            onClose={() => setModal("")}
          >
            <form
              onSubmit={(e) => {
                const b = formData(e);
                act(
                  () =>
                    request("/entries", {
                      ...b,
                      id: edit?.id || 0,
                      contract_id: cid,
                      seconds: Math.round(Number(b.hours) * 3600),
                    }),
                  "Work entry saved",
                );
              }}
            >
              <div className="form-grid">
                <Input
                  label="Date worked"
                  name="work_date"
                  type="date"
                  value={edit?.work_date}
                />
                <Input
                  label="Local start time"
                  name="start_time"
                  type="time"
                  value={edit?.start_time}
                />
                <Input
                  label="Actual hours (decimal)"
                  name="hours"
                  type="number"
                  min="0.0001"
                  max="24"
                  step="0.0001"
                  value={edit?.seconds ? edit.seconds / 3600 : undefined}
                />
                <label className="field">
                  Project
                  <select
                    name="project_id"
                    defaultValue={edit?.project_id || ""}
                  >
                    <option value="">Select project</option>
                    {projects.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  Category
                  <select
                    name="category"
                    defaultValue={edit?.category || categories[0]}
                  >
                    {categories.map((c) => (
                      <option key={c}>{c}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  Billing
                  <select name="billable" defaultValue={edit?.billable ?? 1}>
                    <option value="1">Billable</option>
                    <option value="0">Nonbillable</option>
                  </select>
                </label>
              </div>
              <Input
                label="Description of services"
                name="description"
                value={edit?.description}
                required
              />
              <Input
                label="Repository reference"
                name="repository"
                value={edit?.repository}
              />
              <Input
                label="GitHub issue, PR, commit or attachment URL"
                name="reference"
                value={edit?.reference}
              />
              <label className="field">
                Notes / supporting references
                <textarea name="notes" defaultValue={edit?.notes} />
              </label>
              <label className="field">
                Entry status
                <select name="draft" defaultValue={edit?.draft ?? 1}>
                  <option value="1">
                    Draft / incomplete — excluded from billing
                  </option>
                  <option value="0">Confirmed actual work</option>
                </select>
              </label>
              <div className="actions">
                <Button type="submit">Save entry</Button>
                {edit && (
                  <Button
                    secondary
                    onClick={() => {
                      if (
                        confirm(
                          "Delete this entry? Its prior contents remain in the audit history.",
                        )
                      )
                        act(
                          () =>
                            request(
                              "/entries/" + edit.id + "?confirmed=true",
                              undefined,
                              "DELETE",
                            ),
                          "Entry deleted",
                        );
                    }}
                  >
                    Delete entry
                  </Button>
                )}
              </div>
            </form>
          </Modal>
        )}
        {modal === "timer" && (
          <Modal title="Start a work timer" onClose={() => setModal("")}>
            <form
              onSubmit={(e) => {
                const b = formData(e);
                act(
                  () => request("/timer/start", { ...b, contract_id: cid }),
                  "Timer started",
                );
              }}
            >
              <Input label="Task description" name="description" required />
              <Input
                label="Local date worked"
                name="work_date"
                type="date"
                value={today()}
                required
              />
              <label className="field">
                Project
                <select name="project_id">
                  {projects.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Category
                <select name="category">
                  {categories.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </label>
              <Button type="submit">
                <Play size={16} />
                Start timer
              </Button>
            </form>
          </Modal>
        )}
        {modal.startsWith("week:") &&
          (() => {
            const w = weeks.find((w) => w.week === modal.slice(5))!;
            return (
              <Modal
                title={"Review week of " + w.week}
                onClose={() => setModal("")}
              >
                <p className="callout">
                  Acknowledging a shortfall resolves planning review only. It
                  does not add invoice charges.
                </p>
                <form
                  onSubmit={(e) => {
                    const b = formData(e);
                    act(
                      () =>
                        request("/weekly", {
                          ...b,
                          contract_id: cid,
                          week: w.week,
                        }),
                      "Weekly review recorded",
                    );
                  }}
                >
                  <label className="field">
                    Contractor availability
                    <select name="available" defaultValue={w.available ?? 1}>
                      <option value="1">Available</option>
                      <option value="0">Unavailable</option>
                    </select>
                  </label>
                  <Input
                    label="Written exception agreement reference"
                    name="exception_ref"
                    value={w.exception_ref}
                  />
                  <Input
                    label="Written overtime approval reference"
                    name="overtime_ref"
                    value={w.overtime_ref}
                  />
                  <Input
                    label="Shortfall / partial-week clarification and planning acknowledgement"
                    name="acknowledgement"
                    value={w.acknowledgement}
                  />
                  <Button type="submit">Save weekly review</Button>
                </form>
              </Modal>
            );
          })()}
        {(modal === "invoice" || modal === "replacement") && (
          <Modal
            title={
              modal === "replacement"
                ? "Documented invoice replacement"
                : "Create monthly invoice"
            }
            onClose={() => setModal("")}
          >
            <form
              onSubmit={(e) => {
                const b = formData(e);
                act(async () => {
                  const p = await request<Preview>("/invoices", {
                    ...b,
                    contract_id: cid,
                    replacement_for:
                      modal === "replacement"
                        ? selected?.invoice.id
                        : undefined,
                  });
                  setSelected(p);
                }, "Draft invoice created");
              }}
            >
              <div className="form-grid">
                <Input
                  label="Service period start"
                  name="period_start"
                  type="date"
                  value={
                    modal === "replacement"
                      ? selected?.invoice.period_start
                      : from
                  }
                  required
                />
                <Input
                  label="Service period end"
                  name="period_end"
                  type="date"
                  value={
                    modal === "replacement" ? selected?.invoice.period_end : to
                  }
                  required
                />
              </div>
              <label className="field">
                Presentation
                <select name="presentation">
                  <option value="itemized">Itemized</option>
                  <option value="grouped">
                    Grouped with full dated work detail
                  </option>
                </select>
              </label>
              {modal === "replacement" && (
                <Input
                  label="Reason for replacement (required)"
                  name="revision_reason"
                  required
                />
              )}
              <details>
                <summary>Explicit contractual adjustment (optional)</summary>
                <p className="muted small">
                  Never generated from missing hours. Enter only an
                  independently authorized monetary adjustment.
                </p>
                <Input
                  label="Adjustment USD"
                  name="adjustment"
                  type="number"
                  step="0.01"
                  min="0"
                  value="0"
                />
                <Input label="Documented reason" name="adjustment_reason" />
                <Input
                  label="Manual approval reference"
                  name="adjustment_approval"
                />
              </details>
              <Button type="submit">Create draft for review</Button>
            </form>
          </Modal>
        )}
        {modal.startsWith("expense:") &&
          (() => {
            const expense = data.expenses.find(
              (e) => e.id === Number(modal.slice(8)),
            );
            return (
              <Modal
                title="Record reimbursable expense"
                onClose={() => setModal("")}
              >
                <form
                  onSubmit={(e) => {
                    const b = formData(e);
                    act(() =>
                      request("/expenses", {
                        ...b,
                        id: expense?.id || 0,
                        contract_id: cid,
                      }),
                    );
                  }}
                >
                  <Input
                    label="Expense date"
                    name="expense_date"
                    value={expense?.expense_date}
                    type="date"
                    required
                  />
                  <Input
                    label="Description"
                    name="description"
                    value={expense?.description}
                    required
                  />
                  <Input
                    label="Amount USD"
                    name="amount"
                    value={expense?.amount}
                    type="number"
                    min="0.01"
                    step="0.01"
                    required
                  />
                  <Input
                    label="Prior written approval reference"
                    name="approval_ref"
                    value={expense?.approval_ref}
                    required
                  />
                  <Button type="submit">Save expense</Button>
                </form>
              </Modal>
            );
          })()}
        {modal === "email" && selected && (
          <EmailModal id={selected.invoice.id} onClose={() => setModal("")} />
        )}
        {modal === "withdraw" && (
          <Modal
            title="Withdraw unpaid invoice for correction"
            onClose={() => setModal("")}
          >
            <p className="callout">
              The original snapshot stays in your records. Its balance will be
              withdrawn, and its time and expenses will be released for
              correction. Notify accounting yourself if it was submitted.
            </p>
            <form
              onSubmit={(e) => {
                const b = formData(e);
                if (
                  confirm(
                    "Withdraw this unpaid invoice and release its work for correction?",
                  )
                )
                  act(
                    () =>
                      mutateInvoice("/withdraw", {
                        confirmed: true,
                        reason: b.reason,
                      }),
                    "Invoice withdrawn for correction",
                  );
              }}
            >
              <Input
                label="Documented reason for withdrawal"
                name="reason"
                required
              />
              <Button type="submit">Confirm withdrawal</Button>
            </form>
          </Modal>
        )}
        {modal === "sent" && (
          <Modal title="Record invoice submission" onClose={() => setModal("")}>
            <p>Confirm you sent the invoice and attached the PDF.</p>
            <form
              onSubmit={(e) => {
                const b = formData(e);
                act(
                  () =>
                    mutateInvoice("/status", {
                      state: "Sent",
                      submitted: b.submitted,
                    }),
                  "Submission recorded",
                );
              }}
            >
              <Input
                label="Actual submission / receipt date"
                name="submitted"
                type="date"
                value={today()}
                max={today()}
                required
              />
              <Button type="submit">Confirm sent</Button>
            </form>
          </Modal>
        )}
        {modal === "dispute" && (
          <Modal title="Record dispute" onClose={() => setModal("")}>
            <form
              onSubmit={(e) => {
                const b = formData(e);
                act(
                  () =>
                    mutateInvoice("/status", {
                      state: "Disputed",
                      notes: b.notes,
                    }),
                  "Dispute recorded",
                );
              }}
            >
              <Input
                label="Dispute details and references"
                name="notes"
                required
              />
              <Button type="submit">Save dispute</Button>
            </form>
          </Modal>
        )}
        {(modal === "payment" || modal === "historical") && (
          <Modal
            title={
              modal === "historical"
                ? "Confirm historical payment receipt"
                : "Record payment received"
            }
            onClose={() => setModal("")}
          >
            <form
              onSubmit={(e) => {
                const b = formData(e);
                act(
                  () =>
                    modal === "historical"
                      ? request("/historical", b)
                      : mutateInvoice("/payments", b),
                  "Receipt recorded",
                );
              }}
            >
              {modal === "payment" && (
                <>
                  <Input
                    label="Amount received USD"
                    name="amount"
                    type="number"
                    step="0.01"
                    min="0.01"
                    max={selected?.invoice.balance}
                    required
                  />
                  <Input label="Payment method" name="method" required />
                </>
              )}
              <Input
                label="Actual date received"
                name="received"
                type="date"
                max={today()}
                required
              />
              <Input
                label="Payment / bank reference"
                name="reference"
                required
              />
              <Button type="submit">Confirm receipt</Button>
            </form>
          </Modal>
        )}
        {(modal === "client" || modal.startsWith("client:")) &&
          (() => {
            const c = data.clients.find((c) => c.id === Number(modal.slice(7)));
            return (
              <Modal
                title={c ? "Edit client" : "Add client"}
                onClose={() => setModal("")}
              >
                <form
                  onSubmit={(e) => {
                    const b = formData(e);
                    act(() => request("/clients", { ...b, id: c?.id || 0 }));
                  }}
                >
                  <Input
                    label="Company name"
                    name="name"
                    value={c?.name}
                    required
                  />
                  <Input
                    label="Invoice submission email"
                    name="email"
                    type="email"
                    value={c?.email}
                    required
                  />
                  <Input
                    label="Billing address"
                    name="address"
                    value={c?.address}
                  />
                  <Button type="submit">Save client</Button>
                </form>
              </Modal>
            );
          })()}
        {modal === "project" && (
          <Modal title="Add project" onClose={() => setModal("")}>
            <form
              onSubmit={(e) => {
                const b = formData(e);
                act(() =>
                  request("/projects", {
                    ...b,
                    client_id: contract?.client_id,
                  }),
                );
              }}
            >
              <Input label="Project name" name="name" required />
              <Input label="Repository reference" name="repository" />
              <Button type="submit">Save project</Button>
            </form>
          </Modal>
        )}
        {(modal === "contract" || modal === "new-contract") &&
          (() => {
            const c = modal === "contract" ? contract : undefined;
            return (
              <Modal
                title={c ? "Edit contract" : "Add contract"}
                onClose={() => setModal("")}
              >
                <form
                  onSubmit={(e) => {
                    const b = formData(e);
                    act(() => request("/contracts", { ...b, id: c?.id || 0 }));
                  }}
                >
                  <label className="field">
                    Client
                    <select name="client_id" defaultValue={c?.client_id}>
                      {data.clients.map((cl) => (
                        <option value={cl.id} key={cl.id}>
                          {cl.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <Input
                    label="Contractor name"
                    name="contractor"
                    value={c?.contractor}
                    required
                  />
                  <Input
                    label="Contractor address"
                    name="contractor_address"
                    value={c?.contractor_address}
                  />
                  <div className="form-grid">
                    <Input
                      label="Start date"
                      name="start_date"
                      type="date"
                      value={c?.start_date}
                      required
                    />
                    <Input
                      label="End date (optional)"
                      name="end_date"
                      type="date"
                      value={c?.end_date}
                    />
                    <Input
                      label="Hourly rate USD"
                      name="rate"
                      type="number"
                      step="0.01"
                      min="0.01"
                      value={c?.rate}
                      required
                    />
                    <Input
                      label="Minimum hours per week"
                      name="minimum_hours"
                      type="number"
                      min="0"
                      value={c?.minimum_hours}
                      required
                    />
                    <Input
                      label="Maximum hours without approval"
                      name="maximum_hours"
                      type="number"
                      min="0"
                      value={c?.maximum_hours}
                      required
                    />
                    <Input
                      label="Payment days from receipt"
                      name="payment_days"
                      type="number"
                      min="1"
                      value={c?.payment_days}
                      required
                    />
                    <Input
                      label="Dispute period days"
                      name="dispute_days"
                      type="number"
                      min="1"
                      value={c?.dispute_days}
                      required
                    />
                    <label className="field">
                      Week starts
                      <select
                        name="week_start"
                        defaultValue={c?.week_start || 1}
                      >
                        {[
                          "Monday",
                          "Tuesday",
                          "Wednesday",
                          "Thursday",
                          "Friday",
                          "Saturday",
                          "Sunday",
                        ].map((day, i) => (
                          <option key={day} value={i + 1}>
                            {day}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <Input
                    label="Payment instructions (optional)"
                    name="payment_instructions"
                    value={c?.payment_instructions}
                  />
                  <Button type="submit">Save contract</Button>
                </form>
              </Modal>
            );
          })()}
      </div>
    </BusyContext.Provider>
  );
}
function Stat({
  label,
  value,
  suffix,
  detail,
}: {
  label: string;
  value: string;
  suffix?: string;
  detail: string;
}) {
  return (
    <div className="stat">
      <span className="muted small">{label}</span>
      <strong>
        {value}
        <small>{suffix}</small>
      </strong>
      <span className="muted small">{detail}</span>
    </div>
  );
}
function Step({
  number,
  title,
  text,
  onClick,
}: {
  number: string;
  title: string;
  text: string;
  onClick: () => void;
}) {
  return (
    <button className="step" onClick={onClick}>
      <span>{number}</span>
      <div>
        <strong>{title}</strong>
        <p className="muted small">{text}</p>
      </div>
      <ChevronRight size={16} />
    </button>
  );
}
function EntryTable({
  entries,
  projects,
  onEdit,
  readonly = false,
}: {
  entries: Entry[];
  projects: { id: number; name: string }[];
  onEdit: (e: Entry) => void;
  readonly?: boolean;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Work & project</th>
            <th>Category</th>
            <th>Hours</th>
            <th>Status</th>
            {!readonly && <th />}
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <tr key={e.id}>
              <td className="nowrap">
                {e.work_date || "Unconfirmed"}
                <small>{e.start_time || "Date needed"}</small>
              </td>
              <td>
                <strong className="entry-description">{e.description}</strong>
                <small>
                  {projects.find((p) => p.id === e.project_id)?.name ||
                    e.project ||
                    "Project needed"}
                  {e.repository && " · " + e.repository}
                </small>
              </td>
              <td>
                <span className="muted small">{e.category}</span>
              </td>
              <td className="nowrap">
                {e.seconds ? (e.seconds / 3600).toFixed(4) : "—"}
              </td>
              <td>
                <Badge>
                  {e.draft
                    ? "Draft / incomplete"
                    : e.billable
                      ? "Billable"
                      : "Nonbillable"}
                </Badge>
              </td>
              {!readonly && (
                <td>
                  <button className="text-btn" onClick={() => onEdit(e)}>
                    Review
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {entries.length === 0 && (
        <div className="empty">
          <Clock size={26} />
          <h3>No confirmed work yet</h3>
          <p className="muted">
            Log actual work or confirm an incomplete activity to get started.
          </p>
        </div>
      )}
    </div>
  );
}
function InvoiceTable({
  invoices,
  onOpen,
}: {
  invoices: Invoice[];
  onOpen: (id: number) => void;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Invoice</th>
            <th>Service period</th>
            <th>Amount</th>
            <th>Paid</th>
            <th>Balance</th>
            <th>Due</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {invoices.map((i) => (
            <tr key={i.id}>
              <td>
                <strong>{i.number}</strong>
                <small>{i.client_name}</small>
                {i.submitted && <small>Submitted {i.submitted}</small>}
              </td>
              <td>
                {i.period_start} – {i.period_end}
              </td>
              <td>{i.snapshot ? usd(i.total) : "Draft"}</td>
              <td>{usd(i.paid)}</td>
              <td>{i.snapshot ? usd(i.balance) : "—"}</td>
              <td>{i.due || "Not submitted"}</td>
              <td>
                <Badge>{i.state}</Badge>
                {i.workflow_state && i.workflow_state !== i.state && (
                  <small>Review: {i.workflow_state}</small>
                )}
              </td>
              <td>
                <button className="text-btn" onClick={() => onOpen(i.id)}>
                  Review <ArrowUpRight size={14} />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!invoices.length && (
        <div className="empty">
          <FileText size={28} />
          <h3>Your next invoice starts here</h3>
          <p className="muted">
            Create an invoice from confirmed work for the selected period.
          </p>
        </div>
      )}
    </div>
  );
}
function EmailModal({ id, onClose }: { id: number; onClose: () => void }) {
  const [mail, setMail] = useState<{
    to: string;
    subject: string;
    body: string;
  }>();
  const [copied, setCopied] = useState(false);
  const [err, setErr] = useState("");
  useEffect(() => {
    request<{ to: string; subject: string; body: string }>(
      `/invoices/${id}/email`,
    )
      .then(setMail)
      .catch((e) => setErr(e.message));
  }, [id]);
  return (
    <Modal title="Prepare submission email" onClose={onClose}>
      {err && <p role="alert">{err}</p>}
      {mail && (
        <form
          onSubmit={(event) => {
            const values = formData(event);
            const text = `To: ${values.to}\n${values.cc ? `CC: ${values.cc}\n` : ""}Subject: ${values.subject}\n\n${values.body}`;
            navigator.clipboard
              .writeText(text)
              .then(() => setCopied(true))
              .catch(() =>
                setErr(
                  "Clipboard unavailable. Select and copy the text manually.",
                ),
              );
          }}
        >
          <Input label="To" name="to" value={mail.to} />
          <Input
            label="Optional CC to Brandon (enter address)"
            name="cc"
            type="email"
          />
          <Input label="Subject" name="subject" value={mail.subject} />
          <textarea
            aria-label="Email body"
            name="body"
            className="email-body"
            defaultValue={mail.body}
          />
          <Button type="submit">
            {copied ? "Copied" : "Copy email subject & body"}
          </Button>
          <p className="muted small">
            Attach the downloaded PDF in your email app. Nothing is sent
            automatically.
          </p>
        </form>
      )}
    </Modal>
  );
}
