# CLAUDE.md — Ikimina SaaS

The authoritative blueprint is **`IKIMINA_SaaS_Master_Specification.md`** (the "spec").
Where the spec and any other document or existing code disagree, **the spec wins**.
The original `Ikimina_Project_Documentation.md` is referenced by the spec but is **not
present in this repo**; treat it as unavailable.

## Current phase

**Phase 0 — Foundations** (spec §23). Status: *implemented and verified locally; awaiting the
first CI run on GitHub and the owner's sign-off.* Do not start Phase 1 until the owner confirms.

Phase 0 acceptance: CI green · empty app boots · migration safety check works ·
ArchUnit enforces the rules.

Decisions taken at Phase 0 approval (see `docs/adr/0001-legacy-code.md`):
new backend in `backend/`, base package `rw.ikimina`, Spring Boot 4.1.x, strict migration check
with no override, frontend starts in Phase 1.

## How to work (spec §0)

1. One phase at a time. Do not start Phase N+1 until Phase N's acceptance criteria pass
   **and the owner confirms**.
2. For every phase, show the plan first (files, migrations, tests, i18n keys, audit events,
   permissions, risks) and wait for approval.
3. Anything tagged **[OPEN]** or **[VERIFY]** is raised with the owner, never assumed.
4. **Never fabricate third-party integrations** (MTN MoMo, Airtel, SMS gateways, callback
   signing, legal/regulatory claims). Code against an interface + a `Fake…` implementation
   and flag what needs verification against the provider's official docs.
5. Report results against acceptance criteria item by item, with evidence. Never claim
   done on anything unverified.

## Hard Rules (every phase, no exceptions)

| # | Rule |
|---|------|
| H1 | Money is `java.math.BigDecimal` only — never `float`/`double` for amounts, rates or balances. Use the `Money` value type; no ad-hoc `setScale` elsewhere. |
| H2 | **No Lombok.** Explicit constructors, getters, `equals`/`hashCode`. Prefer records for immutable DTOs. |
| H3 | No destructive SQL in migrations: no `DROP TABLE`, `TRUNCATE`, or `DELETE` of financial data. Flyway is forward-only and additive. |
| H4 | Every tenant-owned table has `group_id NOT NULL`, and every query is scoped by it. |
| H5 | The ledger is append-only. No `UPDATE`/`DELETE` on journal rows; corrections are reversing journals. Only `LedgerService` writes ledger tables. |
| H6 | No secrets in the repo. Config via environment variables; commit only `.env.example`. |
| H7 | Idempotency on every money-moving or webhook endpoint (`Idempotency-Key`); a replay must not double-post. |
| H8 | Every state-changing action writes an audit-log row **in the same transaction**. |
| H9 | No single person can initiate and approve the same money movement. Maker-checker is enforced in code, not the UI. |
| H10 | All user-facing text is i18n-keyed (English + Kinyarwanda). No hard-coded strings in UI, errors, SMS, or PDFs. |

Further standing conventions from the spec:

- **Money (§4.3):** RWF only. Persist `NUMERIC(19,2)`. Every amount posted to the ledger is
  rounded to a **whole RWF** with `RoundingMode.HALF_UP` at the posting boundary.
  Intermediate maths at scale 6 with `MathContext.DECIMAL64`. API serialises money as strings.
- **Time (§4.3, §20.2):** instants are `TIMESTAMPTZ` (UTC); business dates are `DATE` in
  `Africa/Kigali`. Inject a `Clock` everywhere — no `LocalDate.now()` / `Instant.now()` in
  business code.
- **Keys (§6):** `BIGINT GENERATED ALWAYS AS IDENTITY`; public-facing ids are a separate
  `public_id UUID`. Sequential ids never appear in URLs.
- **Tenancy (§5):** shared schema + `group_id` + Postgres RLS. Group endpoints are
  path-scoped `/api/v1/groups/{groupId}/…`; non-members get **404**, not 403. The app DB role
  is not a superuser and does not own tables.
- **Errors (§17):** RFC 7807 `application/problem+json` with a stable `code` and i18n message.
- **Events (§4.1):** transactional outbox (`outbox_events`), no Kafka. Redis optional, never
  required for financial correctness.
- **Schedulers:** `@Scheduled` + ShedLock; idempotent, group-by-group, logged to `scheduler_runs`.

## Tech stack (spec §4.2)

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 (owner decision; spec said 3.x [VERIFY]). Jackson 3 (`tools.jackson.*`), Testcontainers 2 |
| Persistence | Spring Data JPA/Hibernate; `JdbcTemplate` or jOOQ for ledger/report queries |
| DB | PostgreSQL 16+ |
| Migrations | Flyway, forward-only, additive |
| Security | Spring Security, JWT access (15 min) + rotating hashed refresh tokens (30 days) |
| Scheduling | `@Scheduled` + ShedLock (JDBC) |
| API docs | springdoc-openapi (OpenAPI is the contract) |
| Reports | OpenPDF / Flying Saucer, Apache POI |
| Frontend | React + TypeScript + Vite, Tailwind, TanStack Query, React Hook Form + Zod, i18next |
| Tests | JUnit 5, Testcontainers (Postgres), ArchUnit, PIT, Playwright |
| Local infra | Docker + docker-compose (Postgres, optional Redis, MailHog, fake providers) |

