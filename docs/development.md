# Development guide

The invoice/record lifecycle browser workflow runs with `npm run e2e:lifecycle` from `frontend` after packaging. It launches a disposable SQLite instance on port 18084 and stops it on exit. It covers draft creation from a proposed plan, pending work, period edits, deletion, client/contract/project archive/restore, and project editing. `E2E_JAR` selects an alternate packaged jar; `E2E_BROWSER_CHANNEL=bundled` selects installed Playwright Chromium. CI includes this workflow alongside the existing assistant and financial workflows. Never use production data for browser tests.

## Build the repository

Use Java 21+, Maven 3.9+, Node.js 22.12+, npm, and PowerShell 7+ for the Windows scripts. Docker Desktop is an alternative runtime/build environment. Dependency versions are controlled by the Maven parent/properties and npm lockfile.

```bash
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run build
mvn -f backend/pom.xml clean verify
```

`./scripts/start-local.ps1` packages the frontend into Spring Boot and launches the native application. `docker compose up --build -d` builds and runs the same packaged UI/API in Docker. Keep development/test databases separate from live financial data.

For hot reload, follow the two-terminal setup in the root README. Vite serves port 5173 and proxies `/api` to `VITE_DEV_API_TARGET`. Java does not load the root Compose `.env` automatically; supply its token/database environment explicitly.

## Swagger during development

