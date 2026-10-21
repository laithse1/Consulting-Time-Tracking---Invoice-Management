CREATE TABLE clients(id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, address TEXT NOT NULL DEFAULT '');
CREATE TABLE contracts(id INTEGER PRIMARY KEY, client_id INTEGER NOT NULL REFERENCES clients(id), contractor TEXT NOT NULL, start_date TEXT NOT NULL, end_date TEXT, rate TEXT NOT NULL, minimum_hours INTEGER NOT NULL, maximum_hours INTEGER NOT NULL, payment_days INTEGER NOT NULL DEFAULT 30, dispute_days INTEGER NOT NULL DEFAULT 30, week_start INTEGER NOT NULL DEFAULT 1, payment_instructions TEXT NOT NULL DEFAULT '', contractor_address TEXT NOT NULL DEFAULT '');
CREATE TABLE projects(id INTEGER PRIMARY KEY, client_id INTEGER NOT NULL REFERENCES clients(id), name TEXT NOT NULL, repository TEXT NOT NULL DEFAULT '');
CREATE TABLE entries(id INTEGER PRIMARY KEY, contract_id INTEGER NOT NULL REFERENCES contracts(id), project_id INTEGER REFERENCES projects(id), work_date TEXT, start_time TEXT, seconds INTEGER, category TEXT NOT NULL, description TEXT NOT NULL, repository TEXT NOT NULL DEFAULT '', reference TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', billable INTEGER NOT NULL DEFAULT 1, draft INTEGER NOT NULL DEFAULT 1);
CREATE UNIQUE INDEX entry_duplicate ON entries(contract_id,work_date,start_time,seconds,description) WHERE draft=0;
CREATE TABLE weekly(id INTEGER PRIMARY KEY, contract_id INTEGER NOT NULL REFERENCES contracts(id), week TEXT NOT NULL, available INTEGER NOT NULL DEFAULT 1, exception_ref TEXT NOT NULL DEFAULT '', overtime_ref TEXT NOT NULL DEFAULT '', acknowledgement TEXT NOT NULL DEFAULT '', UNIQUE(contract_id,week));
CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1), contract_id INTEGER NOT NULL, project_id INTEGER, description TEXT NOT NULL, category TEXT NOT NULL, work_date TEXT NOT NULL, started TEXT, elapsed INTEGER NOT NULL DEFAULT 0);
CREATE TABLE expenses(id INTEGER PRIMARY KEY,contract_id INTEGER NOT NULL REFERENCES contracts(id), expense_date TEXT NOT NULL, description TEXT NOT NULL, amount TEXT NOT NULL, approval_ref TEXT NOT NULL DEFAULT '');
CREATE TABLE invoices(id INTEGER PRIMARY KEY, number TEXT UNIQUE NOT NULL, contract_id INTEGER NOT NULL REFERENCES contracts(id), period_start TEXT NOT NULL, period_end TEXT NOT NULL, invoice_date TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'Draft', snapshot TEXT, total TEXT NOT NULL DEFAULT '0', submitted TEXT, adjustment TEXT NOT NULL DEFAULT '0', adjustment_reason TEXT NOT NULL DEFAULT '', adjustment_approval TEXT NOT NULL DEFAULT '', presentation TEXT NOT NULL DEFAULT 'itemized', replacement_for INTEGER REFERENCES invoices(id), revision_reason TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '');
CREATE TABLE allocations(entry_id INTEGER PRIMARY KEY REFERENCES entries(id), invoice_id INTEGER NOT NULL REFERENCES invoices(id));
CREATE TABLE expense_allocations(expense_id INTEGER PRIMARY KEY REFERENCES expenses(id), invoice_id INTEGER NOT NULL REFERENCES invoices(id));
CREATE TABLE payments(id INTEGER PRIMARY KEY, invoice_id INTEGER NOT NULL REFERENCES invoices(id), amount TEXT NOT NULL, received TEXT NOT NULL, method TEXT NOT NULL, reference TEXT NOT NULL);
CREATE TABLE audit(id INTEGER PRIMARY KEY, occurred TEXT NOT NULL, action TEXT NOT NULL, entity TEXT NOT NULL, detail TEXT NOT NULL);
CREATE TABLE historical(id INTEGER PRIMARY KEY,description TEXT NOT NULL,amount TEXT NOT NULL,received TEXT,reference TEXT NOT NULL DEFAULT '');
INSERT INTO clients VALUES(1,'XR Navigation Inc.','accounting@xrnavigation.io','');
INSERT INTO contracts(id,client_id,contractor,start_date,rate,minimum_hours,maximum_hours) VALUES(1,1,'Laith Alkhalil','2026-09-01','75.00',10,16);
INSERT INTO projects VALUES(1,1,'Audiom',''),(2,1,'geospatial-mcp-server',''),(3,1,'Audiom MCP Server','');
INSERT INTO entries(contract_id,project_id,category,description,notes) VALUES
(1,2,'Code Refactoring','Refactored the geospatial-mcp-server codebase to prepare it for XR Navigation GitHub organization.','Confirm actual date and duration.'),
(1,2,'Repository Management','Created the company repository and pushed/migrated the project code.','Confirm actual date and duration.'),
(1,3,'Research','Researched Render and Railway as hosting options for a shared MCP server.','Confirm actual date and duration.'),
(1,3,'Architecture and Design','Evaluated Streamable HTTP transport, authentication, user/session isolation, map data provisioning, persistent service hosting, and deployment requirements.','Confirm actual date and duration.'),
(1,3,'Documentation','Documented and shared hosting recommendations and a proposed pilot deployment plan with the team.','Confirm actual date and duration.'),
(1,1,'Meetings and Standups','Initial team introduction meeting','Approximately 1 hour; confirm actual date and duration.'),
(1,1,'Meetings and Standups','Project manager meeting','Approximately 0.5 hour; confirm actual date and duration.');
INSERT INTO historical VALUES(1,'Prior-work payment (excluded from recurring invoices)','4000.00',NULL,'');
