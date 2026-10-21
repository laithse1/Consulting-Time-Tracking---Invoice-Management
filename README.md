# Consulting Invoice Manager

Self-hosted single-user time tracking, weekly contract review, immutable invoices, manual submission, and payment tracking. React + TypeScript + Vite + Tailwind + Radix UI, Java 21 / Spring Boot 3, SQLite / Flyway, PDFBox and Apache POI.

## Run with Docker

1. Copy `.env.example` to `.env`. Set `APP_TOKEN` to a random secret of at least 24 characters (for example generate 32 random bytes with your password manager). Never commit this file.
2. Start Docker Desktop, then run `docker compose up --build -d`.
3. Open http://127.0.0.1:8080 (or the `APP_PORT` set in `.env`) and enter that token. Lock the workspace when finished. On this workstation the verified deployment uses http://127.0.0.1:18080 because Windows blocks port 8080.

The database persists in the `consulting-data` named volume. `docker compose down` preserves it; do not use `down -v` unless you intend to destroy the database. No mail server, cloud service, or GitHub integration is required. Only localhost is published. This is a single-user application: do not expose it publicly without a TLS reverse proxy, individual authentication, and an appropriate access policy.

## Run natively on Windows

Install Java 21+, Maven 3.9+, Node 22.12+ and npm. From the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' # adapt to your installation
$env:APP_TOKEN = '<your randomly generated access token>'
./scripts/start-local.ps1
```

The script builds the frontend, copies its assets into Spring Boot, runs backend tests and starts the app at http://127.0.0.1:8080. Data is under `./data/consulting.db`. If no token is supplied, the script generates and displays a session token. Keep it private. Use PowerShell 7+ for the script, or build manually as below.

Development: run `mvn -f backend/pom.xml spring-boot:run` with APP_TOKEN set; separately run `npm ci` and `npm run dev` in `frontend`. Vite at http://127.0.0.1:5173 proxies `/api` to port 8080. Use the same token. The API fails startup if the token is missing or too short.

## First invoice

1. Select the XR Navigation contract and September 2026 in the month selector.
2. Review the seven seeded activities under Time Tracker. Enter actual work dates, local start times and confirmed durations. The two approximate meeting estimates are notes only; no hours have been assumed. Set each completed activity to **Confirmed actual work**.
3. Under Timesheets review each weekly shortfall, availability, partial contract week, exception and overtime approval. A planning acknowledgement resolves an assignment discrepancy without creating billable time. Overtime requires a written approval or exception reference.
4. Under Invoices, record any pre-approved expenses, then create an invoice for September 1â€“30. Only eligible confirmed billable work and period expenses are selected. Review the warnings and dated work detail. A contractual-minimum adjustment is optional and must carry its own amount, documented reason and manual approval reference.
5. Preview/download PDF, Excel or CSV. Confirm finalization to lock a snapshot. Prepare and copy the submission email, attach the PDF in your email app, send it yourself and record the actual submission/receipt date. Optional CC addresses must be entered; no Brandon address is invented.
6. Record approval, disputes and actual partial/full payments. Balance, due date and overdue state are calculated. Repeat for October with the existing contract and projects.

The historical $4,000 prior-work payment is a separate, initially unconfirmed record under Payments. It never enters recurring invoices or recurring revenue totals.

## Billing rules and corrections

Durations are integer seconds, hours are displayed as decimal hours, and each work line is calculated as `seconds Ã— rate Ã· 3600`, rounded half-up to cents with `BigDecimal`. Totals add the rounded lines, expenses and explicitly approved adjustments. USD is the supported currency. Date-only values are ISO local dates; timer timestamps and audit times use UTC. Timer stop saves a draft: verify its local date/start time and split any work crossing midnight before confirmation.

The configurable week start applies to whole-week compliance, including entries in adjacent months. Invoice service periods include only work dated inside that period. Partial contract weeks retain the minimum (no invented proration) and require clarification. Assignment shortfalls never produce work or automatic charges. References and attachments are stored as text/URLs; binary file upload is not supported.

Finalized invoice snapshots are immutable. To correct an unpaid issued invoice, choose **Withdraw unpaid invoice for correction**, document the reason and explicitly confirm. The original snapshot is retained with a zero active balance; its source work and expenses are released for editing. Correct them, return to the withdrawn invoice, and choose **Create documented replacement**. Finalizing the replacement marks the original Replaced and allocates the corrected contents transactionally. Notify accounting manually that the original was withdrawn if it was already submitted. Invoices with payment events cannot be withdrawn or replaced, because this release does not implement credit notes/refunds. Resolve those cases through your accounting process; do not erase payments. Payment and financial audit records are append-only.

## Tests and builds

```powershell
mvn -f backend/pom.xml test
cd frontend
npm ci
npm test
npm run build
```

For the complete browser workflow, first build the packaged application with `scripts/start-local.ps1` (stop it before rebuilding), then run `node e2e/smoke.mjs` from `frontend` with `JAVA_HOME` set and Chrome installed. This starts its own server on port 18081 with a fresh disposable database and closes it after testing. It never adds test work to your normal database. You can build without starting by running the commands in that script through the Maven package step.

JUnit integration tests use isolated in-memory SQLite with the real migrations. They cover incomplete seeds, overlaps/duplicates, partial weeks, month boundaries, overtime, adjustments, expenses, double billing, immutable snapshots, replacement, timer concurrency and payment arithmetic. Frontend tests use Vitest and React Testing Library. PDF and XLSX exports are exercised by backend tests. See `docs/verification.md` for actual execution results and environment limitations.

## Backups and restore

Protect backups like financial records. Native installs can use `./scripts/backup.ps1 -Database ./data/consulting.db -Destination ./backups` (Python 3 required); it uses SQLite's online backup API and verifies integrity. Store encrypted copies outside this machine. Test a restore periodically.

For Docker, stop writes for a simple consistent file backup:

```powershell
docker compose stop app
New-Item -ItemType Directory -Force backups
docker compose cp app:/app/data/consulting.db ./backups/consulting.db
docker compose start app
```

Restore: stop the app; make a safety backup of the current database; verify the selected backup using Python `sqlite3` / `PRAGMA integrity_check`; replace the native `data/consulting.db` or use `docker compose cp ./backups/consulting.db app:/app/data/consulting.db` while stopped. For Docker, restore file ownership with `docker compose run --rm --user root --cap-add CHOWN --entrypoint chown app consulting:consulting /app/data/consulting.db`. Restart and verify invoices, balances and audit history. Preserve any journal/WAL files if copying an uncleanly stopped database; prefer a clean stop or SQLite backup API. Never copy a live SQLite database with a raw file copy.

Flyway validates and applies versioned migrations on startup. Do not edit applied migrations. Back up before an upgrade. Future PostgreSQL migration requires adapting identity columns, SQLite insert-ID retrieval, date columns and database connection configuration, then migrating data and validating all monetary totals; it is an architectural migration path, not a currently selectable backend.

## Documentation

- [Architecture and decisions](docs/architecture.md)
- [API and integration boundary](docs/api.md)
- [Verification results](docs/verification.md)
- [Sample draft invoice](docs/sample-invoice.pdf) â€” a zero-value draft, with no invented work

Swagger UI: http://127.0.0.1:8080/swagger-ui/index.html. OpenAPI JSON: `/v3/api-docs`. Authorize with your local bearer token. Never add access tokens to query strings or URLs. The manual email payload is the future Gmail/SMTP adapter boundary; no external mail is sent in this release.
