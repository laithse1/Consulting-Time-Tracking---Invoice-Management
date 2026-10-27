# Verification record

## Saved conversation deletion — October 26, 2026

Saved conversations now have a separate Delete control and an explicit confirmation. Proposed and applied conversations disappear from history after deletion, including after reload. Deleting the selected conversation closes its review panel without clearing composer notes. V5 adds a deletion registry rather than removing billing evidence. Source messages, revisions, receipts, work entries, invoices, and pending-work safeguards remain intact; linked invoice review and correction access remains available with a deletion notice.

Verified 89 passing backend tests (one optional PDF sample skipped), 20 frontend tests, strict production compilation, and a Linux Docker image build. Coverage includes stale-version and confirmation guards, idempotent deletion, retained applied receipts and composer drafts, unchanged work/invoice records, and blocked finalization for deleted conversations with pending work. The disposable-database Chrome lifecycle workflow passed deletion, disappearance after reload, unchanged work records, and mobile layout, alongside its existing invoice and record lifecycle checks. The V2-to-V5 migration test preserves pre-existing rows. The existing assistant browser workflow also passed saved corrections, invoice reuse, and reload checks. The conversation-controls screenshot was visually checked. The local app was updated after an integrity-checked SQLite backup; every existing table fingerprint matched after recreation, the V5 registry was empty, authenticated API access returned 200, and anonymous access returned 401. No real conversation was deleted.

## Invoice list editing and deletion — October 25, 2026

Added draft Edit/Delete actions directly to All invoices, reusing the existing database endpoints and optimistic edit tokens. Editing opens a dialog over the list and refreshes the row after saving. Deletion requires confirmation and preserves source work, expenses, reserved numbers, and audit history. Issued invoices retain the review and documented correction workflow.

Verified 19 frontend tests, strict production compilation, and a Linux Docker image build with 86 passing backend tests and one optional sample skipped. The disposable-database Chrome lifecycle workflow passed list and preview editing/deletion, persisted service dates and notes after reload, preserved work entries, and mobile overflow checks. The list screenshot was captured from the fixture and visually checked. The local rollout uses a consistent SQLite backup and verifies unchanged business-table fingerprints; no live invoice is edited or deleted by the rollout.

## Saved-work invoice drafts and record lifecycle — October 25, 2026

Verified 86 passing backend tests (one optional PDF sample generator skipped), 17 frontend tests, strict production compilation, a Linux Docker image build, all six Mermaid diagrams, and relative documentation/image links. New coverage includes invoice drafts from both proposed and applied conversations, unchanged work rows when invoicing applied work, reuse of older invoice receipts, reopenable receipt identities, optimistic draft edit/delete tokens, linked-period synchronization, retained invoice numbers, allocation-protected expense deletion, archive inheritance/restoration, and active-timer guards. The V2-to-current upgrade check preserves all pre-existing business rows while adding V3/V4 metadata tables.

All three headless Chrome workflows passed with disposable SQLite databases. The new lifecycle workflow starts with applied October work, creates its invoice without replaying entries, checks pending/planned separation, edits dates/notes, deletes and recreates the draft with a new number, deletes an expense, archives/restores clients/contracts/projects, edits a project, and verifies mobile layout. The existing financial and assistant workflows continue to cover finalization, withdrawal/replacement, exports, payments, correction history, timers, retries, allocation tables, and origins. Initial mobile overflow and overly broad text selectors were corrected before rollout. The new invoice-review screenshot was captured from the disposable fixture and visually checked.

The local app was updated after consistent, integrity-checked SQLite backups. V4 adds `record_archives`; every pre-existing table fingerprint matched across recreation, including work, invoices, payments, notes, and receipts. Authenticated API access returned 200, anonymous access returned 401, and generated OpenAPI exposed the new invoice edit/delete, plan invoice, and archive routes. The user's already-linked October draft was reused; the rollout did not create, delete, or replay their work/invoices, confirm hours, record payments, or finalize anything. No `.env`, provider credential, or existing migration file was changed. CI now includes the lifecycle workflow; hosted CI itself was not run in this session.

## Proposed allocation tables and invoice review — October 25, 2026

The supplied October work breakdown is retained as an acceptance fixture in `backend/src/test/resources/assistant/october-proposed-work.txt`. It produces 80 proposed hours including 16 planned hours, 12 earlier task drafts, one October 25 PR draft without assumed hours, required billing questions, and an initially excluded October 1–30 invoice action. Markdown and inherited-period table variants, explicit service ranges, and proposed/future-duration safeguards are covered separately.

Verified 76 passing backend tests (one optional sample generator skipped), 14 frontend tests, strict production compilation, and a Linux Docker build. The workflow tests cover atomic rejection of unresolved questions/future work, immutable review metadata, blank daily portions, stable action identities, invoice snapshots including split portions, and billing only actual confirmed hours. Provider error tests distinguish quota/billing, temporary throttling, and unknown 429 responses without exposing remote message text. No paid provider request was made.

