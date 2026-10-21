# Verification

Validated on Windows on October 19, 2026, using Maven 3.9.9 with Java 21 and Node 22.23.2.

## Automated checks

- Backend: 16 passing JUnit/Mockito tests (13 domain/integration tests against migrated SQLite and 3 HTTP/authentication tests).
- Frontend: 5 passing Vitest/React Testing Library tests; strict TypeScript compilation and production Vite build pass. A late authorized network response cannot reopen a locked workspace.
- Headless Chrome: full workflow passed against disposable SQLite databases in both native Java and the final Docker runtime. Covered token login, seven incomplete seed activities, manual actual work, weekly review, draft creation, explicit finalization, PDF/XLSX/CSV exports, edited email and CC copying, actual submission date, approval, partial payment, historical-payment isolation, dark theme and mobile layout. No browser JavaScript errors or document-level horizontal overflow.
- SQLite online backup: generated a backup from the isolated test database and passed `PRAGMA integrity_check`.
- Docker Compose: image built, backend tests passed during the Linux image build, and the application started successfully. The live deployment at `http://127.0.0.1:18080` passed authenticated API, anonymous-access rejection, seed integrity and OpenAPI checks. Port 8080 was blocked by Windows, so the ignored local `.env` selects port 18080. The runtime uses Ubuntu Temurin Java 21 and an executable temporary filesystem for SQLite JDBC's extracted native library. The application root filesystem remains read-only and its service runs as UID 10001 with all capabilities dropped.
- npm dependency audit: zero advisories after test-dependency updates, including zero in production dependencies. This is not a claim that every dependency is free of undiscovered vulnerabilities.

## Financial edge cases

Incomplete seed activities cannot be billed. Overlaps and duplicate work are rejected. Weeks crossing a month boundary include all dated work for compliance, while invoice contents stay inside the service period. Partial contract weeks require clarification. Unapproved overtime blocks finalization. Availability/exception acknowledgements never create invoice charges. Adjustments require separate documentation and approval. Unapproved expenses block finalization and may be corrected before allocation. Issued entries and expenses remain locked while allocated to a live invoice. Explicit confirmed withdrawal of an unpaid invoice preserves its snapshot and releases source records for audited correction. Existing allocations prevent double billing. Linked replacement invoices preserve original snapshots and identify the withdrawn invoice in their exports. Snapshot monetary values deserialize as BigDecimal, preserving exact decimal arithmetic. Payments cannot exceed balances; overdue and paid states derive from submission dates and payment events. Long abandoned timers can be stopped without creating false confirmed durations.

## Visual review

The zero-value seeded September draft PDF was rendered with Poppler and visually inspected. It contains no invented work. Desktop light/dark and 390px mobile layouts were inspected. `dashboard.png` shows only the initial, unconfirmed seed state. Temporary test work and payment fixtures remain solely in disposable databases under ignored `tmp/`.

## Scope and operating constraints

Single-user local deployment with a bearer token; no multi-user identity/roles. References/attachment URLs are supported, binary uploads are not. Email is manual. Credit notes/refunds and replacing paid invoices are not implemented. PostgreSQL is a documented migration path, not a switchable database. The application uses USD. PDF presentation currently uses PDF standard fonts and is designed for Latin/ASCII content; use the full Unicode CSV/XLSX export for text outside that font repertoire.

The requested Spring Boot 3.x line is pinned to 3.5.16, its [final open-source maintenance release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). Keeping a supported framework line will require a future Spring Boot 4 migration or commercial 3.x maintenance. No such migration is included because the requested stack specifies 3.x.
