# Operations and recovery

## Editing, deleting, and archiving records

Saved conversations have a **Delete** button beside each row. Confirmation removes them from recent history, closes the selected conversation, and persists a soft-deletion marker with an audit event. It does not erase source messages, revisions, receipts, composer notes, work entries, or invoices. Invoice-linked review and correction access remains available, including all unresolved billing safeguards. Reload before retrying if another tab changed the plan version.

| Record                    | Available operation                                                                   | History protection                                                                                                       |
| ------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Invoice draft             | Edit period, invoice date, presentation, approved adjustment, and notes; delete draft | Number stays reserved; prior edits/deletion remain audited; source work and expenses remain                              |
| Finalized invoice         | Withdraw an unpaid invoice and create a documented replacement                        | Issued snapshot and payment history are retained; paid invoices require an accounting correction outside direct deletion |
| Work entry                | Edit/delete from Time Tracker or an editable invoice's work table                     | Allocated issued work is protected; prior contents remain audited                                                        |
| Expense                   | Edit/delete using its Review dialog                                                   | Allocated issued expenses are protected; deletion is audited                                                             |
| Client, contract, project | Edit and archive/restore from their management screens                                | Relationships and historical work/invoices/payments remain intact; no cascade deletion                                   |

Open a saved conversation, whether proposed or already applied, and choose **Create invoice draft** to create or open its document without replaying work. Draft previews show pending and planned rows separately from billable totals. Return to the exact linked plan through **Review linked work in Work Assistant**. Existing invoice receipts are reused; financial confirmation/finalization remains explicit.

Draft edits/deletion require the current preview token. If another window changed the invoice, reload it and review the latest version. Invoice-period changes update linked assistant plans and invalidate stale plan versions. Deleted drafts disappear from active lists but retain their number in SQLite; never renumber later invoices to close a gap.

Archived records are labeled and remain available for historical review. A client's effective archive state also applies to its contracts/projects; restoring the client preserves any independent child archive. Stop an active timer before archiving its owner. Restore a record before adding new work under it. Existing work may still be corrected and invoiced, and payments on historical invoices can still be recorded.

Flyway V4 adds `record_archives` without rewriting existing business tables. No `.env` or provider configuration changes are needed. Back up the database before upgrades as described below.

## Runtime and storage

The packaged app runs one Spring Boot process serving the React assets and REST API. Compose binds `127.0.0.1:<APP_PORT>` to container port 8080 and bind-mounts `<DATA_DIR>/consulting.db` at `/app/data/consulting.db`. Provider profiles are read from the read-only `/app/config` mount. Keep provider files and database/backups private.

The Java runtime is Ubuntu Temurin 21 (glibc), runs as UID/GID 10001, drops capabilities, and uses a read-only root filesystem. `/tmp` is a bounded executable mount because SQLite JDBC extracts a native library there. Do not change that mount to `noexec` or switch the runtime to Alpine without addressing the SQLite native-library requirements.

```bash
docker compose ps
docker compose logs --tail 100 app
docker compose restart app
```

The UI reads financial data from SQLite when unlocked/refreshed. Theme preference and session token are browser state; they are not a financial backup. Avoid two processes writing the same database file. Flyway migrations run at startup.

## Swagger and API documentation