Both headless Chrome workflows passed against disposable databases. The exact October prompt exercised table intake, proposed/planned totals, the October 25 task, exact invoice end date, daily splitting, billing-question blocking, saving drafts without an invoice, and exclusion of future work from the ledger. The first concurrent assistant run exceeded its disposable-server startup window; running it without the competing server passed. The allocation review screenshot was captured from the disposable fixture and visually reviewed.

The running local app was updated after an integrity-checked SQLite backup. Authenticated workspace/assistant routes returned 200, anonymous API access returned 401, the new split route appeared in OpenAPI, and the served frontend contained the allocation review UI. Every pre-existing business-table fingerprint, including saved notes, matched before and after recreation. No schema migration, credential edit, environment change, or synthetic main-database entry was needed.

## Free-form intake and action-limit fix — October 25, 2026

Verified 65 passing backend tests (one optional PDF sample generator skipped), 13 passing frontend tests, strict TypeScript/production build, repository formatting, all five Mermaid diagrams, and a Linux Docker image build. Both headless Chrome workflows passed against disposable databases. Added coverage includes configurable local limits, invoice-slot accounting, original-source preservation for 40-line narratives, notes-only application with no ledger writes, multiline provider evidence with 35 PR references as one logical work item, nonbillable provider context, and automatic selection of a configured profile with consent still required.

The existing running app was updated after a consistent, integrity-checked database backup. Authenticated workspace/assistant routes returned 200, anonymous API access returned 401, SQLite integrity returned `ok`, and pre-existing business-table fingerprints—including saved notes—matched before and after recreation. No new schema migration or environment-limit increase was required. Temporary test servers were stopped.

At rollout, all three private provider profiles were disabled and no profile was ready. The user chose to configure a provider API locally. No work notes were sent to an external provider during verification. The narrative interpretation path was checked with fixtures/mocks; actual provider quality and connectivity require the user's configured API. The first ready profile becomes the initial selection when Work Assistant opens. Offline overflow preservation is explicitly identified as nonbillable notes, not semantic interpretation or an invoice.

## Configuration and Swagger audit — October 22, 2026

The private root `.env` was compared with the current `.env.example`. Fourteen missing optional settings were appended with their documented defaults, including `ASSISTANT_MAX_REVISIONS=30` and `API_DOCS_ENABLED=true`. Existing values, including the access token and selected port, were preserved. Optional settings already had backend/Compose defaults; making them explicit does not require new credentials.

An isolated packaged backend on port 18083 verified Swagger UI, the Swagger shortcut redirect, JSON/YAML OpenAPI specifications, Swagger configuration, and the bundled OAuth redirect helper. With documentation enabled, the shortcut returned HTTP 302 and the other five routes returned HTTP 200. With documentation disabled, all six returned HTTP 404. In both modes, `/api/state` returned 401 without authentication and 200 with the fixture token. The generated specification included the assistant correction/reopen endpoints and the HTTP bearer security scheme. Temporary processes were stopped; the live database and main app were not used.

The README and API, configuration, development, operations, architecture, and assistant guides were reconciled with the current settings, authentication, migration history, and workflow. The API guide lists the full Swagger URLs, runtime-specific base addresses, authorization instructions, documentation toggle, and troubleshooting. Documentation formatting, local links, and Mermaid parsing were checked separately from the application test runs recorded below.

## Conversational invoice workflow — October 22, 2026

The conversational workflow was verified with 54 passing backend tests (one optional PDF sample generator skipped), 10 passing frontend tests, strict TypeScript checks, a production frontend build, Maven packaging, and repository formatting checks. All five documentation Mermaid diagrams parse successfully.

New backend coverage includes professional wording proposals, preserved commands, correction consent/version checks, clarification without partial changes, rejection of fabricated time and financial actions, invoice-period coverage, saved draft reopening, stale record protection, excluding/reincluding work without changing task identity, invoice reuse, and corrected descriptions in exports. Provider HTTP/template tests use a local fixture and mocked responses; paid/live model quality was not evaluated.

Both browser workflows passed in headless Chrome against separate disposable SQLite files. The assistant workflow corrected a saved two-hour task to 90 minutes, refreshed the invoice preview to 2.00 billable hours/$150.00 including its other half-hour task, retained nine total work entries and one invoice, and restored correction history after reload. Mobile layout and JavaScript error checks passed. The manual workflow continued to cover finalization, withdrawal/replacement, exports, email preparation, submission, and payments.

The V2-to-V3 upgrade test created an existing database with composer notes and an applied receipt, applied only V3, compared all 15 pre-existing business tables, and verified `PRAGMA integrity_check = ok`. Existing migration files were unchanged. The live database was not opened or migrated during this development work. The main Docker app remains stopped; temporary browser servers were stopped after their tests. Rebuild the app when ready to run the new version.

The new correction screenshot in `docs/images/assistant-corrections.png` was captured from the isolated browser fixture and visually inspected. GitHub Actions and live external providers were not run in this session.

## Previous structured application release

