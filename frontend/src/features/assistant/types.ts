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
}

export interface Result {
  type: string;
  entry?: Entry;
  invoice?: Invoice;
  warnings?: string[];
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
  id: string;
  source: string;
  status: string;
  version: number;
  created: string;
  engine: string;
  actions: Action[];
  results: Result[];
}

export interface History {
  limits?: { sourceCharacters: number; actions: number; history: number };
  draft: { source: string };
  plans: Plan[];
  providers: { id: string; name: string; model: string; configured: boolean }[];
}
