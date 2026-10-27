import type { Entry, Invoice, Week } from "@/types";
export interface Action {
  type: string;
  description: string;
  questions?: string[];
  work_date?: string;
  seconds?: number;
  start_time?: string;
  project_id?: number;
  category?: string;
  billable?: number;
  draft?: number;
  period_start?: string;
  period_end?: string;
  number?: string;
  reference?: string;
  source_text?: string;
  wording_suggestion?: boolean;
  provider_suggestion?: boolean;
  skip?: boolean;
  _invoice_id?: number;
  _entry_id?: number;
  proposed_hours?: string;
  allocation_start?: string;
  allocation_end?: string;
  work_state?: "proposed" | "planned" | "performed_unconfirmed";
  requires_resolution?: boolean;
  resolved?: boolean;
}

export interface Result {
  type: string;
  note?: string;
  entry?: Entry;
  invoice?: Invoice;
  warnings?: string[];
  total?: number;
  preview?: {
    entries: Entry[];
    hours: number;
    subtotal: number;
    expenseTotal: number;
    total: number;
  };
  report?: {
    actualHours: number;
    billableHours: number;
    servicesValue: number;
    note: string;
    projects: { project: string; hours: number }[];
  };
  weeks?: Week[];
  email?: { to: string; cc?: string; subject: string; body: string };
}

export interface Plan {
  deleted_at?: string | null;
  reference_date?: string;
  id: string;
  source: string;
  status: string;
  version: number;
  created: string;
  engine: string;
  revisions?: {
    version: number;
    instruction: string;
    reply: string;
    engine: string;
    created: string;
  }[];
  review?: { hours: number; servicesValue: number; incompleteTasks: number; note: string };
  actions: Action[];
  results: Result[];
}

export interface History {
  limits?: { sourceCharacters: number; actions: number; history: number };
  draft: { source: string };
  plans: Plan[];
  providers: { id: string; name: string; model: string; configured: boolean }[];
}