The earlier refactored release was built and tested locally on Windows and in the Linux Docker build. It was deployed at **http://127.0.0.1:18080** and subsequently stopped at the user's request. Port 8080 is unavailable on this workstation; the private root `.env` selects 18080. The evidence below describes that earlier deployment.

| Check                      | Executed result                                                                                                                                                                                                  |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Backend build/tests        | 39 passing tests; 1 optional PDF sample generator skipped. Docker image build succeeded.                                                                                                                         |
| Ledger/service integration | 13 passing tests for money, eligibility, snapshots, allocations, corrections, expenses, payments, timer behavior, and contract review.                                                                           |
| Assistant integration      | 9 passing tests for explicit facts, missing/ambiguous facts, saved drafts, confirmation/version checks, atomic rollback, reports, and retry/duplicate protection.                                                |
| HTTP/authentication        | 9 passing MockMvc tests for bearer access, explicit confirmation, typed request validation/mapping, structured-field rejection, origin allowlists, malformed assistant actions, and fractional-second rejection. |
| Runtime configuration      | 4 passing tests for overrides, token constraints, resource bounds, timeouts, and secret redaction.                                                                                                               |
| Package boundaries         | 3 passing checks for controller/persistence separation, repository/service separation, and framework-independent domain code.                                                                                    |
| Provider adapter           | 1 passing local-fixture test for credential handling, live config reload/disable, verbatim coverage, and rejection of invented/omitted source/signs.                                                             |
| Frontend                   | 7 passing Vitest/Testing Library tests. Strict TypeScript, unused-variable/parameter checks, and production build pass.                                                                                          |
| Formatting                 | Repository Prettier check passes for application source, public JSON/YAML, workflows, Markdown, and browser scripts. Applied migrations/private files are excluded.                                              |
| Financial browser workflow | Passed in headless Chrome against a disposable container/database.                                                                                                                                               |
| Assistant browser workflow | Passed in headless Chrome against a separate disposable container/database.                                                                                                                                      |
| Local database             | Integrity check returned `ok`; business-data fingerprints matched before and after deployment.                                                                                                                   |

The GitHub Actions CI workflow is configured to run formatting, types, frontend tests/build, Maven verification, and both browser workflows. It was not executed remotely in this session. The optional Pages workflow was prepared earlier and remains manually triggered; nothing was published.

## Browser coverage

The financial workflow covered token login, incomplete seeds, actual work entry, weekly review dialogs, invoice drafting/finalization, withdrawal/correction/linked replacement, PDF/XLSX/CSV downloads, edited submission text and CC copying, actual submission date, approval, partial payment, historical-receipt isolation, dark theme, and mobile layout.

The assistant workflow covered autosaved notes, local parsing, editable review fields, explicit confirmation, a batch producing 2.5 hours and a $187.50 invoice draft/report, idempotent application, reload recovery, invoice review, start/pause/resume/stop timer commands, draft creation on stop, allowed-origin preflight, rejection of an untrusted origin, and mobile layout. No browser JavaScript errors or document-level horizontal overflow were observed.

All financial/browser fixtures used the fixed isolated test credential and temporary SQLite files. No synthetic work, invoices, or payment events were added to the live database.

## Persistence and migration evidence

Before replacing the running service, it was cleanly stopped and backed up to a timestamped file under ignored `backups/`. A fingerprint was recorded for clients, contracts, projects, entries, weekly reviews, expenses, invoices, payments, historical records, audit records, assistant drafts, and assistant plans.

After deployment, authenticated `/api/state` and `/api/assistant` returned 200; anonymous API access returned 401. SQLite integrity remained `ok`, and the complete business-data fingerprint was unchanged. The live workspace retained its seven original draft entries and existing client/contract/project/history records, with zero test invoices/payments/plans added by verification.

Both applied Flyway SQL files were compared byte for byte against the source baseline and remained unchanged. This refactor required no schema migration. The original source baseline and superseded implementations were retained under ignored `tmp/` for recovery; database backups and the earlier named volume remain separate from those source archives.

## Visual review

Five documentation screenshots were captured from the isolated assistant demonstration database and inspected: dashboard, assistant review, invoice preview, dark dashboard, and 390-pixel mobile dashboard. README images are under `docs/images/`. The wider invoice framing preserves readable category/hour columns. The earlier zero-value sample invoice PDF remains a draft with no invented work; export generation is covered by the ledger and browser checks.

## Scope and maintenance

The product remains a local single-user application with a bearer token. It supports reference URLs rather than binary attachment uploads. Email sending, multi-user identity/roles, credit/refund accounting, and selectable PostgreSQL storage are outside this release. Provider tests used a local HTTP fixture; no paid account or live cloud inference was used.

The requested Spring Boot 3.x stack is pinned to 3.5.16, its [final open-source maintenance release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). A supported-line upgrade or commercial maintenance remains a future lifecycle decision. PDF rendering uses standard PDF fonts; CSV/XLSX remain the full-Unicode export options for text outside that font repertoire.