With `API_DOCS_ENABLED=true`, this workstation serves [Swagger UI](http://127.0.0.1:18080/swagger-ui/index.html), [OpenAPI JSON](http://127.0.0.1:18080/v3/api-docs), and [OpenAPI YAML](http://127.0.0.1:18080/v3/api-docs.yaml). Substitute the actual backend origin for other deployments. [The API guide](api.md#swagger-and-openapi-urls) documents every entry/configuration/helper URL and authorization steps.

Documentation is readable without the token while enabled. API execution still requires the workspace bearer token and uses the same database as the UI. Set `API_DOCS_ENABLED=false` and recreate/restart the backend to return 404 for documentation endpoints while retaining the authenticated API. Root `.env` changes require Compose recreation; source changes also require rebuilding. Native Java requires exported settings rather than automatically loading `.env`.

## Consistent backups

With Python 3 installed:

```powershell
./scripts/backup.ps1 -Database ./data/consulting.db -Destination ./backups
```

Or provide a new target filename directly:

```bash
python scripts/backup.py data/consulting.db backups/consulting-before-upgrade.db
```

The script uses SQLite's online backup API, refuses overwrites, and runs an integrity check. It works with the local Docker bind mount as well as native installations. Store encrypted copies outside this machine and test restores periodically. Choose a frequency appropriate to your recovery needs; this release does not install an automatic backup schedule.

For a clean offline copy, stop the application first. Do not copy a live SQLite database with an ordinary file operation. If a database was not cleanly stopped, journal/WAL files may matter; prefer the online backup API or a verified clean stop.

## Restore procedure

1. Stop all application processes that access the target database.
2. Create a safety backup of the current database. Preserve the current release/configuration.
3. Validate the selected backup using Python `sqlite3` and `PRAGMA integrity_check`; the result must be `ok`.
4. Replace `data/consulting.db` (or the configured local file) with the verified backup. Keep the old safety copy separate.
5. Start the matching/compatible application release. Check logs and migration status.
6. Verify clients, source work, invoices, balances, payment events, and audit history in the UI.

On Linux Docker hosts, ensure the bind directory/file is writable by UID/GID 10001. Ownership repair, if required, is explicit:

```bash
docker compose run --rm --user root --cap-add CHOWN --entrypoint chown app -R 10001:10001 /app/data
```

Use this only for the intended application data mount. Windows Docker Desktop normally handles host bind permissions. Do not erase a database to resolve a startup or migration error.

## Upgrade and rollback

Back up before upgrading. Build and test, then run `docker compose up --build -d`. Check authenticated workspace access, migration validation, and the important balances. Container recreation preserves the host bind mount. Migrations add schema changes; existing issued snapshots remain historical records.

A rollback of application binaries does not roll back a schema migration. Use a compatible prior release or restore a verified pre-upgrade backup with its matching release. Retain the current data safely before attempting recovery.

The earlier Docker named-volume database was cleanly stopped, backed up, and migrated to the local bind mount. The original volume and timestamped backups were retained. Do not treat the old volume as the active data source after migration.

## Common problems

For long narratives that hit the task limit, use Automatic/Narrative notes for safe preservation or configure a language provider to organize the prose. The action budget includes invoice/report steps. See [narrative intake](work-assistant.md#free-form-narrative-intake).

For a proposed work allocation table, use Automatic processing and the documented **Period / Work description / Hours** columns. This local path does not need a provider connection. Review proposed/planned totals, confirm actual daily work, resolve billing questions, and enable invoice preparation separately. Saving drafts is possible with invoice preparation excluded. Future dates cannot be confirmed relative to the plan's reference date; resubmit work in a new plan after it has been performed.

If an external provider returns HTTP 429, read the classification: quota/billing failures require account credits or a limit change; temporary throttling requires waiting or reducing request size/frequency. Unknown 429 errors remain explicitly ambiguous. Changing models or repeatedly retrying does not restore exhausted API credits. Provider error bodies are size/time bounded, and only recognized identifiers affect the public message.

| Symptom                                 | Checks                                                                                                                                                                           |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Startup rejects configuration           | Supply a token of at least 24 characters; inspect the named invalid setting/limit in logs.                                                                                       |
| Port unavailable                        | Change root `.env` `APP_PORT`, then recreate Compose. On this workstation port 18080 is verified.                                                                                |
| UI unlock fails                         | Confirm the token belongs to the running backend instance; avoid copying quotes/whitespace.                                                                                      |
| Swagger UI or specification returns 404 | Check `API_DOCS_ENABLED`, backend host/port, and proxy routes. Recreate/restart after changing the documentation flag. See the [URL reference](api.md#swagger-and-openapi-urls). |
| Read-only database                      | Check the host mount path, file permissions, and whether another process is using a different database.                                                                          |
| SQLite native-library failure           | Use the provided glibc runtime and executable `/tmp` mount.                                                                                                                      |
| Missing expected records                | Confirm `DATA_DIR`/`DB_PATH` and working directory before creating any new data. Native Maven development can otherwise use a different relative path.                           |
| Origin rejected in a split deployment   | Configure the exact frontend origin and restart the backend. CORS does not replace bearer authentication.                                                                        |
| Provider unavailable                    | Check enabled profile, installed/model name, endpoint, backend credentials, JSON request, and response pointer. Local rules still work without a provider.                       |
| Invoice cannot finalize                 | Resolve the displayed actual-work, weekly review, expense approval, or adjustment warnings. A shortfall does not authorize charges.                                              |

## Deployment boundary

Current deployment is single-user and local. A remotely accessible backend needs HTTPS, suitable identity/access controls, private configuration, and backup/recovery arrangements. GitHub Pages serves only static frontend assets; it cannot host this Java/SQLite backend or access your local disk on its own. Binary attachment upload, automated mail delivery, credit/refund accounting, multi-user roles, and a selectable PostgreSQL backend are outside this release.