For default native development, open [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html), [OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs), or [OpenAPI YAML](http://127.0.0.1:8080/v3/api-docs.yaml) on port 8080. The current Docker setup uses port 18080 instead. Use the backend directly rather than the Vite or Pages frontend origin. In **Authorize**, supply only the backend's `APP_TOKEN` value. Use a development database for write requests. [The API guide](api.md#swagger-and-openapi-urls) lists the redirect/configuration/helper URLs, documentation flag, and troubleshooting.

## Backend responsibilities

| Package                                                    | Responsibility                                                                                                                      |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `controller`                                               | Routes, typed requests, Bean Validation, response adaptation. No JDBC/provider implementation access.                               |
| `dto.request`                                              | Wire contracts using snake_case names compatible with the API.                                                                      |
| `service`                                                  | Business decisions, validation, transaction boundaries, and cross-repository coordination.                                          |
| `service.assistant`                                        | Saved drafts, wording proposals, versioned conversations/review, atomic application, editable receipt reopening, and exact reports. |
| `service.export`                                           | Rendering existing invoice data; issued invoices use stored snapshots.                                                              |
| `repository`                                               | Parameterized SQL, row lookups, insert identities, and persistence. No service/controller dependencies.                             |
| `domain`                                                   | Framework-independent validation, arithmetic, work periods, and deterministic text parsing.                                         |
| `integration.ai`                                           | Bounded HTTP JSON extraction, wording, and correction suggestions; no execution tools or database access.                           |
| `config`, `security`, `validation`, `exception`, `support` | Configuration, request/authentication boundaries, error envelopes, and serialization adapters.                                      |

This is Spring JDBC, not JPA: database rows and snapshot projections use maps, with typed HTTP DTOs and explicitly typed repository arguments. Do not add unused entity/DTO layers merely to mirror package names. `RequestMapper` removes null optional fields so domain defaults stay intentional. `JsonDocuments` preserves decimal precision on snapshot reload.

The `WorkspaceService` facade is an application boundary for assistant/export integrations. Financial behavior belongs to the feature services, not that facade or controllers. Keep constructors explicit and fields private/final.

## Adding or changing behavior

1. Define the HTTP contract if it changes, preserving compatibility or documenting a migration.
2. Put validation and transactional orchestration in the owning feature service.
3. Put SQL in the corresponding repository. Bind values; keep table names within explicit allowlists.
4. Add a new Flyway migration for schema changes. Never reformat or edit an applied migration.
5. Extend the feature UI/API interaction and configuration reference where needed.
6. Verify the actual business boundary, then run the relevant full workflow for financial changes.

Amounts use `BigDecimal`; durations use integer seconds. Round each work line half-up to cents before summing. Do not use floating-point values for authoritative financial calculations. Preserve snapshots and append-only financial history. Never infer worked time from contractual minimums or an estimated description.

## Frontend responsibilities

`App` composes the workspace provider and shell. `app` owns navigation/composition. `hooks/useWorkspaceController` coordinates authorized refreshes, status, and mutations. Feature folders own pages and dialogs; `features/assistant` owns its types, state hook, editors, and receipts. Shared controls/tables live under `components`; transport, formatting, and forms live under `lib`.

Use `@/` imports to identify source-root modules. Public deployment settings live in `config/application.ts`, with a Vite example file. Domain vocabulary is distinct from runtime settings. Financial values shown by React are displays; backend values remain authoritative.

Keep async responses from reopening a locked workspace. Preserve the authorization/sequence guard in the workspace hook. Serialize composer saves and keep failed/unsaved wording visible. Explicit review controls must remain associated with the version being applied.

## Formatting and checks

```bash
npm --prefix frontend run format
npm --prefix frontend run format:check
npm --prefix frontend run typecheck
npm --prefix frontend test
mvn -f backend/pom.xml clean verify
```

Formatting covers application source, public JSON/YAML configuration, Markdown, browser scripts, and Java. It excludes secrets, databases, backups, migrations, generated assets, and dependencies. EditorConfig describes indentation/encoding. Java uses four spaces; frontend/configuration uses two.

`PackageBoundariesTest` prevents controllers from taking persistence/provider dependencies, repositories from taking service/controller dependencies, and domain code from taking Spring/application adapter dependencies. Configuration tests exercise overrides, resource limits, and secret redaction. HTTP tests exercise authentication, typed mapping, invalid payloads, and origin checks.

When IDE Java compilation competes with Maven output, use an isolated build directory:

```bash
mvn -f backend/pom.xml -Dbuild.output-directory=../tmp/backend-check verify
```

Do not run concurrent Maven builds against the same output directory. Docker builds also isolate compilation from the IDE.

## Browser workflows

Install Chrome for the default scripts. Alternatively install bundled Chromium with `npx playwright install chromium` from `frontend` and set `E2E_BROWSER_CHANNEL=bundled`.

After packaging the application, run `npm --prefix frontend run e2e:smoke` from the repository root. Direct `node e2e/smoke.mjs` invocation must use `frontend` as its working directory. The smoke script launches its own Java process on port 18081 with a fresh temporary SQLite database and closes it after testing. It checks invoice finalization, corrections/replacement, exports, email copying, submission, payment, and responsive layouts.

The assistant script launches and stops its own isolated Java server on port 18082, using a fresh temporary SQLite database, disabled providers, and the fixed test token. Run it with `npm run e2e:assistant` from `frontend`. It checks saved correction messages, review enforcement, draft invoice/entry reuse, invoice previews, reload, timers, mobile layout, and origins. Both scripts support `E2E_JAR` for an alternative packaged jar. `E2E_BASE_URL` instead targets an externally managed disposable instance: configure the fixture token `e2e-isolated-test-token-not-a-real-secret`, and for assistant origin checks set `APP_ALLOWED_ORIGINS=https://example.test`. Both scripts refuse a non-fresh initial entry count. Never target the live database.

CI provisions its own disposable database and bundled browser. It runs formatting, TypeScript, frontend tests/build, Maven verification, and both browser workflows. The Pages publishing workflow is separate and manually triggered.

## Review checklist

Describe the behavior, validation, and tested boundaries in a change. For financial work, check incomplete drafts, duplicate allocation, partial weeks, payment arithmetic, and frozen snapshots. For provider work, check consent, credentials, response limits, fabricated/omitted source, and draft enforcement. For UI work, check keyboard/dialog behavior, desktop/mobile overflow, and lock-state handling. Update README screenshots only from isolated demonstration data.
