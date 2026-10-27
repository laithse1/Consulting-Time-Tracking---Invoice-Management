export interface Client {
  archived?: boolean;
  archived_self?: boolean;
  id: number;
  name: string;
  email: string;
  address: string;
}
export interface Contract {
  archived?: boolean;
  archived_self?: boolean;
  id: number;
  client_id: number;
  contractor: string;
  start_date: string;
  end_date: string | null;
  rate: string;
  minimum_hours: number;
  maximum_hours: number;
  payment_days: number;
  dispute_days: number;
  week_start: number;
  payment_instructions: string;
  contractor_address: string;
}
export interface Project {
  archived?: boolean;
  archived_self?: boolean;
  id: number;
  client_id: number;
  name: string;
  repository: string;
}
export interface Entry {
  id: number;
  contract_id: number;
  project_id: number | null;
  work_date: string | null;
  start_time: string | null;
  seconds: number | null;
  category: string;
  description: string;
  repository: string;
  reference: string;
  notes: string;
  billable: number;
  draft: number;
  project?: string;
  amount?: string;
}
export interface Invoice {
  workflow_state?: string;
  client_name?: string;
  id: number;
  number: string;
  contract_id: number;
  period_start: string;
  period_end: string;
  invoice_date: string;
  state: string;
  snapshot: string | null;
  total: string;
  paid: string;
  balance: string;
  submitted: string | null;
  due?: string;
  overdue?: boolean;
  adjustment: string;
  adjustment_reason: string;
  adjustment_approval: string;
  presentation: string;
  replacement_for: number | null;
  revision_reason: string;
  notes: string;
}
export interface Payment {
  id: number;
  invoice_id: number;
  amount: string;
  received: string;
  method: string;
  reference: string;
}
export interface Expense {
  id: number;
  contract_id: number;
  expense_date: string;
  description: string;
  amount: string;
  approval_ref: string;
}
export interface Week {
  week: string;
  hours: number;
  minimum: number;
  maximum: number;
  difference: number;
  status: string;
  partial: boolean;
  unresolved: boolean;
  available?: number;
  exception_ref?: string;
  overtime_ref?: string;
  acknowledgement?: string;
}
export interface Timer {
  id: number;
  contract_id: number;
  project_id: number | null;
  description: string;
  category: string;
  work_date: string;
  started: string | null;
  elapsed: number;
}
export interface State {
  clients: Client[];
  contracts: Contract[];
  projects: Project[];
  entries: Entry[];
  invoices: Invoice[];
  payments: Payment[];
  expenses: Expense[];
  historical: {
    id: number;
    description: string;
    amount: string;
    received: string | null;
    reference: string;
  }[];
  audit: {
    id: number;
    occurred: string;
    action: string;
    entity: string;
    detail: string;
  }[];
  timer: Timer[];
}
export interface Preview {
  edit_token?: string;
  pendingWork?: { description: string; proposed_hours?: string; work_date?: string }[];
  plannedWork?: { description: string; proposed_hours?: string }[];
  assistantPlans?: string[];
  invoice: Invoice;
  contract: Contract;
  client: Client;
  entries: Entry[];
  expenses: Expense[];
  hours: number;
  subtotal: string;
  expenseTotal: string;
  total: string;
  warnings: string[];
}
