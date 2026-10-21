# REST API

All `/api/**` routes require `Authorization: Bearer <APP_TOKEN>`. Requests/responses use JSON except downloads. Errors use HTTP 400 (invalid operation/input), 401 (authentication), or Spring error responses. OpenAPI is generated at `/v3/api-docs`; Swagger UI is `/swagger-ui/index.html`.

| Method | Route | Purpose |
|---|---|---|
| GET | /api/state | Persistent workspace including clients, contracts, projects, entries, invoices, payments, expenses, timer and audit |
| POST | /api/clients | Create/update `{id?,name,email,address}` |
| POST | /api/contracts | Create/update full contract configuration |
| POST | /api/projects | Create/update `{id?,client_id,name,repository}` |
| POST | /api/entries | Create/update actual or incomplete work |
| DELETE | /api/entries/{id}?confirmed=true | Explicitly delete unallocated work, preserving audit |
| POST | /api/timer/start | `{contract_id,project_id,description,category,work_date}` |
| POST | /api/timer/pause, /resume, /stop | Pause/resume/stop singleton timer; stop creates draft |
| GET | /api/compliance?contract=1&from=2026-09-01&to=2026-09-30 | Whole-week actuals and contractual discrepancies |
| POST | /api/weekly | `{contract_id,week,available,exception_ref,overtime_ref,acknowledgement}` |
| POST | /api/expenses | `{contract_id,expense_date,description,amount,approval_ref}` |
| POST | /api/invoices | Create draft with `{contract_id,period_start,period_end,presentation,adjustment?,adjustment_reason?,adjustment_approval?,replacement_for?,revision_reason?}` |
| GET | /api/invoices/{id} | Preview, frozen content if issued, and current status/balance |
| POST | /api/invoices/{id}/finalize | `{confirmed:true}`, warnings must be resolved |
| POST | /api/invoices/{id}/status | `{state:"Sent",submitted:"YYYY-MM-DD"}` or `{state:"Approved"}` or `{state:"Disputed",notes:"..."}` |
| POST | /api/invoices/{id}/payments | `{amount:"100.00",received:"YYYY-MM-DD",method:"ACH",reference:"..."}` |
| POST | /api/invoices/{id}/withdraw | `{confirmed:true,reason:"..."}`; unpaid issued invoices only; preserves snapshot and releases source work for correction before a linked replacement |
| GET | /api/invoices/{id}/email | Copyable `{to,subject,body}`; does not send |
| GET | /api/invoices/{id}/export/pdf, /xlsx, /csv | Download full dated invoice contents |
| GET | /api/history.csv | Invoice, payment and separate historical records |
| POST | /api/historical | Confirm prior-work receipt `{received,reference}` |

Entry payload: `id?`, `contract_id`, `project_id`, `work_date` (nullable for draft), `start_time` (`HH:mm`), `seconds` (integer), `category`, `description`, `repository`, `reference`, `notes`, `billable` (0/1), `draft` (0/1). Confirmed work requires a date, start time and positive duration within one local day. Date must fit the contract and the project must belong to that client. Entries already allocated to finalized invoices cannot be changed/deleted.

Financial amounts are decimal strings, not binary floating-point values. Every invoice uses a unique sequential number within its start month. Finalization, allocation and replacement are a single database transaction. Monetary adjustment approval is supplied explicitly, independently from weekly acknowledgement. API state changes and prior entry versions are recorded in `audit`.

Submission is deliberately manual. A future email adapter should consume the email payload plus an issued PDF and require explicit per-send consent. It must record actual delivery/receipt time only after confirmed delivery; never infer submission from merely downloading or copying an email.
