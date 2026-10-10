# CLAUDE.md — Ikimina SaaS

The authoritative blueprint is **`IKIMINA_SaaS_Master_Specification.md`** (the "spec").
Where the spec and any other document or existing code disagree, **the spec wins**.
The original `Ikimina_Project_Documentation.md` is referenced by the spec but is **not
present in this repo**; treat it as unavailable.

## Current phase

**Phase 3 — Loans** (spec §23, §9): **3a backend**, then **3b frontend**, then **3c withdrawals**.
Each PR targets `main` directly (no stacking). Phases 0–2 are merged and signed off.

Phase 3 acceptance: golden schedule tests · every illegal transition rejected · self-approval
blocked · double-disburse blocked under concurrency · overdue job idempotent with a fake clock.

Owner decisions so far: new backend in `backend/`, base package `rw.ikimina`, Spring Boot 4.1.x,
strict migration check with no override (ADR 0001); Phase 1: no national ID in MVP (no
`member_profiles` yet), platform admin created by the **dev seeder only** (production bootstrap
undecided), fake SMS provider only, SecLists top-10k password list. Phase 2: buckets managed by
SETTINGS_EDIT and changes to their money terms need a **second officer**; obligations anchored at
the bucket start date, **due on the period's last day**; overpayments carry to the next obligation;
reversals are **two-step** (CONTRIBUTION_RECORD asks, a different LOAN_APPROVE holder approves);
**withdrawals are Phase 3c, exit settlement Phase 4** (it needs fines). Phase 3: the spec 9.3
Secretary-substitution rule as written; schedules MONTHLY and AT_MATURITY only (weekly needs owner
input); interest recognised WHEN_PAID only; overpaying a loan is refused; the fines part of a
repayment is 0 until Phase 4; one open loan per member unless the product allows more; the approver
cannot record the disbursement of a **single-approval** loan when ≥3 officers are active (dual approvals
already have an independent second signer), so in that case the **President** gives the single approval (the
Secretary if the President borrows) - the Treasurer records the payout; money a borrower records on their **own**
loan is allowed but **flagged** (loan page + audit); the savings multiple counts every fund **except the social
fund**; a repayment can be reversed two-step like a contribution (disbursements cannot - cancel before
payout instead); loan-product money terms need a second officer.

## Ledger rules (Phase 2)

- Money moves only through `Ledger.post(JournalRequest)` (public API in `rw.ikimina.ledger`). Nothing
  outside the ledger module names a `ledger_*` table (`LedgerWriteAccessTest`).
- Every money endpoint takes an `Idempotency-Key`; derive the journal key from it and pass a request
  hash, so a retry replays and a changed retry is refused (H7).
- Corrections are reversals (`Ledger.reverse`), never edits. Modules react to `JournalReversed`
  (an in-transaction event) to update their own records - see `ContributionReversalListener`.
- Balances are a projection; `LedgerReconciler` recomputes them from lines nightly.
- Only journal types whose module undoes its own records on `JournalReversed` are reversible
  (`ReversalService.REVERSIBLE`: contributions, loan repayments). Add a type there only with its listener.

## Loan rules (Phase 3)

- A loan's status changes only through `Loan.apply(action)`, which asks `LoanStateMachine` — never set it directly.
- Who may approve is `ApprovalPolicy` (pure, unit-tested); the approve/reject endpoints are `AnyMember` because the
  Secretary's substitute approval is not a matrix permission. The borrower check compares memberships, not roles.
- Money endpoints (disburse, repay) store `idempotency_key` + `request_hash` on their own row and look it up *before*
  any rule the first attempt may have made false ("already disbursed", "more than is owed").
- A loan keeps a copy of its product's money terms from request time; schedules come from that copy.

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
| `GroupRouteCoverageIT` | `src/test/.../groups` | every `/groups/{groupId}` route is in `GroupRoutes` and declares its access |
| `TenantIsolationIT` | same | spec 5.6: every role of group A, on every group route, cannot read/modify group B |
| `RoleMatrixIT` + `PermissionMatrixTest` | same | HTTP behaviour and code matrix both equal spec 5.5 (`SpecPermissionMatrix`, hand-copied) |
| `RowLevelSecurityIT` | same | RLS confines the app role to the tenant, with no app code involved |
| `AuditChainIT` | `src/test/.../audit` | chain intact under concurrency; edits, deletions and tail cuts detected |
| `RateLimitIT` | `src/test/.../identity` | OTP and login limits return 429 + Retry-After |
| `LoanScheduleCalculatorTest` + `src/test/resources/loans/golden/*.csv` | `src/test/.../loans/internal` | spec 9.5 schedules match hand-worked golden files; principal never drifts |
| `LoanStateMachineTest` | same | every (status × action) pair behaves as spec 9.1 says; illegal transitions refused |
| `ApprovalPolicyTest` + `LoanFlowIT` | same | spec 9.3 maker-checker: who may approve, Secretary substitution, borrower never approves |
| `ConcurrentLoanMoneyIT` | `src/test/.../loans` | a loan is disbursed once under 20 simultaneous attempts; parallel repayments each count once |