## Module layout (spec §4.1) — modular monolith, package-by-module

```
backend/src/main/java/rw/ikimina
├── shared/         Money, Clock config, error model (RFC 7807), i18n, idempotency, web/security plumbing
├── identity/       users, auth, OTP, refresh tokens
├── groups/         groups, settings (bylaws), memberships, roles, invitations
├── ledger/         LedgerService.post(), accounts, journals, lines, balances, reconciliation
├── savings/        buckets, obligations, contributions, withdrawals
├── loans/          products, state machine, approvals, disbursement, schedule, repayments
├── fines/          rules, assessment, payment, waiver
├── meetings/       meetings, attendance, minutes, resolutions
├── billing/        plans, subscriptions, invoices, PlanGate, payments via PaymentProvider
├── notifications/  templates, outbox worker, SmsProvider
├── reports/        statements, summaries, exports
├── audit/          hash-chained audit log
└── platform/       platform-admin console, support-access grants
```

Each module's public API sits in its root package; implementation (entities, repositories)
goes in `<module>.internal`, reachable only from the same module. Modules never touch each
other's internals; `shared` depends on no module; no cycles between modules.

## Enforced by the build (do not work around these)

| Check | Where | Guards |
|---|---|---|
| maven-enforcer bans Lombok and H2 | `backend/pom.xml` | H2, real-Postgres tests |
| `ArchitectureTest` (rules in `ArchitectureRules`) | `src/test/.../architecture` | H1 no float/double anywhere, no `BigDecimal`↔double conversions; no wall-clock reads outside `ClockConfig`; no field injection; module encapsulation; no cycles; repositories in `.internal` |
| `ArchitectureRulesCanaryTest` | same | proves each rule fails on bad fixtures (`rw.ikimina.archfixtures`, test sources only) |
| `MigrationSafetyTest` | `src/test/.../migration` | H3: no DROP TABLE/COLUMN/SCHEMA/DATABASE, TRUNCATE, DELETE FROM, RENAME, column type change, nor disabling triggers/RLS. Versioned `V<n>__lower_snake.sql` only |
| `check-migrations-append-only.sh` (CI) | `backend/scripts/` | existing migrations are never edited, renamed or deleted |
| `I18nKeyParityTest` | `src/test/.../i18n` | H10: `messages.properties` (en) and `messages_rw.properties` have identical keys |
| gitleaks over full history (CI) | `.gitleaksignore` for reviewed exceptions | H6 |

## Database conventions

- Schema `ikimina`. Role `ikimina_owner` owns everything and runs Flyway; the app connects as
  `ikimina_app` (no superuser, no BYPASSRLS, owns nothing). Roles come from
  `backend/docker/postgres/sql/roles.sql`, shared by docker-compose and the tests.
- Every migration that creates a table **grants `${appRole}` exactly the privileges it needs**
  (append-only tables: `SELECT, INSERT` only). Attach `forbid_mutation()` (V1) to append-only tables.
- Money columns `NUMERIC(19,2)`; write them via `Money.toPostingAmount()` (ledger) or `Money.toStorageAmount()`.

## Conventions in code

- Errors: throw `ApiException(ErrorCode, args...)`; `GlobalExceptionHandler` renders RFC 7807 with
  `code`, localised `title`/`detail`, `requestId`. New codes need `error.<code>.title|detail` in both bundles.
- Money crosses JSON as a string; JSON numbers are rejected.
- Kinyarwanda values are `[rw-todo]` placeholders until the owner supplies reviewed copy. Never machine-translate.
- Security is deny-by-default (`SecurityConfig`). CSRF is off only because auth is bearer-header;
  revisit when the refresh token moves to a cookie (Phase 1, spec §16.2).

## Legacy code

`ikimina/` and `frontend-ikimina/` are **frozen** (ADR 0001): reference only, no new work, kept
green in CI (`legacy-*` jobs) until retired. Their migrations are outside the new safety check.

## Commands (run from `backend/`)

JDK 21 is required; on this machine set `JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"`
(the default `java` on PATH is 17). Docker must be running for integration tests.

```sh
./mvnw verify                                   # everything: enforcer, unit, ArchUnit, migration check, Testcontainers ITs
./mvnw test                                     # unit tests only (no Docker)
./mvnw test -Dtest=ArchitectureTest             # one test class
./mvnw -Pmutation test-compile pitest:mutationCoverage   # PIT (not a CI gate yet)
cp .env.example .env && docker compose up --build        # local stack (Postgres 16 + backend on :8082)
docker compose --profile tools up                        # + Mailpit and Redis
(cd .. && sh backend/scripts/check-migrations-append-only.sh origin/main)   # paths are repo-relative
```

`*Test` = unit (surefire), `*IT` = integration on PostgreSQL (failsafe). The OpenAPI document is
written to `backend/target/openapi/openapi.json` by `ApplicationBootIT`.
