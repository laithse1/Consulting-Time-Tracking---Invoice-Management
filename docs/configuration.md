# Configuration reference

Configuration is split by responsibility. Runtime defaults live in `backend/src/main/resources/application.yml`; `ApplicationProperties` validates application settings. Contract-specific financial terms live in SQLite. Public UI settings live in `frontend/src/config/application.ts`. Provider credentials live only in the ignored server-side provider file or explicitly supplied backend environment variables.

## Backend and Docker

| Variable                          | Default                             | Meaning                                                                                                                                          |
| --------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `APP_TOKEN`                       | Required                            | Private single-user access token, minimum 24 characters.                                                                                         |
| `APP_PORT`                        | `8080`                              | Native server port; Docker Compose host port. Docker's internal listener stays on 8080.                                                          |
| `BIND_ADDRESS`                    | `127.0.0.1`                         | Native bind address. Compose supplies `0.0.0.0` inside the container and publishes only host localhost.                                          |
| `DATA_DIR`                        | `./data`                            | Docker host bind-mount directory. It is not a native Spring setting.                                                                             |
| `DB_PATH`                         | `./data/consulting.db`              | Native SQLite file. Docker uses `/app/data/consulting.db` inside the mount.                                                                      |
| `APP_ALLOWED_ORIGINS`             | Empty                               | Comma-separated exact frontend origins for a split deployment. Include scheme and port, exclude paths and wildcards.                             |
| `ASSISTANT_PROVIDER_CONFIG`       | `./config/assistant-providers.json` | Native provider file; Docker supplies `/app/config/assistant-providers.json`.                                                                    |
| `INVOICE_NUMBER_PREFIX`           | `LA`                                | Prefix for new invoices. Uppercase letters/digits, starts with a letter, maximum 12 characters. Existing numbers and snapshots remain unchanged. |
| `ASSISTANT_MAX_SOURCE_CHARACTERS` | `10000`                             | Composer input limit; valid range 100–10000. The UI reads this limit from the assistant API.                                                     |
| `ASSISTANT_MAX_ACTIONS`           | `30`                                | Maximum actions per plan; valid range 1–30.                                                                                                      |
| `ASSISTANT_HISTORY_LIMIT`         | `50`                                | Plans returned per history request; valid range 1–500. Older plans remain stored.                                                                |
| `ASSISTANT_MAX_REVISIONS`         | `30`                                | Saved conversational revisions/reopens per plan; valid range 1–100. Review fields remain available at the limit.                                 |
| `AI_CONNECT_TIMEOUT`              | `8s`                                | Provider connection timeout.                                                                                                                     |
| `AI_REQUEST_TIMEOUT`              | `35s`                               | Provider HTTP request timeout.                                                                                                                   |
| `AI_RESPONSE_TIMEOUT`             | `20s`                               | Bounded response-body reading timeout.                                                                                                           |
| `AI_MAX_RESPONSE_BYTES`           | `1048576`                           | Response size cap; valid range 1024–10485760 bytes.                                                                                              |
| `AI_MAX_CONFIG_BYTES`             | `262144`                            | Provider file cap; valid range 1024–1048576 bytes.                                                                                               |
| `LOG_LEVEL`                       | `INFO`                              | Root logging level. Avoid verbose HTTP logging around credentials.                                                                               |
| `API_DOCS_ENABLED`                | `true`                              | Enables/disables Swagger UI, JSON/YAML specifications, redirects, and UI configuration. See [Swagger URLs](api.md#swagger-and-openapi-urls).     |
| `TZ`                              | `America/Chicago` in Compose        | Container timezone. Native Java uses its configured system timezone. Work dates remain explicit local dates; timer/audit instants are UTC.       |

Provider timeouts must be positive and at most five minutes. Spring configuration properties can also be supplied through standard Spring mechanisms, for example `--app.assistant.history-limit=100`. Never put credentials on a command line that may be captured in process listings.

For Docker, the root `.env` is consumed by Compose; only explicitly listed variables are forwarded to the container. Add/change a runtime value and recreate the service with `docker compose up -d`. For native execution, set environment variables in the launching process. The application does not automatically load the root `.env` when Java runs directly.

On upgrades, compare setting names with `.env.example` and add missing settings without replacing your private file. Omitted optional settings already use the defaults above. The conversational assistant adds `ASSISTANT_MAX_REVISIONS=30`; it does not require a new workspace token or provider profile format. `.env` controls runtime limits, while language-provider models, endpoints, and credentials are configured separately. Never replace an existing token or provider key with an example value.

## Public frontend settings

These settings are embedded in browser assets. Use `frontend/.env` for Vite development/native builds or the corresponding root `.env` values for Docker build arguments, then rebuild.

| Variable                     | Default                      | Meaning                                                                                    |
| ---------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------ |
| `VITE_APP_TITLE`             | `Consulting Invoice Manager` | Public application title.                                                                  |
| `VITE_WORKSPACE_LABEL`       | `Independent consulting`     | Workspace subtitle.                                                                        |
| `VITE_API_BASE_URL`          | Empty                        | Empty means same origin. For a split deployment, supply the backend origin without `/api`. |
| `VITE_BASE_PATH`             | `/`                          | Static asset base path; Pages repository sites use `/<repository>/`.                       |
| `VITE_DEV_API_TARGET`        | `http://127.0.0.1:8080`      | Vite development proxy target.                                                             |
| `VITE_ASSISTANT_AUTOSAVE_MS` | `600`                        | Notes autosave delay, 100–10000 ms.                                                        |
| `VITE_TIMER_TICK_MS`         | `1000`                       | Timer display refresh, 100–10000 ms. Elapsed time remains based on persisted timestamps.   |
| `VITE_NOTICE_DURATION_MS`    | `5000`                       | Success notice duration, 1000–60000 ms.                                                    |

Numeric UI settings fail fast when malformed. Vite values are public; no token or provider key belongs in them. Restart Vite after changing development settings. Rebuild/recreate Docker when changing frontend build arguments.

## Provider profiles

Copy the public template to `config/assistant-providers.json` if the private file does not already exist. Preserve existing configured profiles when upgrading. The template's profiles are disabled by default. Set `enabled`, endpoint, model, headers, request JSON, response pointer, and credentials for the intended provider. Configuration is re-read for every request. Reload Work Assistant to refresh the selector.

The adapter supports `{{input}}`, `{{instructions}}`, `{{model}}`, `{{secret:NAME}}`, and `{{env:NAME}}` substitutions for extraction, invoice wording, and conversational corrections. Substitution happens once so placeholder-like text in user notes stays literal data. Secrets remain backend-side. Environment substitutions require variables actually supplied to that Java process/container. For Docker, add those specific variables through an override rather than assuming every value in `.env` is forwarded.

No model is installed automatically. HTTP is allowed only for local inference endpoints; remote providers require HTTPS. Requests do not follow redirects. Provider output must preserve disjoint verbatim source spans, including signs/punctuation, and cover all non-whitespace source. Every provider suggestion remains draft work. See [provider adapter details](work-assistant.md#optional-providers).

## Business configuration

Clients & Contracts owns rates, contractual start/end dates, weekly minimum/maximum hours, week start, payment/dispute terms, addresses, and payment instructions. Projects own repository references. These values are stored in SQLite and are not deployment defaults.

Actual work, approvals, invoice finalization, receipts, and payments require explicit facts and user actions. USD, exact decimal arithmetic, line-level cent rounding, immutable issued snapshots, and unique invoice allocations are application invariants; environment settings cannot disable them.