## Database conventions

- Schema `ikimina`. Role `ikimina_owner` owns everything and runs Flyway; the app connects as
  `ikimina_app` (no superuser, no BYPASSRLS, owns nothing). Roles come from
  `backend/docker/postgres/sql/roles.sql`, shared by docker-compose and the tests.
- Every migration that creates a table **grants `${appRole}` exactly the privileges it needs**
  (append-only tables: `SELECT, INSERT` only). Attach `forbid_mutation()` (V1) to append-only tables.
- Money columns `NUMERIC(19,2)`; write them via `Money.toPostingAmount()` (ledger) or `Money.toStorageAmount()`.
- **Every new tenant table** (V5 is the pattern): `group_id NOT NULL`, `ENABLE` + `FORCE ROW LEVEL
  SECURITY`, an `owner_all` policy `TO CURRENT_USER`, and tenant policies `TO ${appRole}` on
  `app_current_group_id()`. Add it to `RowLevelSecurityIT.TENANT_TABLES`.
- Tenant context reaches PostgreSQL through `TenantAwareTransactionManager` (`set_config(..., true)`
  per transaction). Never set `app.*` settings by hand; inside a transaction use `TenantSession.enterGroup`.
- Audit rows are hashed and chained by the `audit_chain_link` trigger, ordered by `chain_seq`
  (never by id - ids are drawn before the chain lock). Never change `audit_canonical()`.

## Group endpoints - checklist for every new one

1. Path `/api/v1/groups/{groupId}/...` with the variable named exactly `groupId` (the membership guard keys on it).
2. Exactly one access declaration: `@PreAuthorize("@perm.has(#groupId, 'PERMISSION')")`, or
   `@GroupAccess.AnyMember` (+ an object rule in the service), or `@GroupAccess.NonMemberAllowed`.
3. Add it to `support/GroupRoutes.ALL` with the spec permission and a valid body - `GroupRouteCoverageIT`
   fails otherwise, and the isolation and role-matrix tests then cover it automatically.
4. Repository queries take `groupId` explicitly (H4) even though RLS also filters.
5. Audit the state change in the same transaction; add i18n keys for any new error code (EN + RW).
6. Sensitive actions (spec 16.1 list) need `@RequiresRecentAuthentication` or `StepUp.require()`.

## Conventions in code

- Errors: throw `ApiException(ErrorCode, args...)`; `GlobalExceptionHandler` renders RFC 7807 with
  `code`, localised `title`/`detail`, `requestId`. New codes need `error.<code>.title|detail` in both bundles.
- Money crosses JSON as a string; JSON numbers are rejected.
- Kinyarwanda values are `[rw-todo]` placeholders until the owner supplies reviewed copy. Never machine-translate.
- Security is deny-by-default (`SecurityConfig`). Access tokens are bearer headers (no CSRF exposure);
  the refresh token is an HttpOnly/SameSite=Strict cookie scoped to `/api/v1/auth`, and the endpoints
  reading it require the `X-Ikimina-Csrf` header (spec §16.2).
- Auth failures that must be recorded (wrong password, OTP attempt) return an outcome and let the
  transaction commit; the controller then reports the error. Never let them roll back the counter.
- Never reveal whether a phone has an account (register, login, forgot-password all look identical).
- SMS: `SmsNotifier.send(...)` with a `sms.<key>` template; delivered after commit. Never log SMS text
  outside the dev fake provider (it may hold a code).
- Rate limits: `RateLimiter.consume(limit, subject)`; counts in its own transaction.

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

## Frontend (`frontend/`)

React 19 + TypeScript 6 + Vite 8, Tailwind 4, TanStack Query, React Hook Form + Zod, i18next, React Router.
Versions are pinned exactly; TypeScript stays below 6.1 because typescript-eslint does not support 7 yet.

- **All text is i18n-keyed** (H10): `src/i18n/locales/en.json` is the source; `rw.json` holds `[rw-todo]`
  placeholders until the owner supplies reviewed Kinyarwanda. `i18n.test.ts` fails on missing keys or
  on a `t('...')` key that does not exist. Server errors are shown as the server's own localised text.
- **Money is never a JS number** (H1): amounts stay decimal strings (`lib/money.ts`); `parseFloat` is
  banned by ESLint.
- **Tokens** (`src/styles.css` `@theme`): `tokens.test.ts` enforces WCAG AA for every text/background pair.
  Brand amber is background-only (it fails AA as text).
- **Session**: access token in memory only; refresh via the HttpOnly cookie (`api/client.ts`). Sensitive
  actions go through `useSession().withStepUp(...)`, which handles REAUTHENTICATION_REQUIRED.
- Role-aware UI hides what the server would refuse anyway; the server stays the authority.

```sh
cd frontend
npm ci
npm run dev              # http://localhost:5173, /api proxied to :8082 (IKIMINA_API_URL to change)
npm run lint && npm run typecheck && npm test && npm run build
npm run test:e2e         # Playwright smoke against a running stack - see e2e/README.md
```
