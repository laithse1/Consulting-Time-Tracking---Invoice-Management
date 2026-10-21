import { useWorkspace } from "@/app/WorkspaceContext";
import { Badge } from "@/components/ui/Badge";
import { BusyContext } from "@/components/ui/BusyContext";
import { Button } from "@/components/ui/Button";
import { applicationConfig } from "@/config/application";
import WorkAssistant from "@/features/assistant/WorkAssistant";
import { UnlockWorkspace } from "@/features/auth/UnlockWorkspace";
import { ClientDialog } from "@/features/clients/ClientDialog";
import { ClientsPage } from "@/features/clients/ClientsPage";
import { ContractDialog } from "@/features/clients/ContractDialog";
import { DashboardPage } from "@/features/dashboard/DashboardPage";
import { DisputeDialog } from "@/features/invoices/DisputeDialog";
import { EmailModal } from "@/features/invoices/EmailModal";
import { ExpenseDialog } from "@/features/invoices/ExpenseDialog";
import { InvoiceDraftDialog } from "@/features/invoices/InvoiceDraftDialog";
import { InvoicesPage } from "@/features/invoices/InvoicesPage";
import { SubmissionDialog } from "@/features/invoices/SubmissionDialog";
import { WithdrawInvoiceDialog } from "@/features/invoices/WithdrawInvoiceDialog";
import { PaymentDialog } from "@/features/payments/PaymentDialog";
import { PaymentsPage } from "@/features/payments/PaymentsPage";
import { ProjectDialog } from "@/features/projects/ProjectDialog";
import { ProjectsPage } from "@/features/projects/ProjectsPage";
import { ReportsPage } from "@/features/reports/ReportsPage";
import { SettingsPage } from "@/features/settings/SettingsPage";
import { EntryDialog } from "@/features/time-tracking/EntryDialog";
import { TimerDialog } from "@/features/time-tracking/TimerDialog";
import { TimeTrackingPage } from "@/features/time-tracking/TimeTrackingPage";
import { WeeklyReviewDialog } from "@/features/time-tracking/WeeklyReviewDialog";
import { Check, ChevronRight, LogOut, Moon, Plus, Sun, X } from "lucide-react";
import { navigationItems as nav } from "./navigation";
export function WorkspaceShell() {
  const {
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
    contract,
    setCid,
    error,
    setError,
    notice,
    dark,
    setDark,
    modal,
    setModal,
    selected,
    setSelected,
    busy,
    refresh,
    invoices,
    openInvoice,
    entryForm,
  } = useWorkspace();
  if (!connected || !data) return <UnlockWorkspace />;
  return (
    <BusyContext.Provider value={busy}>
      <div className="app">
        <aside>
          <a className="brand" href="#" onClick={() => setPage("Dashboard")}>
            <div className="brandmark">
              ci<span>m</span>
            </div>
            <div>
              {applicationConfig.title.split(" ")[0]}
              <span>{applicationConfig.title.split(" ").slice(1).join(" ")}</span>
            </div>
          </a>
          <div className="workspace">
            <span className="avatar">LA</span>
            <div>
              {contract?.contractor
                ? `${contract.contractor.split(" ")[0]}’s workspace`
                : "Your workspace"}
              <small>{applicationConfig.workspaceLabel}</small>
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
                      invoices.filter((i) => i.state === "Draft" || i.state === "Ready for Review")
                        .length
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
                    {data.clients.find((cl) => cl.id === c.client_id)?.name} · {c.contractor}
                  </option>
                ))}
              </select>
              <button className="icon" aria-label="Toggle theme" onClick={() => setDark(!dark)}>
                {dark ? <Sun size={19} /> : <Moon size={19} />}
              </button>
              <span className="avatar">LA</span>
            </div>
          </header>
          <div className="content">
            <div className="page-head">
              <div>
                <div className="eyebrow">CONSULTING WORKSPACE</div>
                <h1>{page === "Dashboard" ? "A clear view of your work." : page}</h1>
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
            {page === "Work Assistant" && data && (
              <WorkAssistant
                key={cid}
                data={data}
                cid={cid}
                onRefresh={refresh}
                onInvoice={openInvoice}
              />
            )}
            {page === "Dashboard" && <DashboardPage />}
            {(page === "Time Tracker" || page === "Timesheets") && <TimeTrackingPage />}
            {page === "Projects" && <ProjectsPage />}
            {page === "Invoices" && <InvoicesPage />}
            {page === "Payments" && <PaymentsPage />}
            {page === "Reports" && <ReportsPage />}
            {page === "Clients & Contracts" && <ClientsPage />}
            {page === "Settings" && <SettingsPage />}
            <footer>
              {applicationConfig.title}{" "}
              <span>Actual work. Clear records. Confident invoicing.</span>
            </footer>
          </div>
        </main>
        {modal === "entry" && <EntryDialog />}
        {modal === "timer" && <TimerDialog />}
        {modal.startsWith("week:") && <WeeklyReviewDialog />}
        {(modal === "invoice" || modal === "replacement") && <InvoiceDraftDialog />}
        {modal.startsWith("expense:") && <ExpenseDialog />}
        {modal === "email" && selected && (
          <EmailModal id={selected.invoice.id} onClose={() => setModal("")} />
        )}
        {modal === "withdraw" && <WithdrawInvoiceDialog />}
        {modal === "sent" && <SubmissionDialog />}
        {modal === "dispute" && <DisputeDialog />}
        {(modal === "payment" || modal === "historical") && <PaymentDialog />}
        {(modal === "client" || modal.startsWith("client:")) && <ClientDialog />}
        {modal === "project" && <ProjectDialog />}
        {(modal === "contract" || modal === "new-contract") && <ContractDialog />}
      </div>
    </BusyContext.Provider>
  );
}
