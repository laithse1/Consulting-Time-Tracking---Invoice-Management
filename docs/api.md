# REST API

All `/api/**` routes require `Authorization: Bearer <APP_TOKEN>`. Requests/responses use JSON except downloads. Errors include HTTP 400 (invalid input/operation), 401 (missing/incorrect token), 403 (rejected origin), and 409 (database constraint conflict).

## Swagger and OpenAPI URLs

The following links use this workstation's Docker address, **http://127.0.0.1:18080**. They become available after the backend starts with `API_DOCS_ENABLED=true` (the default).

| Resource               | URL                                                                               | Purpose                                                                                                        |
| ---------------------- | --------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Swagger UI             | [Interactive API reference](http://127.0.0.1:18080/swagger-ui/index.html)         | Browse operations, authorize, and execute requests.                                                            |
| Swagger entry redirect | [Swagger shortcut](http://127.0.0.1:18080/swagger-ui.html)                        | Returns HTTP 302 to `/swagger-ui/index.html`.                                                                  |
| OpenAPI JSON           | [JSON specification](http://127.0.0.1:18080/v3/api-docs)                          | Generated operation and schema definitions.                                                                    |
| OpenAPI YAML           | [YAML specification](http://127.0.0.1:18080/v3/api-docs.yaml)                     | The same specification in YAML format.                                                                         |
| Swagger configuration  | [UI configuration JSON](http://127.0.0.1:18080/v3/api-docs/swagger-config)        | Configuration consumed by Swagger UI, including the specification URL.                                         |
| OAuth redirect helper  | [Bundled redirect helper](http://127.0.0.1:18080/swagger-ui/oauth2-redirect.html) | Swagger's bundled helper page. This app uses bearer authentication and does not configure an OAuth login flow. |

Swagger JavaScript, CSS, and supporting resources are served under `/swagger-ui/`; open the UI entry point rather than individual assets. There are no named OpenAPI groups or separate admin/management Swagger pages configured in this application.

### Which base URL should I use?

| Runtime                                  | Backend base URL                | Swagger UI example                              |
| ---------------------------------------- | ------------------------------- | ----------------------------------------------- |
| This workstation's Docker `.env`         | `http://127.0.0.1:18080`        | `http://127.0.0.1:18080/swagger-ui/index.html`  |
| Fresh Compose setup using `.env.example` | `http://127.0.0.1:8080`         | `http://127.0.0.1:8080/swagger-ui/index.html`   |
| Native Java/Maven using the default port | `http://127.0.0.1:8080`         | `http://127.0.0.1:8080/swagger-ui/index.html`   |
| Custom port or separately hosted backend | The backend's configured origin | Append `/swagger-ui/index.html` to that origin. |

Docker URLs use the host `APP_PORT`, not its internal container port. Native Java does not automatically read the root Compose `.env`; its actual port comes from the launching environment or Spring configuration. During Vite development, open Swagger on the Java backend, not port 5173: Vite proxies `/api`, not `/swagger-ui` or `/v3/api-docs`. A GitHub Pages frontend also uses the separately hosted backend's Swagger URL.

### Authorize and try a request

1. Start the backend, then open **Swagger UI** above.
2. Click **Authorize** and enter the `APP_TOKEN` value for that backend into `localToken`. Enter only the value: Swagger adds the `Bearer ` prefix.
3. Confirm **Authorize**, close the dialog, expand **GET `/api/state`**, select **Try it out**, and **Execute**. A valid token returns HTTP 200; a missing/incorrect token returns HTTP 401.
4. Review a request's input before executing a write operation. Swagger calls the same live services and database as the application; use an isolated development database for experiments.

Documentation pages and specifications are readable without the token when enabled. Executing `/api/**` operations still requires authorization and applicable origin checks. No token or provider credential is included in the generated specification. `APP_TOKEN` grants workspace API access; provider API keys belong in the server-side provider configuration.

### Enable, disable, and troubleshoot

Set `API_DOCS_ENABLED=true` in the root `.env` for Compose, or export it for native Java. To disable the documentation, set it to `false`. Recreate the Compose service with `docker compose up -d` after a runtime environment change, or restart native Java. When documentation is disabled, the listed documentation endpoints return HTTP 404; the authenticated workspace API remains available. Rebuild with `docker compose up --build -d` when application source has changed as well.

For a 404, check the backend origin/port and documentation flag. For “Failed to load API definition,” inspect `/v3/api-docs` and `/v3/api-docs/swagger-config` on the same backend origin, then check backend logs. A proxy deployment must forward `/swagger-ui/`, `/swagger-ui.html`, `/v3/api-docs`, and `/v3/api-docs.yaml`, including nested configuration requests. Do not put the token in a URL/query string.

## Workspace endpoints

| Method | Route                                                    | Purpose                                                                                                                                                      |
| ------ | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| GET    | /api/state                                               | Persistent workspace including clients, contracts, projects, entries, invoices, payments, expenses, timer and audit                                          |
| POST   | /api/clients                                             | Create/update `{id?,name,email,address}`                                                                                                                     |
| POST   | /api/contracts                                           | Create/update full contract configuration                                                                                                                    |
| POST   | /api/projects                                            | Create/update `{id?,client_id,name,repository}`                                                                                                              |
| POST   | /api/entries                                             | Create/update actual or incomplete work                                                                                                                      |
| DELETE | /api/entries/{id}?confirmed=true                         | Explicitly delete unallocated work, preserving audit                                                                                                         |
| POST   | /api/timer/start                                         | `{contract_id,project_id,description,category,work_date}`                                                                                                    |
| POST   | /api/timer/pause, /resume, /stop                         | Pause/resume/stop singleton timer; stop creates draft                                                                                                        |
| GET    | /api/compliance?contract=1&from=2026-09-01&to=2026-09-30 | Whole-week actuals and contractual discrepancies                                                                                                             |
| POST   | /api/weekly                                              | `{contract_id,week,available,exception_ref,overtime_ref,acknowledgement}`                                                                                    |
| POST   | /api/expenses                                            | `{contract_id,expense_date,description,amount,approval_ref}`                                                                                                 |
| POST   | /api/invoices                                            | Create draft with `{contract_id,period_start,period_end,presentation,adjustment?,adjustment_reason?,adjustment_approval?,replacement_for?,revision_reason?}` |
| GET    | /api/invoices/{id}                                       | Preview, frozen content if issued, and current status/balance                                                                                                |
| POST   | /api/invoices/{id}/finalize                              | `{confirmed:true}`, warnings must be resolved                                                                                                                |
| POST   | /api/invoices/{id}/status                                | `{state:"Sent",submitted:"YYYY-MM-DD"}` or `{state:"Approved"}` or `{state:"Disputed",notes:"..."}`                                                          |
| POST   | /api/invoices/{id}/payments                              | `{amount:"100.00",received:"YYYY-MM-DD",method:"ACH",reference:"..."}`                                                                                       |
| POST   | /api/invoices/{id}/withdraw                              | `{confirmed:true,reason:"..."}`; unpaid issued invoices only; preserves snapshot and releases source work for correction before a linked replacement         |
| GET    | /api/invoices/{id}/email                                 | Copyable `{to,subject,body}`; does not send                                                                                                                  |
| GET    | /api/invoices/{id}/export/pdf, /xlsx, /csv               | Download full dated invoice contents                                                                                                                         |
| GET    | /api/history.csv                                         | Invoice, payment and separate historical records                                                                                                             |
| POST   | /api/historical                                          | Confirm prior-work receipt `{received,reference}`                                                                                                            |

Entry payload: `id?`, `contract_id`, `project_id`, `work_date` (nullable for draft), `start_time` (optional `HH:mm`), `seconds` (integer), `category`, `description`, `repository`, `reference`, `notes`, `billable` (0/1), `draft` (0/1). Confirmed work requires a date and positive actual duration, at most 24 hours. When clock times are supplied, work must fit within one local day and not overlap other clock-based entries. Duration-only entries retain no invented clock start; exact duplicates are rejected. Date must fit the contract and the project must belong to that client. Entries already allocated to finalized invoices cannot be changed/deleted.

## Assistant endpoints

These endpoints use the same bearer authentication.

| Method | Path                             | Purpose                                                                                                                                                           |
| ------ | -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | /api/assistant?contract=1        | Saved composer draft, configured recent plans/receipts and input limits, nonsecret provider metadata                                                              |
| POST   | /api/assistant/draft             | Persist `{contract_id,source}`                                                                                                                                    |
| POST   | /api/assistant/plan              | Persist source and propose `{contract_id,source,reference_date,provider_id?,provider_consent?,prepare_invoice?,period_start?,period_end?}` without ledger changes |
| PUT    | /api/assistant/plans/{id}        | Review `{version,actions}`; preserves action count/type, increments version                                                                                       |
| POST   | /api/assistant/plans/{id}/apply  | `{version,confirmed:true}` applies the batch atomically; repeating an applied plan returns the stored receipt                                                     |
| POST   | /api/assistant/plans/{id}/revise | `{version,instruction,provider_id?,provider_consent?}` proposes existing work-task edits and stores a versioned correction/reply without ledger writes            |
| POST   | /api/assistant/plans/{id}/reopen | `{version}` reopens editable saved work/invoice receipts and binds subsequent application to existing IDs                                                         |

The assistant permits work logging, timer controls, invoice drafting, reports, weekly compliance review, and preparation of an existing invoice's email. It does not permit payment/status changes, finalization, or email sending. Provider requests occur outside database transactions and require explicit consent. Source is saved before parsing/network extraction so provider failures do not discard notes. See `docs/work-assistant.md` for the grammar and provider configuration.

Plan responses include `revisions` (saved instructions, replies, versions, engine, timestamps) and a server-calculated `review` summary. Actions may contain `source_text`, `wording_suggestion`, and `skip`. Internal `_entry_id`, `_entry_baseline`, and `_invoice_id` fields on reopened plans must be round-tripped unchanged; clients cannot bind arbitrary records. Invoice results include the full `preview`. Reopened plans retain the previous stored receipt but hide results in the response until reapplied. Invoice period changes require a new draft rather than altering a bound invoice.

Stored financial amounts and request amounts use decimal strings; computed responses are serialized from exact BigDecimal values. Clients must not replace authoritative backend arithmetic with binary floating-point calculations. Every invoice uses a unique sequential number within its start month. Finalization, allocation and replacement are a single database transaction. Monetary adjustment approval is supplied explicitly, independently from weekly acknowledgement. API state changes and prior entry versions are recorded in `audit`.

Submission is deliberately manual. A future email adapter should consume the email payload plus an issued PDF and require explicit per-send consent. It must record actual delivery/receipt time only after confirmed delivery; never infer submission from merely downloading or copying an email.
