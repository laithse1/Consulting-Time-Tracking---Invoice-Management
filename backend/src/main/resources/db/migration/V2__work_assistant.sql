CREATE TABLE assistant_drafts(contract_id INTEGER PRIMARY KEY REFERENCES contracts(id), source TEXT NOT NULL, updated TEXT NOT NULL);
CREATE TABLE assistant_plans(id TEXT PRIMARY KEY, contract_id INTEGER NOT NULL REFERENCES contracts(id), source TEXT NOT NULL, reference_date TEXT NOT NULL, engine TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'proposed', version INTEGER NOT NULL DEFAULT 1, plan_json TEXT NOT NULL, results_json TEXT, created TEXT NOT NULL, applied TEXT);
CREATE INDEX assistant_history ON assistant_plans(contract_id,created);
CREATE UNIQUE INDEX untimed_entry_duplicate ON entries(contract_id,work_date,seconds,description,coalesce(project_id,0),billable) WHERE draft=0 AND start_time IS NULL;
