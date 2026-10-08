# Ikimina SaaS Master Specification

**Version:** 1.0 (supersedes `Ikimina_Project_Documentation.md`)
**Audience:** Claude Code (primary), the product owner (secondary)
**Status:** Authoritative blueprint. Where this document and the old document disagree, this document wins.

> **Conventions used in this document**
> - **MUST / MUST NOT** = hard requirement. **SHOULD** = default unless you have a reason.
> - **[DECISION]** = a choice made in this spec; changing it has downstream effects.
> - **[VERIFY]** = a factual or legal claim that has NOT been confirmed and must be checked before it is relied on.
> - **[OPEN]** = a question only the product owner can answer. Claude Code MUST ask, not guess.

---

## Table of Contents
0. How Claude Code must use this document
1. Product vision, positioning and business model
2. Personas and roles
3. Scope: MVP, V2, V3
4. System architecture
5. Multi-tenancy and authorization
6. Data model (DDL)
7. Financial ledger
8. Savings engine
9. Loan engine
10. Fines and penalties engine
11. Meetings and governance
12. Subscription and billing (the SaaS layer)
13. Payments architecture
14. Notifications
15. Reporting and regulatory export
16. Security
17. API specification
18. Frontend and UX
19. Non-functional requirements
20. Testing strategy
21. Infrastructure, CI/CD, observability, backup
22. Seed and demo data
23. Implementation phases with acceptance criteria
24. Claude Code prompts
25. Open questions for the product owner
26. Changes from the original document

---

# 0. How Claude Code must use this document

1. **Read the whole document before writing code.** Then create `CLAUDE.md` in the repo root summarising Section 0, the Hard Rules below, and the phase you are on.
2. **Build one phase at a time** (Section 23). Do not start Phase N+1 until Phase N's acceptance criteria pass and the person confirms.
3. **Ask before guessing.** Anything tagged **[OPEN]** or **[VERIFY]** must be raised, not assumed.
4. **Show your plan first** for every phase: list files to create, migrations to add, and tests to write. Wait for approval.
5. **Never fabricate integrations.** If a third-party API detail (MoMo endpoints, SMS gateway, callback signing) is not documented in this spec or in the provider's official docs that you can read, write the code against an interface plus a fake implementation and flag it.

## Hard Rules (apply to every phase)

| # | Rule |
|---|------|
| H1 | **No floating point for money.** Use `java.math.BigDecimal` only. Never `float`/`double` for amounts, rates or balances. |
| H2 | **No Lombok.** Explicit constructors, getters, `equals`/`hashCode`. Prefer Java records for immutable DTOs. |
| H3 | **No destructive SQL in migrations.** No `DROP TABLE`, `TRUNCATE`, or `DELETE` of financial data in any Flyway migration. Schema changes are additive and backward-compatible. |
| H4 | **Every tenant-owned table has `group_id NOT NULL`** and every query is scoped by it. |
| H5 | **The ledger is append-only.** No `UPDATE` or `DELETE` on journal rows, ever. Corrections are reversing journals. |
| H6 | **No secrets in the repo.** Configuration via environment variables. Commit only `.env.example`. |
| H7 | **Idempotency on every money-moving or webhook endpoint.** Replaying a request must not double-post. |
| H8 | **Every state-changing action writes an audit log row** in the same transaction. |
| H9 | **No single person can initiate and approve the same money movement.** Maker-checker is enforced in code, not in the UI. |
| H10 | **All user-facing text is i18n keyed** (English + Kinyarwanda). No hard-coded strings in UI or SMS templates. |

---

# 1. Product vision, positioning and business model

## 1.1 Vision
**"The operating system for Rwanda's savings groups."** A multi-tenant SaaS where each *ikimina* (plural *ibimina*) runs its savings, loans, fines, meetings and reporting transparently, with every franc traceable.

## 1.2 Positioning (what we sell)
Do **not** sell "we record savings" (a notebook or Excel does that). Sell:
- **Transparency:** every member sees their own balance, loans and fines in real time.
- **Control:** no single officer can approve or pay out a loan alone.
- **Accountability:** every action is logged; history cannot be quietly edited.
- **Convenience:** meetings, contributions, reminders and statements run themselves.
- **Reporting:** one-click statements for members, officers and local authorities.

## 1.3 Pricing [DECISION]
Pricing lives in the database (`subscription_plans`), never hard-coded, so it can change without a release.

| Plan | Launch price (RWF/month) | Intended limits | Notes |
|------|--------------------------|-----------------|-------|
| Trial | 0 for 14 days | Full Advanced features | Auto-starts on group creation |
| Basic | 3,000 | Up to 30 members, 1 savings bucket set, SMS pay-as-you-go | Launch price |
| Advanced | 5,000 | Up to 100 members, unlimited buckets, meetings, reports, SMS bundle | Launch price |
| Standard / Professional / Organization | Defined later | Larger groups, federations, NGOs | Plan model MUST support adding these without code changes |

- Annual billing at a discount (e.g., 10 months for 12) SHOULD be supported by the plan model from day one even if the UI ships later.
- **Market note [VERIFY]:** at least one competing Rwanda platform reportedly charges 15,000–75,000 RWF/month. Treat 3,000/5,000 as **launch/introductory** prices and keep the model flexible. The product owner should validate willingness-to-pay with 10+ real groups before fixing long-term prices.
- Additional future revenue (model must not block it): SMS bundles, premium reports, custom branding, federation/organization accounts, API access.

## 1.4 Success metrics
Activated groups (recorded ≥1 contribution), trial→paid conversion, monthly churn, paying groups, SMS delivery rate, median time to record a meeting, support tickets per 100 groups.

---

# 2. Personas and roles

## 2.1 Platform level
- **PLATFORM_ADMIN** (formerly `ROLE_SUPER_ADMIN`): product owner/staff. Manages plans, subscriptions, suspensions, support, platform health. **MUST NOT** have routine read access to a group's member-level financial data; any such access (for support) requires a logged, time-boxed "support access grant" the group admin consents to. [DECISION]

## 2.2 Group level (stored on `group_memberships.role`)
| Role | Capabilities |
|------|--------------|
| **PRESIDENT** | Approves loans, closes meetings, edits bylaws (with secretary/treasurer countersign for financial parameters), manages members |
| **TREASURER** | Records contributions/repayments/expenses, approves loans, disburses, reconciles |
| **SECRETARY** | Manages meetings, attendance, minutes, member records, communications |
| **AUDITOR / CENSOR** (optional) | Read-only access to all group finance + audit log. Common in ibimina; cheap to add |
| **MEMBER** | Own data only: balances, loans, fines, statements; request loans; vote; view group summary at the level the bylaws allow |

- A user can belong to **multiple groups** with a different role in each. [DECISION]
- Role slots (President, Treasurer, Secretary) are **unique per group among ACTIVE members**; a transfer-of-office flow is audited.

## 2.3 Key user stories (abbreviated)
- *President:* "Before the meeting I see expected vs collected money and pending loans on one screen."
- *Treasurer:* "I record who paid, with the MoMo reference, in under 2 minutes for 40 members."
- *Member:* "I check my balance and loan on my phone and receive an SMS when anything changes."
- *Platform owner:* "I see which groups are trialing, paying, past due, or churned."

---

# 3. Scope

## 3.1 MVP (V1) — a group can run end to end and pay us
1. Auth (phone + password, SMS OTP verification, password reset), profiles
2. Group creation, bylaws/settings, member invitations and management, roles
3. Savings: buckets, contribution schedules, manual contribution recording, member balances
4. Immutable double-entry ledger underpinning everything
5. Loans: products, request, maker-checker approval, disbursement recording, repayment schedule, repayments
6. Fines: automatic overdue-loan fines, missed-contribution fines, manual fines, fine payment
7. Meetings: create, notify, attendance, in-meeting contributions/repayments/fines, decisions, minutes, meeting report
8. Subscriptions: trial, Basic/Advanced, invoices, MoMo payment of subscription, grace period, suspension, reactivation
9. Notifications: SMS + in-app (+ email optional), EN/RW templates
10. Reports: member statement, group financial summary, loan report, contribution report; PDF and Excel export
11. Audit log, RBAC, tenant isolation, rate limiting, backups
12. Platform admin console (groups, subscriptions, support, health)

## 3.2 V1.1 — closing the loop on ibimina reality
- **Cycle close and share-out** (end-of-cycle distribution of savings plus a share of interest/fine income) — central to how ibimina work; schedule right after MVP.
- Excel/CSV bulk import of existing members and historical balances (essential for onboarding groups that already have years of records).

## 3.3 V2 — competitive advantage (after paying groups exist)
- In-app MoMo **member contributions and loan disbursement** via a licensed provider — **only after** legal sign-off (see 13.4)
- Airtel Money provider, USSD (`*XXX#`), WhatsApp notifications
- Automated reminders (contributions, repayments), advanced analytics, member trust score
- Dividend/share management, group expenses/income workflows, digital voting, digital signatures
- Progressive Web App with offline read access

## 3.4 V3 — infrastructure
Federations / cooperatives / NGOs: organization accounts that roll up many groups; API access; white-label branding; institution partnerships.

## 3.5 Explicitly out of scope for MVP
USSD, WhatsApp, automated MoMo payouts of loans, in-app contribution collection, direct government-portal synchronisation, multi-currency, native mobile apps.

---

# 4. System architecture

## 4.1 Architecture decision [DECISION]
The original document proposed microservices with Kafka and a Redis cluster. For a solo-built product charging a few thousand RWF per group, that is operationally expensive and adds failure modes without benefit. **Start as a modular monolith** and make the boundaries explicit so modules can be extracted later.

```
┌─────────────┐   HTTPS    ┌───────────────────────────────────────────┐
│ React SPA   │──────────▶│ Spring Boot modular monolith              │
│ (PWA-ready) │            │  ┌────────┐ ┌────────┐ ┌─────────────┐    │
└─────────────┘            │  │identity│ │ groups │ │  ledger     │    │
┌─────────────┐  webhook   │  ├────────┤ ├────────┤ ├─────────────┤    │
│ MoMo / SMS  │──────────▶│  │savings │ │ loans  │ │ fines       │    │
│ providers   │◀──────────│  ├────────┤ ├────────┤ ├─────────────┤    │
└─────────────┘            │  │meetings│ │billing │ │ notifications│   │
                           │  ├────────┤ ├────────┤ ├─────────────┤    │
                           │  │reports │ │ audit  │ │ platform    │    │
                           │  └────────┘ └────────┘ └─────────────┘    │
                           └───────────┬───────────────────────────────┘
                                       │
                          ┌────────────▼───────────┐   ┌──────────────┐
                          │ PostgreSQL (primary)   │   │ Redis (opt.) │
                          │ + outbox table         │   │ rate limits, │
                          └────────────────────────┘   │ OTP, cache   │
                                                       └──────────────┘
```

- **Domain events:** use the **transactional outbox pattern** (an `outbox_events` table written in the same DB transaction, drained by a scheduled publisher). Consumers (notifications, audit fan-out) read the outbox. This gives Kafka-like reliability with zero extra infrastructure. If scale later demands it, the publisher can forward to Kafka without changing producers.
- **Redis** is optional in MVP: use it for rate-limit counters and OTP storage if available; otherwise use Postgres tables with TTL cleanup. Do not make Redis a hard dependency for core financial correctness.
- **Package-by-module** (not by layer). Modules communicate through public service interfaces and events, never by reaching into each other's repositories. Enforce with **ArchUnit** tests.

## 4.2 Technology stack [DECISION]
| Layer | Choice | Notes |
|-------|--------|-------|
| Language | Java 21 (LTS) | Original said 17; 21 is fine and current LTS |
| Framework | Spring Boot latest stable 3.x GA at project start | **[VERIFY]** version at start. The original pinned 3.2.3, which is no longer a supported line |
| Persistence | Spring Data JPA + Hibernate; **jOOQ or `JdbcTemplate` for ledger/report queries** | Use plain SQL where aggregation matters |
| DB | **PostgreSQL 16+** | Original said 12+, which is end-of-life |
| Migrations | **Flyway**, versioned, forward-only | See H3 |
| Security | Spring Security 6, JWT access + rotating refresh tokens | |
| Scheduling | Spring `@Scheduled` + **ShedLock** (JDBC lock) | Prevents double-run when >1 instance |
| Docs | springdoc-openapi | OpenAPI is the API contract |
| PDF/Excel | OpenPDF or Flying Saucer (PDF), Apache POI (Excel) | |
| Frontend | React + TypeScript + Vite, Tailwind, TanStack Query, React Hook Form + Zod, i18next | Mobile-first |
| Tests | JUnit 5, Testcontainers (Postgres), ArchUnit, Playwright (E2E) | |
| Containers | Docker + docker-compose for local | |

## 4.3 Time, locale, currency [DECISION]
- Store instants as `TIMESTAMPTZ` (UTC). Business dates (due dates, meeting dates, contribution periods) are `DATE` interpreted in **Africa/Kigali (UTC+2, no DST)**.
- Currency: **RWF only**. RWF has no minor unit in practice. Persist as `NUMERIC(19,2)`; **every amount posted to the ledger MUST be rounded to a whole RWF** using `RoundingMode.HALF_UP` at the posting boundary. Intermediate calculations use scale 6 and a `MathContext` of `DECIMAL64`. Implement one `Money` value type enforcing this; no ad-hoc `setScale` calls elsewhere.
- Locales: `en` and `rw` (Kinyarwanda). Default per user preference, fallback `en`.

---

# 5. Multi-tenancy and authorization

## 5.1 Tenancy model [DECISION]
**Shared database, shared schema, `group_id` discriminator on every tenant-owned row**, plus Postgres **Row-Level Security (RLS)** as defence-in-depth.

## 5.2 Request flow
1. JWT authenticates the **user** (not the group). It carries `sub` (user id) and platform role only.
2. Group-scoped endpoints are **path-scoped**: `/api/v1/groups/{groupId}/...`.
3. A `GroupAccessGuard` resolves `(userId, groupId) → active membership + role` and rejects with **404** (not 403) if the user is not a member, to avoid leaking group existence.
4. A `TenantContext` (request-scoped) holds `groupId` and `membershipId`.
5. On each transaction, run `SET LOCAL app.current_group_id = '<id>'` (via a Hibernate/JDBC interceptor) so RLS policies apply.

## 5.3 RLS example
```sql
ALTER TABLE loans ENABLE ROW LEVEL SECURITY;
ALTER TABLE loans FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON loans
  USING (group_id = NULLIF(current_setting('app.current_group_id', true), '')::BIGINT);
```
- The application DB role MUST NOT be a superuser and MUST NOT own the tables (owners bypass RLS unless `FORCE`).
- Platform jobs that legitimately span tenants (billing, overdue scan) use a **separate DB role/connection** with explicit, narrowly scoped policies, and they iterate group by group.

## 5.4 Authorization layers (all required)
1. **Authentication** (valid JWT).
2. **Membership** (active member of `groupId`).
3. **Role/permission** (`@PreAuthorize("@perm.has(#groupId, 'LOAN_APPROVE')")`). Define a permission matrix in code and a test that asserts it.
4. **Object ownership** (member can only read own member-level data unless role grants more).
5. **Subscription gate** (group must be ACTIVE/TRIAL/PAST_DUE/GRACE for writes; SUSPENDED = read-only; see Section 12).
6. **Database RLS** (last line of defence).

## 5.5 Permission matrix (MVP)
| Permission | President | Treasurer | Secretary | Auditor | Member |
|---|:-:|:-:|:-:|:-:|:-:|
| MEMBER_MANAGE | ✔ | | ✔ | | |
| SETTINGS_EDIT (financial params need 2 officers) | ✔ | ✔ | ✔ | | |
| CONTRIBUTION_RECORD | | ✔ | | | |
| REPAYMENT_RECORD | | ✔ | | | |
| LOAN_REQUEST | ✔ | ✔ | ✔ | | ✔ |
| LOAN_APPROVE | ✔ | ✔ | (see 9.3) | | |
| LOAN_DISBURSE | | ✔ | | | |
| FINE_ISSUE / WAIVE | ✔ | ✔ | ✔ | | |
| EXPENSE_RECORD | | ✔ | | | |
| MEETING_MANAGE | ✔ | | ✔ | | |
| REPORT_VIEW_GROUP | ✔ | ✔ | ✔ | ✔ | summary only |
| AUDIT_LOG_VIEW | ✔ | ✔ | | ✔ | |
| OWN_DATA_VIEW | ✔ | ✔ | ✔ | ✔ | ✔ |

## 5.6 Mandatory tenant-isolation tests
For every endpoint under `/groups/{groupId}/...`: a test that authenticates as a member of Group A and tries to read/modify a resource ID belonging to Group B (using Group A's path *and* Group B's path). Both MUST return 404/403 and never leak data. Make this a reusable test harness so new endpoints are covered automatically (e.g., scan registered routes and fail CI if a route lacks an isolation test).

---

# 6. Data model (DDL)

> All migrations are **Flyway, forward-only, additive** (H3). The DDL below defines the target schema; split it across versioned migrations (`V1__identity.sql`, `V2__groups.sql`, …). Use `BIGINT GENERATED ALWAYS AS IDENTITY` for keys. Public-facing identifiers SHOULD be a separate `public_id UUID` so sequential ids are never exposed in URLs.

## 6.1 Identity and groups
```sql
CREATE TABLE users (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  phone         VARCHAR(20) NOT NULL UNIQUE,         -- E.164, e.g. +2507XXXXXXXX
  phone_verified_at TIMESTAMPTZ,
  email         VARCHAR(255) UNIQUE,
  password_hash VARCHAR(255) NOT NULL,               -- Argon2id or bcrypt(12+)
  full_name     VARCHAR(200) NOT NULL,
  locale        VARCHAR(5) NOT NULL DEFAULT 'rw',
  platform_role VARCHAR(30) NOT NULL DEFAULT 'USER', -- USER | PLATFORM_ADMIN
  status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  failed_logins INT NOT NULL DEFAULT 0,
  locked_until  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE refresh_tokens (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  family_id UUID NOT NULL,                 -- rotation family for reuse detection
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE groups (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  name          VARCHAR(200) NOT NULL,
  registration_number VARCHAR(100),          -- sector registration, optional
  phone         VARCHAR(20),
  email         VARCHAR(255),
  province VARCHAR(80), district VARCHAR(80), sector VARCHAR(80), cell VARCHAR(80), village VARCHAR(80),
  status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ARCHIVED
  created_by    BIGINT NOT NULL REFERENCES users(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE group_settings (            -- the group's "bylaws" in structured form
  group_id BIGINT PRIMARY KEY REFERENCES groups(id),
  settings JSONB NOT NULL,                -- validated against a versioned JSON schema
  schema_version INT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE group_memberships (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id   BIGINT NOT NULL REFERENCES groups(id),
  user_id    BIGINT NOT NULL REFERENCES users(id),
  member_number VARCHAR(30),
  role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',  -- PRESIDENT|TREASURER|SECRETARY|AUDITOR|MEMBER
  status     VARCHAR(20) NOT NULL DEFAULT 'INVITED', -- INVITED|ACTIVE|SUSPENDED|LEFT|REMOVED
  joined_at  TIMESTAMPTZ, left_at TIMESTAMPTZ, left_reason TEXT,
  UNIQUE (group_id, user_id)
);
-- one active holder per officer role per group
CREATE UNIQUE INDEX uq_active_officer ON group_memberships(group_id, role)
  WHERE status = 'ACTIVE' AND role IN ('PRESIDENT','TREASURER','SECRETARY');

CREATE TABLE member_profiles (           -- sensitive; encrypt national_id at application level
  membership_id BIGINT PRIMARY KEY REFERENCES group_memberships(id),
  group_id BIGINT NOT NULL REFERENCES groups(id),
  national_id_enc BYTEA, date_of_birth DATE, address TEXT,
  next_of_kin_name VARCHAR(200), next_of_kin_phone VARCHAR(20), next_of_kin_relation VARCHAR(60)
);

CREATE TABLE group_invitations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  phone VARCHAR(20) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  token_hash VARCHAR(128) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ, created_by BIGINT NOT NULL REFERENCES users(id)
);
```

## 6.2 Ledger (see Section 7 for semantics)
```sql
CREATE TABLE ledger_accounts (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  account_type VARCHAR(40) NOT NULL,
    -- GROUP_CASH | MEMBER_SAVINGS | LOAN_RECEIVABLE | INTEREST_INCOME | FINE_INCOME
    -- | FINE_RECEIVABLE | EXPENSE | SOCIAL_FUND | SHARE_OUT_PAYABLE | EQUITY ...
  membership_id BIGINT REFERENCES group_memberships(id),
  bucket_id BIGINT,                 -- FK added after savings_buckets exists
  loan_id BIGINT,                   -- FK added after loans exists
  normal_side VARCHAR(6) NOT NULL CHECK (normal_side IN ('DEBIT','CREDIT')),
  currency CHAR(3) NOT NULL DEFAULT 'RWF',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_ledger_account_dims ON ledger_accounts
  (group_id, account_type, COALESCE(membership_id,0), COALESCE(bucket_id,0), COALESCE(loan_id,0));

CREATE TABLE ledger_journals (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  journal_type VARCHAR(40) NOT NULL,
    -- CONTRIBUTION | WITHDRAWAL | LOAN_DISBURSEMENT | LOAN_REPAYMENT | INTEREST_ACCRUAL
    -- | FINE_ASSESSED | FINE_PAID | FINE_WAIVED | EXPENSE | INCOME | SHARE_OUT | REVERSAL | ADJUSTMENT
  idempotency_key VARCHAR(100) NOT NULL,
  business_date DATE NOT NULL,
  description TEXT,
  source VARCHAR(30) NOT NULL,           -- MANUAL | MEETING | MOMO | SYSTEM
  external_ref VARCHAR(100),             -- MoMo transaction id, receipt no.
  meeting_id BIGINT, loan_id BIGINT, member_id BIGINT,
  reverses_journal_id BIGINT REFERENCES ledger_journals(id),
  created_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (group_id, idempotency_key)
);

CREATE TABLE ledger_lines (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  journal_id BIGINT NOT NULL REFERENCES ledger_journals(id),
  group_id BIGINT NOT NULL REFERENCES groups(id),
  account_id BIGINT NOT NULL REFERENCES ledger_accounts(id),
  direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0)
);
CREATE INDEX idx_lines_account ON ledger_lines(account_id);
CREATE INDEX idx_lines_journal ON ledger_lines(journal_id);

-- Derived projection for fast reads; always reconcilable from ledger_lines.
CREATE TABLE ledger_balances (
  account_id BIGINT PRIMARY KEY REFERENCES ledger_accounts(id),
  group_id BIGINT NOT NULL REFERENCES groups(id),
  balance NUMERIC(19,2) NOT NULL DEFAULT 0,   -- signed per account normal_side
  last_journal_id BIGINT,
  version BIGINT NOT NULL DEFAULT 0
);

-- Immutability + balance enforcement (see Section 7.3)
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'ledger rows are immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_journals_immutable BEFORE UPDATE OR DELETE ON ledger_journals
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_lines_immutable BEFORE UPDATE OR DELETE ON ledger_lines
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
```

## 6.3 Savings
```sql
CREATE TABLE savings_buckets (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  name VARCHAR(200) NOT NULL,                    -- e.g. Ubwizigame, Ingoboka
  description TEXT,
  bucket_type VARCHAR(30) NOT NULL,              -- SAVINGS | SOCIAL_FUND | SHARES
  is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
  minimum_contribution NUMERIC(19,2) NOT NULL DEFAULT 0,
  contribution_frequency VARCHAR(20) NOT NULL,   -- WEEKLY|BIWEEKLY|MONTHLY|PER_MEETING|ADHOC
  cycle_type VARCHAR(20) NOT NULL,               -- FIXED_TERM|ROLLING
  start_date DATE NOT NULL, end_date DATE,
  withdrawable BOOLEAN NOT NULL DEFAULT FALSE,   -- social funds usually are not
  late_penalty_rule JSONB,                       -- {type: FLAT|PERCENT, value: ..., grace_days: ...}
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (group_id, name)
);
ALTER TABLE ledger_accounts ADD CONSTRAINT fk_la_bucket FOREIGN KEY (bucket_id) REFERENCES savings_buckets(id);

CREATE TABLE contribution_obligations (   -- what each member OWES for a period
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  bucket_id BIGINT NOT NULL REFERENCES savings_buckets(id),
  membership_id BIGINT NOT NULL REFERENCES group_memberships(id),
  period_start DATE NOT NULL, due_date DATE NOT NULL,
  amount_due NUMERIC(19,2) NOT NULL, amount_paid NUMERIC(19,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',    -- OPEN|PARTIAL|PAID|OVERDUE|WAIVED
  UNIQUE (bucket_id, membership_id, period_start)
);

CREATE TABLE savings_transactions (        -- business record linked to the ledger journal
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  bucket_id BIGINT NOT NULL REFERENCES savings_buckets(id),
  membership_id BIGINT NOT NULL REFERENCES group_memberships(id),
  txn_type VARCHAR(20) NOT NULL,             -- CONTRIBUTION|WITHDRAWAL|SHARE_OUT
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  journal_id BIGINT NOT NULL REFERENCES ledger_journals(id),
  obligation_id BIGINT REFERENCES contribution_obligations(id),
  meeting_id BIGINT,
  payment_method VARCHAR(20) NOT NULL,       -- CASH|MOMO_MANUAL|MOMO_API|BANK
  external_ref VARCHAR(100),
  recorded_by BIGINT NOT NULL REFERENCES users(id),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## 6.4 Loans
```sql
CREATE TABLE loan_products (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  name VARCHAR(120) NOT NULL,
  interest_method VARCHAR(20) NOT NULL,         -- FLAT | REDUCING_BALANCE
  interest_rate_percent NUMERIC(7,4) NOT NULL,  -- per period, see interest_period
  interest_period VARCHAR(10) NOT NULL,         -- MONTH | LOAN_TERM
  min_amount NUMERIC(19,2), max_amount NUMERIC(19,2),
  max_multiple_of_savings NUMERIC(7,2),         -- e.g. 3x member savings
  min_term_months INT NOT NULL, max_term_months INT NOT NULL,
  repayment_frequency VARCHAR(20) NOT NULL,     -- MONTHLY|WEEKLY|AT_MATURITY
  grace_days INT NOT NULL DEFAULT 0,
  dual_approval_threshold NUMERIC(19,2),        -- >= requires President AND Treasurer
  allocation_order JSONB NOT NULL DEFAULT '["FINES","INTEREST","PRINCIPAL"]',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE loans (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  product_id BIGINT NOT NULL REFERENCES loan_products(id),
  borrower_membership_id BIGINT NOT NULL REFERENCES group_memberships(id),
  purpose TEXT,
  principal_amount NUMERIC(19,2) NOT NULL CHECK (principal_amount > 0),
  term_months INT NOT NULL,
  status VARCHAR(30) NOT NULL,
    -- SUBMITTED|PARTIALLY_COUNTERSIGNED|APPROVED|DISBURSED|OVERDUE|SETTLED|REJECTED|CANCELLED|WRITTEN_OFF
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at TIMESTAMPTZ, disbursed_at TIMESTAMPTZ, matures_on DATE,
  required_approvals INT NOT NULL,              -- snapshot at submission (1 or 2)
  rejection_reason TEXT,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_loans_group_status ON loans(group_id, status);
CREATE INDEX idx_loans_borrower ON loans(group_id, borrower_membership_id);
ALTER TABLE ledger_accounts ADD CONSTRAINT fk_la_loan FOREIGN KEY (loan_id) REFERENCES loans(id);

CREATE TABLE loan_approvals (               -- replaces loan_signatures; append-only
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  loan_id BIGINT NOT NULL REFERENCES loans(id),
  approver_membership_id BIGINT NOT NULL REFERENCES group_memberships(id),
  approver_role VARCHAR(20) NOT NULL,       -- role AT TIME OF SIGNING (snapshot)
  decision VARCHAR(10) NOT NULL CHECK (decision IN ('APPROVE','REJECT')),
  comment TEXT,
  ip_address INET, user_agent TEXT,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (loan_id, approver_membership_id)
);

CREATE TABLE loan_installments (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  loan_id BIGINT NOT NULL REFERENCES loans(id),
  installment_no INT NOT NULL,
  due_date DATE NOT NULL,
  principal_due NUMERIC(19,2) NOT NULL, interest_due NUMERIC(19,2) NOT NULL,
  principal_paid NUMERIC(19,2) NOT NULL DEFAULT 0, interest_paid NUMERIC(19,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|PARTIAL|PAID|OVERDUE
  UNIQUE (loan_id, installment_no)
);

CREATE TABLE loan_repayments (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  loan_id BIGINT NOT NULL REFERENCES loans(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  fines_part NUMERIC(19,2) NOT NULL DEFAULT 0,
  interest_part NUMERIC(19,2) NOT NULL DEFAULT 0,
  principal_part NUMERIC(19,2) NOT NULL DEFAULT 0,
  journal_id BIGINT NOT NULL REFERENCES ledger_journals(id),
  payment_method VARCHAR(20) NOT NULL, external_ref VARCHAR(100),
  meeting_id BIGINT,
  recorded_by BIGINT NOT NULL REFERENCES users(id),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (fines_part + interest_part + principal_part = amount)
);

CREATE TABLE loan_disbursements (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  loan_id BIGINT NOT NULL UNIQUE REFERENCES loans(id),   -- one disbursement per loan
  amount NUMERIC(19,2) NOT NULL,
  method VARCHAR(20) NOT NULL, external_ref VARCHAR(100),
  journal_id BIGINT NOT NULL REFERENCES ledger_journals(id),
  disbursed_by BIGINT NOT NULL REFERENCES users(id),
  disbursed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## 6.5 Fines
```sql
CREATE TABLE fine_rules (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  trigger_type VARCHAR(30) NOT NULL,   -- LOAN_OVERDUE|CONTRIBUTION_LATE|MEETING_ABSENT|MEETING_LATE|CUSTOM
  calc_type VARCHAR(10) NOT NULL,      -- FLAT|PERCENT
  value NUMERIC(19,4) NOT NULL,
  percent_base VARCHAR(20),            -- OVERDUE_AMOUNT|INSTALLMENT_AMOUNT|OUTSTANDING_PRINCIPAL
  recurrence VARCHAR(15) NOT NULL DEFAULT 'ONCE',  -- ONCE|PER_PERIOD
  period_days INT, max_total_per_item NUMERIC(19,2),
  grace_days INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE fines (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  membership_id BIGINT NOT NULL REFERENCES group_memberships(id),
  rule_id BIGINT REFERENCES fine_rules(id),
  source_type VARCHAR(30) NOT NULL,   -- INSTALLMENT|OBLIGATION|MEETING|MANUAL
  source_id BIGINT,
  period_key VARCHAR(40) NOT NULL,    -- makes the scan idempotent
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  amount_paid NUMERIC(19,2) NOT NULL DEFAULT 0,
  status VARCHAR(15) NOT NULL DEFAULT 'OPEN',   -- OPEN|PARTIAL|PAID|WAIVED
  reason TEXT, assessed_journal_id BIGINT REFERENCES ledger_journals(id),
  issued_by BIGINT REFERENCES users(id),        -- NULL = system
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (group_id, rule_id, source_type, source_id, period_key)
);
CREATE INDEX idx_fines_open_member ON fines(group_id, membership_id) WHERE status IN ('OPEN','PARTIAL');
```

## 6.6 Meetings and governance
```sql
CREATE TABLE meetings (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  title VARCHAR(200) NOT NULL,
  scheduled_at TIMESTAMPTZ NOT NULL, location VARCHAR(200),
  status VARCHAR(15) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED|IN_PROGRESS|CLOSED|CANCELLED
  opened_by BIGINT REFERENCES users(id), closed_by BIGINT REFERENCES users(id),
  closed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE meeting_attendance (
  meeting_id BIGINT NOT NULL REFERENCES meetings(id),
  group_id BIGINT NOT NULL REFERENCES groups(id),
  membership_id BIGINT NOT NULL REFERENCES group_memberships(id),
  status VARCHAR(15) NOT NULL,   -- PRESENT|ABSENT|LATE|EXCUSED
  excuse TEXT,
  PRIMARY KEY (meeting_id, membership_id)
);

CREATE TABLE meeting_minutes (
  meeting_id BIGINT PRIMARY KEY REFERENCES meetings(id),
  group_id BIGINT NOT NULL REFERENCES groups(id),
  body TEXT NOT NULL, written_by BIGINT NOT NULL REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE resolutions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  meeting_id BIGINT REFERENCES meetings(id),
  title VARCHAR(200) NOT NULL, description TEXT,
  outcome VARCHAR(15),            -- PASSED|REJECTED|DEFERRED
  votes_for INT, votes_against INT, votes_abstain INT,
  created_by BIGINT NOT NULL REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## 6.7 Expenses and income (group level)
```sql
CREATE TABLE group_expenses (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  category VARCHAR(60) NOT NULL, description TEXT,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  incurred_on DATE NOT NULL, journal_id BIGINT NOT NULL REFERENCES ledger_journals(id),
  recorded_by BIGINT NOT NULL REFERENCES users(id),
  approved_by BIGINT REFERENCES users(id),     -- maker-checker above threshold
  receipt_ref VARCHAR(100)
);
```

## 6.8 SaaS billing
```sql
CREATE TABLE subscription_plans (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,            -- TRIAL|BASIC|ADVANCED|...
  name VARCHAR(80) NOT NULL,
  price_monthly NUMERIC(19,2) NOT NULL, price_annual NUMERIC(19,2),
  member_limit INT, bucket_limit INT,
  features JSONB NOT NULL,                     -- feature flags: {"meetings":true,"reports_pdf":true,...}
  sms_included INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE subscriptions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL UNIQUE REFERENCES groups(id),
  plan_id BIGINT NOT NULL REFERENCES subscription_plans(id),
  status VARCHAR(20) NOT NULL,   -- TRIAL|ACTIVE|PAST_DUE|GRACE_PERIOD|SUSPENDED|CANCELLED
  billing_cycle VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',
  trial_ends_at TIMESTAMPTZ,
  current_period_start DATE, current_period_end DATE,
  grace_ends_at TIMESTAMPTZ, suspended_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE subscription_invoices (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
  invoice_number VARCHAR(30) NOT NULL UNIQUE,
  period_start DATE NOT NULL, period_end DATE NOT NULL,
  amount NUMERIC(19,2) NOT NULL, status VARCHAR(15) NOT NULL DEFAULT 'OPEN', -- OPEN|PAID|VOID
  due_date DATE NOT NULL, issued_at TIMESTAMPTZ NOT NULL DEFAULT now(), paid_at TIMESTAMPTZ,
  UNIQUE (subscription_id, period_start)
);

CREATE TABLE subscription_payments (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  invoice_id BIGINT NOT NULL REFERENCES subscription_invoices(id),
  provider VARCHAR(20) NOT NULL,             -- MTN_MOMO|MANUAL|AIRTEL
  provider_reference VARCHAR(100), idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  amount NUMERIC(19,2) NOT NULL, payer_phone VARCHAR(20),
  status VARCHAR(15) NOT NULL,               -- PENDING|SUCCESSFUL|FAILED|EXPIRED
  raw_payload JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), confirmed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_sub_pay_provider_ref ON subscription_payments(provider, provider_reference)
  WHERE provider_reference IS NOT NULL;
```

## 6.9 Platform tables
```sql
CREATE TABLE audit_logs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT,                          -- NULL for platform-level events
  actor_user_id BIGINT, actor_role VARCHAR(30),
  action VARCHAR(80) NOT NULL,              -- e.g. LOAN_APPROVED, SETTINGS_CHANGED, MEMBER_REMOVED
  entity_type VARCHAR(60), entity_id VARCHAR(60),
  before_state JSONB, after_state JSONB, reason TEXT,
  ip_address INET, user_agent TEXT, request_id VARCHAR(64),
  prev_hash CHAR(64), row_hash CHAR(64),    -- hash chain for tamper evidence (per group)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE notifications (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT, user_id BIGINT NOT NULL REFERENCES users(id),
  channel VARCHAR(10) NOT NULL,            -- SMS|EMAIL|IN_APP
  template_key VARCHAR(80) NOT NULL, params JSONB, locale VARCHAR(5) NOT NULL,
  status VARCHAR(15) NOT NULL DEFAULT 'QUEUED', -- QUEUED|SENT|DELIVERED|FAILED
  provider_message_id VARCHAR(100), attempts INT NOT NULL DEFAULT 0,
  cost NUMERIC(10,2), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), sent_at TIMESTAMPTZ, read_at TIMESTAMPTZ
);

CREATE TABLE outbox_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  aggregate_type VARCHAR(60) NOT NULL, aggregate_id VARCHAR(60) NOT NULL,
  event_type VARCHAR(80) NOT NULL, payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), processed_at TIMESTAMPTZ, attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE webhook_events (               -- raw inbound provider callbacks, for replay + dedupe
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  provider VARCHAR(20) NOT NULL, event_id VARCHAR(100), signature_valid BOOLEAN NOT NULL,
  payload JSONB NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT now(), processed_at TIMESTAMPTZ,
  UNIQUE (provider, event_id)
);

CREATE TABLE support_tickets (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT, user_id BIGINT NOT NULL REFERENCES users(id),
  subject VARCHAR(200) NOT NULL, body TEXT NOT NULL,
  status VARCHAR(15) NOT NULL DEFAULT 'OPEN', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE support_access_grants (       -- group consents to platform staff viewing data
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groups(id),
  granted_by BIGINT NOT NULL REFERENCES users(id),
  platform_user_id BIGINT NOT NULL REFERENCES users(id),
  expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ
);

CREATE TABLE scheduler_runs (               -- observability for cron-style jobs
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  job_name VARCHAR(80) NOT NULL, started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ, status VARCHAR(15), groups_processed INT, error TEXT
);
```

**Index guidance:** every FK and every `(group_id, …)` access path used by a list screen gets an index. Add `EXPLAIN` checks in tests for the three heaviest reports.

---

# 7. Financial ledger

## 7.1 Principles
1. **Double-entry, append-only.** Every financial event is a *journal* with ≥2 *lines*; total debits = total credits.
2. **Balances are derived.** `ledger_balances` is a projection updated inside the same transaction as the journal; a nightly **reconciliation job** recomputes from `ledger_lines` and alerts on any mismatch.
3. **Corrections = reversal + repost.** A mistaken contribution is corrected by a `REVERSAL` journal (`reverses_journal_id`), then a new correct journal. Never edit history.
4. **Idempotency:** `(group_id, idempotency_key)` is unique. A retry returns the original result.
5. **One posting API.** All money-affecting code goes through `LedgerService.post(JournalRequest)`. No other class writes to ledger tables (ArchUnit-enforced).

## 7.2 Standard journal templates
| Event | Debit | Credit |
|---|---|---|
| Member contribution (cash/MoMo received) | GROUP_CASH | MEMBER_SAVINGS (member, bucket) |
| Loan disbursement | LOAN_RECEIVABLE (loan) | GROUP_CASH |
| Loan repayment: principal | GROUP_CASH | LOAN_RECEIVABLE (loan) |
| Loan repayment: interest | GROUP_CASH | INTEREST_INCOME |
| Fine assessed | FINE_RECEIVABLE (member) | FINE_INCOME |
| Fine paid | GROUP_CASH | FINE_RECEIVABLE (member) |
| Fine waived | FINE_INCOME | FINE_RECEIVABLE (member) |
| Expense | EXPENSE | GROUP_CASH |
| Savings withdrawal | MEMBER_SAVINGS | GROUP_CASH |
| Share-out (V1.1) | MEMBER_SAVINGS + retained-earnings equity | SHARE_OUT_PAYABLE → GROUP_CASH |

> Interest accrual policy [DECISION]: recognise interest **when due** (installment due date) or **when paid**, per group setting; default **when paid** (cash basis) because most ibimina are cash-basis. Document the choice in group settings.

## 7.3 Enforcement
- Service layer validates `sum(debits) == sum(credits)` before insert.
- Add a **deferred constraint trigger** on `ledger_lines` that verifies per-journal balance at commit, as a safety net.
- `ledger_balances` updates use `SELECT … FOR UPDATE` on the affected account rows (ordered by id to prevent deadlocks) **and** the `version` column for optimistic locking; retry on conflict (bounded retries).
- Reject postings into a **closed period** (after cycle close) except via an explicit, audited, dual-approved adjustment.

## 7.4 Mandatory ledger tests
- Property-based test: random sequences of postings keep `sum(all balances by normal side)` consistent and every journal balanced.
- Concurrency test: 50 parallel contributions to one member/bucket yield exactly the expected balance.
- Idempotency test: same key twice → one journal.
- Mutation test: `UPDATE`/`DELETE` on `ledger_*` fails.
- Reconciliation test: tamper `ledger_balances` directly (as DB admin) → reconciliation job flags it.

---

# 8. Savings engine

## 8.1 Concepts
- **Bucket:** a named pool (Ubwizigame core savings, Ingoboka social fund, shares, project pools). Each has its own minimum, frequency, cycle, penalty, and withdrawability.
- **Obligation:** auto-generated per member per period from the bucket's schedule (job runs nightly, idempotent via the unique key).
- **Contribution:** recorded by the Treasurer (or in a meeting session) against an obligation or as an ad-hoc deposit; posts a ledger journal and a `savings_transactions` row atomically.

## 8.2 Rules
- Contribution amount > 0; may be partial; overpayment carries as ad-hoc deposit or applies to the next obligation per group setting.
- Contributions require an active membership and an ACTIVE bucket.
- Social-fund buckets are non-withdrawable except via a recorded resolution/payout (V2).
- Member balance per bucket = ledger balance of `MEMBER_SAVINGS(member, bucket)`.
- Recording a contribution **with MoMo reference** stores `external_ref`; a unique partial index prevents recording the same MoMo reference twice in one group. Show a warning on duplicate.

## 8.3 Withdrawal and exit
Defined by bylaws in settings: `withdrawals_allowed`, `notice_days`, `exit_fee`. A leaving member's settlement is computed (savings − outstanding loans − open fines) and requires dual approval.

## 8.4 Cycle close and share-out (V1.1) [OPEN]
The product owner must confirm the standard payout model(s) used by target groups (pro-rata by contribution vs. shares; treatment of interest and fine income; carry-over of social funds). Do not implement until confirmed.

---

# 9. Loan engine

## 9.1 Lifecycle (state machine)
```
          ┌────────────┐
 request  │ SUBMITTED  │───reject────────────▶ REJECTED
          └─────┬──────┘
         1st approval
          ┌─────▼──────────────────────┐
          │ PARTIALLY_COUNTERSIGNED    │───reject──▶ REJECTED
          └─────┬──────────────────────┘
       required approvals met
          ┌─────▼──────┐   cancel (by borrower, before disbursal)──▶ CANCELLED
          │  APPROVED  │
          └─────┬──────┘
   Treasurer records disbursement
          ┌─────▼──────┐   installment past due+grace   ┌─────────┐
          │ DISBURSED  │◀──────────────────────────────▶│ OVERDUE │
          └─────┬──────┘      cleared / re-paid          └────┬────┘
        fully repaid                                     write-off (dual approval)
          ┌─────▼──────┐                                ┌─────▼───────┐
          │  SETTLED   │                                │ WRITTEN_OFF │
          └────────────┘                                └─────────────┘
```
- Transitions are validated in one `LoanStateMachine` class; illegal transitions throw. A table-driven test asserts every (state × action) pair.
- No endpoint, role, or token may skip a state (preserves original Req 2.2).

## 9.2 Eligibility checks at request time (all configurable per product)
Active member; no existing blocking loan (configurable); amount within `[min,max]`; amount ≤ `max_multiple_of_savings × member savings`; term within bounds; no unpaid fines above threshold; group has sufficient **available funds** (`GROUP_CASH` balance − reserved approved-but-undisbursed loans). Return clear, localised reasons.

## 9.3 Approval rules (maker-checker) [DECISION]
- `required_approvals` is snapshotted at submission: **2** if `principal ≥ product.dual_approval_threshold` (or if threshold is null → always 2), else **1**.
- When 2 are required, approvers must be **President and Treasurer** (two distinct people).
- When 1 is required, the approver may be President or Treasurer.
- **The borrower can never approve their own loan.** If the borrower is the President or Treasurer, the Secretary substitutes for that role's approval (so approvals = the other officer + Secretary). If this cannot produce two valid distinct approvers, the loan cannot be approved and the UI explains why. [OPEN: confirm this substitution rule fits real group practice.]
- **The person who approves cannot be the person who records the disbursement for the same loan** when the group has ≥3 officers active. [DECISION, configurable]
- Each approval stores the approver's role at the time, IP, and user agent.
- Approving requires re-authentication (password or OTP) for amounts at or above the dual-approval threshold. [DECISION]
- A rejection by any required approver ends the loan as REJECTED with a mandatory reason.
- Approvals are append-only; a mistaken approval is handled by cancelling the loan before disbursal and re-submitting, not by editing.

## 9.4 Disbursement
- MVP: the Treasurer records that money was handed over (cash/MoMo/bank) with a reference → creates `loan_disbursements` + ledger journal + generates the installment schedule + moves status to DISBURSED.
- V2: automated MoMo disbursement via provider interface (after legal sign-off, 13.4).
- `loan_disbursements.loan_id` is UNIQUE → a loan cannot be disbursed twice, even under concurrent requests (also use `SELECT … FOR UPDATE` on the loan row).

## 9.5 Interest and schedule generation
- **FLAT:** `interest_total = principal × rate × periods`; spread evenly across installments.
- **REDUCING_BALANCE:** standard amortisation; compute with `BigDecimal` at scale 6; round each installment to whole RWF; **adjust the final installment** so principal sums exactly and total never drifts.
- Provide a pure, heavily unit-tested `LoanScheduleCalculator` with golden-file test cases (include cases for 1, 3, 6, 12 months; both methods; awkward rates).
- Schedule is generated once at disbursement and stored in `loan_installments`. Changes (restructure/reschedule) are a V2 feature requiring dual approval and creating a *new* schedule version, never mutating paid history.

## 9.6 Repayment allocation
Configurable order per product (default **Fines → Interest → Principal**), oldest due first. A repayment produces a single journal with the correct lines and a `loan_repayments` row whose parts sum to the amount (DB `CHECK`). Overpayment beyond the balance is rejected with a clear message (or applied as savings if the group enables it).

## 9.7 Overdue detection
- Nightly job (02:00 Africa/Kigali) via `@Scheduled` + ShedLock.
- Marks installments `OVERDUE` when `due_date + grace_days < today` and unpaid; sets loan status OVERDUE; emits events (notify borrower and officers; invoke fine engine).
- **Idempotent and resumable:** safe to re-run; processes group by group with its own transaction; logs to `scheduler_runs`; failure in one group does not abort others.

---

# 10. Fines and penalties engine

- Rules live in `fine_rules` per group, created from bylaws in the settings UI.
- **Triggers:** loan installment overdue, contribution late, absence/lateness at a meeting, or a manual fine by an officer (with reason).
- **Idempotency:** the `UNIQUE (group_id, rule_id, source_type, source_id, period_key)` constraint guarantees the nightly job cannot fine the same item twice. For recurring fines `period_key` is the period index (e.g., `2026-W41` or `P3`).
- **Caps:** `max_total_per_item` prevents runaway compounding. Fines **do not accrue interest** unless an explicit rule says so. [DECISION]
- **Posting:** assessment → `FINE_ASSESSED` journal (receivable vs income); payment → `FINE_PAID`; waiver → `FINE_WAIVED` (requires officer + reason; above a threshold requires dual approval).
- **Member visibility:** members see each fine, the rule that produced it, and the date. Disputes are a V2 workflow.

---

# 11. Meetings and governance

## 11.1 Meeting session flow
```
Create (Secretary) ──▶ Notify members (SMS/in-app) ──▶ Open meeting
   ──▶ Mark attendance ──▶ Record contributions / repayments / fines (fast entry grid)
   ──▶ Review & decide on loan requests ──▶ Record resolutions + minutes
   ──▶ Close meeting ──▶ Generate meeting report (PDF)
```

## 11.2 Requirements
- **Pre-meeting dashboard** shows: attendance expected, expected contributions, collected, outstanding, loan repayments due, fines pending, loan requests waiting, loans awaiting approval.
- **Fast-entry grid:** one row per member, columns for each bucket contribution + repayment + fine payment; keyboard-friendly; saves per-row with optimistic UI and idempotency keys; shows running totals vs expected. Designed for a treasurer entering 40 members quickly on a phone.
- **Attendance** automatically feeds the fine engine (absence/late rules) at meeting close.
- **Closing a meeting** locks its entries from edit (further changes are reversing journals) and produces an immutable report snapshot (stored PDF + hash).
- **Resolutions** record the decision and vote counts (digital voting is V2).
- Meeting minutes are editable by the Secretary until the meeting is closed, then versioned.

---

# 12. Subscription and billing (the SaaS layer)

## 12.1 Lifecycle
```
Group created ─▶ TRIAL (14 days, full Advanced features)
   │ pays before trial ends
   ▼                                   trial ends unpaid
ACTIVE ◀──────── pays ─────────────── PAST_DUE ─▶ GRACE_PERIOD (7 days) ─▶ SUSPENDED
   │ renewal invoice unpaid at period end        │
   └────────────▶ PAST_DUE                       └─ pay anytime ─▶ ACTIVE (everything restored)
ACTIVE ─ cancel at period end ─▶ CANCELLED (data retained; reactivatable)
```
- **Grace and trial lengths are configurable** (`platform_settings`), defaults 14 and 7 days.
- **Suspended = read-only**, never deleted. Members can still view their own balances and statements; officers can export their data. All write operations return a clear `SUBSCRIPTION_SUSPENDED` error with a pay-now link. [DECISION — strongly protects goodwill and conversion]
- **Data retention:** cancelled/suspended data retained ≥ 12 months [DECISION]; deletion on request follows the data-protection process (Section 16.8).

## 12.2 Billing job
Daily job (ShedLock): generate next invoice 5 days before period end; send reminders at T-3, T-0, T+3, T+7 in SMS (EN/RW); transition statuses by dates; all transitions audited and emit events. Idempotent via `UNIQUE (subscription_id, period_start)`.

## 12.3 Paying the subscription
1. Group admin opens **Billing** → sees plan, next invoice, history.
2. Chooses **Pay with MoMo** → platform calls `PaymentProvider.requestPayment(...)` (Section 13) → member approves the prompt on their phone.
3. Provider callback (or status poll fallback) → `subscription_payments` updated → invoice PAID → subscription ACTIVE and period extended → receipt sent by SMS/email.
4. **Manual fallback:** platform admin can record an offline payment (bank/cash/MoMo reference) with an audit entry, also dual-controlled once volume warrants.

## 12.4 Plan limits and feature flags
- Enforced server-side by a `PlanGate` (`canAddMember`, `canCreateBucket`, `featureEnabled("meetings")`). UI hides/disables, but the server is the authority.
- Exceeding a limit blocks the *new* action only; existing data is never removed on downgrade.
- Upgrade is immediate and prorated; downgrade applies at period end. [DECISION]

## 12.5 Platform admin console
Groups list with status/plan/members/last activity; subscription filters (trial ending, past due, suspended); manual payment recording; plan editor; impersonation is **not** supported — use consented support-access grants instead (Section 2.1).

---

# 13. Payments architecture

## 13.1 Provider abstraction [DECISION]
```java
public interface PaymentProvider {
    String code();                                              // "MTN_MOMO", "AIRTEL_MONEY"
    PaymentRequestResult requestPayment(PaymentRequest r);      // collect from a payer (request-to-pay)
    PaymentStatusResult  getPaymentStatus(String providerRef);  // poll fallback
    DisbursementResult   disburse(DisbursementRequest r);       // V2 only, behind a feature flag
    boolean verifyCallback(HttpHeaders h, byte[] rawBody);      // signature/auth per provider
    ParsedCallback parseCallback(byte[] rawBody);
}
```
- Provide `FakePaymentProvider` (deterministic, scriptable) for dev/test and E2E. All business logic is tested against the fake.
- A `PaymentProviderRegistry` selects by code. Adding Airtel = new class + config, no core change.

## 13.2 Webhook handling (applies to every provider)
1. Receive the **raw body**; verify authenticity **using the mechanism the provider actually documents** (shared-secret HMAC, signed JWT, IP allow-list, or a combination). **[VERIFY]** against the provider's current developer documentation — do not assume the `X-Callback-Signature` header from the old document exists for MTN MoMo.
2. Persist to `webhook_events` (dedupe on `provider + event_id`) **before** processing; return 200 quickly.
3. Process asynchronously/idempotently: look up the payment by provider reference, check amount and currency match the expected invoice, then apply.
4. **Never trust the callback alone for high-value state changes**: after a SUCCESSFUL callback, optionally confirm via `getPaymentStatus`.
5. **Reconciliation job:** every N minutes, poll PENDING payments older than a threshold; expire stale ones; alert on callbacks that don't match any payment.
6. Rate-limit and size-limit the endpoint; respond with generic bodies; log signature failures as security events.

## 13.3 MVP payment flows
- **Subscription payments (platform revenue):** MoMo request-to-pay + manual fallback. This is the platform collecting its own fees and is the lowest-risk use of mobile money.
- **Member contributions and repayments:** **recorded manually** by the Treasurer, optionally with the MoMo reference number. The platform does **not** touch group funds in MVP.

## 13.4 Regulatory boundary for moving group money [VERIFY — blocks V2 payment features]
Before building in-app member contribution collection or automated loan disbursement:
1. Obtain **written legal advice** on whether handling or routing members' funds would make the platform a regulated entity under Rwanda's payment-services and financial-service-provider rules (the National Bank of Rwanda publishes regulations and a list of licensed institutions, including a regulation on non-deposit-taking financial service providers and a directive on digital saving facilitators — **read the current texts; do not rely on this summary**).
2. Prefer routing funds **through a licensed provider** (funds settle directly into the group's own wallet/account) so the platform never holds them.
3. Gate these features behind a `payments.group_money_enabled` feature flag, **off by default**.

---

# 14. Notifications

## 14.1 Channels and architecture
- Channels: **SMS (primary)**, in-app, email (optional). WhatsApp in V2.
- `NotificationService.send(templateKey, recipient, params)` writes a `notifications` row and an outbox event; a worker sends via `SmsProvider` (interface; e.g., a Rwanda-capable aggregator — **[OPEN]** product owner to choose; implement a `FakeSmsProvider` first).
- Retries with exponential backoff, max attempts, dead-letter state; delivery receipts update status.
- Per-group SMS counters vs plan allowance; block or warn when exhausted. SMS cost recorded per message for margin tracking.
- **Respect opt-out** for non-critical messages; critical financial confirmations always send.
- SMS length: keep templates short; track GSM-7 vs Unicode segment counts (Kinyarwanda is Latin script, but verify special characters).

## 14.2 Required templates (EN + RW each; keys in an i18n file)
`otp_code`, `invite_to_group`, `contribution_received`, `contribution_due_reminder`, `loan_requested`, `loan_awaiting_your_approval`, `loan_approved`, `loan_rejected`, `loan_disbursed`, `repayment_received`, `repayment_due_reminder`, `loan_overdue`, `fine_assessed`, `fine_paid`, `meeting_scheduled`, `meeting_reminder`, `subscription_trial_ending`, `subscription_invoice_due`, `subscription_paid`, `subscription_suspended`.

> Kinyarwanda copy MUST be reviewed by a native speaker before launch. Claude Code writes the English source and placeholder RW; the product owner supplies final RW text.

---

# 15. Reporting and regulatory export

## 15.1 Reports (PDF + Excel; all derived from the ledger)
| Report | Audience | Contents |
|---|---|---|
| Member statement | Member | Opening balance, every contribution/withdrawal/repayment/fine, closing balance, period filter |
| Group financial summary | Officers | Assets (cash, loans receivable), member savings liabilities, income (interest, fines), expenses, net position |
| Contribution report | Treasurer | Expected vs paid per member/period; arrears |
| Loan portfolio report | Officers | Active, overdue, repaid; portfolio at risk; aging |
| Meeting report | All | Attendance, collections, decisions, minutes |
| Audit report | Auditor | Filtered audit log export |

- Reports are **point-in-time capable** (`as_of` date) by summing ledger lines up to that date.
- Generated reports are stored with a SHA-256 hash and generation metadata; each PDF footer shows the generation timestamp and report id so printed copies can be verified.
- Heavy reports run async with a status endpoint if > 3s.

## 15.2 Regulatory reporting and export [DECISION + VERIFY]
- The original document assumed direct synchronisation with a MINECOFIN portal and a "6-step entry structure". **Neither is verified.** Do **not** build an integration.
- Build a **Regulatory Reporting & Export** module that produces clearly labelled records an officer can use for official registration and reporting: member register, cash flows, account statements, loan portfolio, and the roles/approvals record (initiation vs verification vs approval).
- **[OPEN]** The product owner should obtain from the relevant authority (the sector/district administration and MINECOFIN) the actual current requirements and any templates, then provide them so the export can be matched precisely. Until then, label exports "Group Records Export", not "MINECOFIN submission".

---

# 16. Security

## 16.1 Authentication
- Phone number is the primary identifier. Registration verifies phone via SMS OTP (6 digits, 5-min expiry, max 5 attempts, per-phone and per-IP rate limits).
- Passwords: Argon2id (or bcrypt cost ≥ 12); min length 8; check against a common-password list; no composition rules theatre.
- Login lockout with progressive delay; generic error messages (no account enumeration).
- **Access token** 15 min; **refresh token** 30 days, **rotating with reuse detection** (reuse revokes the whole family). Store refresh tokens hashed. Prefer `HttpOnly; Secure; SameSite=Strict` cookies for the refresh token on web.
- Step-up re-authentication for sensitive actions: loan approval ≥ threshold, role transfer, financial settings change, member removal, data export.
- Optional TOTP/WebAuthn for officers (V2).

## 16.2 CSRF/CORS [corrects the original]
The original disabled CSRF globally. That is acceptable only for header-based bearer-token APIs. If the refresh token lives in a cookie, the refresh/logout endpoints **MUST** be CSRF-protected (SameSite=Strict + CSRF token or custom header check). CORS: explicit allow-list of origins; no wildcards with credentials.

## 16.3 Webhook endpoints
`permitAll` at the Spring layer is acceptable **only** because the controller itself verifies authenticity (Section 13.2). Add: strict body-size limit, IP allow-list **if** the provider publishes stable ranges, dedupe, replay-window checks where the provider supplies timestamps.

## 16.4 Secure coding baseline
- Validate all input (Bean Validation); reject unknown JSON fields.
- Parameterised queries only. No string-concatenated SQL.
- Output encoding in the SPA; strict **CSP**, `X-Content-Type-Options`, `Referrer-Policy`, HSTS.
- File uploads (documents) limited by type/size, virus-scanned if introduced, served from a separate origin/bucket with signed URLs.
- Dependency scanning (OWASP Dependency-Check / Dependabot) and secret scanning in CI.

## 16.5 Rate limiting
Per-IP and per-user (Bucket4j or a gateway): auth endpoints strict; OTP send very strict; read APIs moderate; webhook generous but bounded. Return `429` with `Retry-After`.

## 16.6 Audit and tamper evidence
- `audit_logs` is append-only with a **per-group hash chain** (`row_hash = SHA256(prev_hash || canonical_row)`). A nightly verifier recomputes and alerts on a broken chain.
- Log: logins (success/fail), role changes, settings changes (before/after), approvals/rejections, disbursements, repayments, fine waivers, member add/remove, exports, support-access grants, subscription changes.
- Never log secrets, full tokens, OTPs, or full national IDs.

## 16.7 Data protection
- Encrypt in transit (TLS 1.2+) and at rest (volume/DB encryption). Application-level encryption for national ID numbers (envelope encryption; keys outside the DB).
- PII minimisation: collect national ID only if the owner confirms it is needed for the group's compliance.
- Role-based field masking (e.g., phone partially masked for ordinary members viewing others).

## 16.8 Privacy and legal [VERIFY]
Rwanda has a personal data protection law (Law No. 058/2021 is commonly cited — **[VERIFY]** current requirements, registration/notification duties, cross-border transfer rules, and retention limits with a Rwandan lawyer). Provide: privacy policy and terms (EN/RW), explicit consent capture at registration, data-export and deletion-request flows, and a data-processing record. Prefer hosting that meets the legal requirements for data location **[VERIFY]**.

## 16.9 Secrets and repository hygiene
Carry forward the original's concern about leaked credentials: before the repo goes anywhere shared, scan history (e.g., `gitleaks`), rotate anything ever committed, and purge with `git filter-repo`/BFG if needed. Rotation matters more than rewriting history, because anything once public must be assumed compromised.

## 16.10 Threat model checklist (reviewed each phase)
BOLA/IDOR (Section 5.6 tests) · privilege escalation via role self-assignment · replayed/forged webhooks · double-spend via concurrent disbursement/repayment · negative/zero/huge amounts · rounding exploits · mass assignment · SMS pumping/abuse via OTP endpoint · enumeration · insider tampering (hash-chained audit, immutable ledger) · subscription bypass (server-side `PlanGate`).

---

# 17. API specification

**Conventions:** base `/api/v1`; JSON; `camelCase`; errors use RFC 7807 `application/problem+json` with a stable `code` and an i18n `message`; list endpoints are paginated (`page`, `size`, `sort`); money serialised as **strings** (`"150000.00"`) to avoid client float issues; timestamps ISO-8601 UTC; dates `YYYY-MM-DD`. Money-moving POSTs require header `Idempotency-Key`. OpenAPI generated and kept in CI (`openapi.json` checked for breaking changes).

## 17.1 Auth and profile
```
POST /auth/register                 {phone, fullName, password, locale}
POST /auth/verify-phone             {phone, otp}
POST /auth/login                    {phone, password}  → {accessToken, expiresIn} (+ refresh cookie)
POST /auth/refresh · POST /auth/logout
POST /auth/password/forgot · POST /auth/password/reset
GET  /me · PATCH /me                profile, locale
GET  /me/groups                     groups + my role + subscription status
```

## 17.2 Groups and members
```
POST   /groups                                  create (creator becomes PRESIDENT; starts TRIAL)
GET    /groups/{groupId}                        PATCH settings/profile
GET    /groups/{groupId}/settings               PUT (financial params require 2-officer confirm)
GET    /groups/{groupId}/members                POST invite · GET /members/{memberId}
PATCH  /groups/{groupId}/members/{memberId}     role/status (audited)
POST   /groups/{groupId}/invitations/accept     {token}
POST   /groups/{groupId}/offices/transfer       {role, toMemberId}   (two-step, audited)
```

## 17.3 Savings
```
GET/POST   /groups/{groupId}/buckets            GET/PATCH /buckets/{bucketId}
GET        /groups/{groupId}/obligations?bucketId&status&period
POST       /groups/{groupId}/contributions      {memberId,bucketId,amount,method,externalRef,obligationId?,meetingId?}
POST       /groups/{groupId}/withdrawals        (when allowed)
GET        /groups/{groupId}/members/{memberId}/balances
GET        /groups/{groupId}/members/{memberId}/transactions
POST       /groups/{groupId}/journals/{journalId}/reverse   {reason}   (officer + audit)
```

## 17.4 Loans
```
GET/POST   /groups/{groupId}/loan-products
POST       /groups/{groupId}/loans                          request {productId,amount,termMonths,purpose}
GET        /groups/{groupId}/loans?status&memberId          GET /loans/{loanId}
POST       /groups/{groupId}/loans/{loanId}/approve         {comment}   (replaces old /sign)
POST       /groups/{groupId}/loans/{loanId}/reject          {reason}
POST       /groups/{groupId}/loans/{loanId}/cancel
POST       /groups/{groupId}/loans/{loanId}/disburse        {method,externalRef}
GET        /groups/{groupId}/loans/{loanId}/schedule
POST       /groups/{groupId}/loans/{loanId}/repayments      {amount,method,externalRef,meetingId?}
```

## 17.5 Fines, expenses
```
GET/POST   /groups/{groupId}/fine-rules
GET        /groups/{groupId}/fines?status&memberId          POST (manual)
POST       /groups/{groupId}/fines/{fineId}/payments · POST /fines/{fineId}/waive
GET/POST   /groups/{groupId}/expenses
```

## 17.6 Meetings
```
GET/POST   /groups/{groupId}/meetings        GET/PATCH /meetings/{id}
POST       /meetings/{id}/open · /close
PUT        /meetings/{id}/attendance         [{memberId,status}]
GET        /meetings/{id}/summary            (pre-meeting dashboard + live totals)
PUT        /meetings/{id}/minutes · POST /meetings/{id}/resolutions
GET        /meetings/{id}/report             (PDF)
```

## 17.7 Dashboard and reports
```
GET /groups/{groupId}/dashboard
GET /groups/{groupId}/reports/{type}?from&to&asOf&format=json|pdf|xlsx
GET /groups/{groupId}/audit-logs?actor&action&from&to
GET /groups/{groupId}/exports/records             (Regulatory Reporting & Export)
```

## 17.8 Billing
```
GET  /groups/{groupId}/billing                    plan, status, next invoice
GET  /groups/{groupId}/billing/invoices
POST /groups/{groupId}/billing/invoices/{id}/pay  {provider, payerPhone}   (Idempotency-Key)
POST /groups/{groupId}/billing/plan               change plan
```

## 17.9 Webhooks and platform
```
POST /webhooks/momo                               (provider-verified; no JWT)
POST /webhooks/sms-delivery
GET  /platform/groups · /platform/subscriptions · /platform/metrics   (PLATFORM_ADMIN only)
POST /platform/subscriptions/{id}/manual-payment
```

## 17.10 Standard error codes (examples)
`VALIDATION_FAILED`, `NOT_FOUND`, `FORBIDDEN`, `SUBSCRIPTION_SUSPENDED`, `PLAN_LIMIT_REACHED`, `INSUFFICIENT_GROUP_FUNDS`, `LOAN_INVALID_TRANSITION`, `SELF_APPROVAL_FORBIDDEN`, `DUPLICATE_APPROVAL`, `DUPLICATE_EXTERNAL_REF`, `IDEMPOTENCY_CONFLICT`, `PERIOD_CLOSED`, `RATE_LIMITED`.

---

# 18. Frontend and UX

## 18.1 Principles
- **Mobile-first** (most users are on low-end Android phones, often slow networks). Target Lighthouse performance ≥ 85 on a mid-range device profile; keep the initial JS bundle small; lazy-load routes.
- **Plain language** in English and Kinyarwanda; a language toggle on every screen; numbers formatted `150,000 RWF`.
- **Role-aware navigation:** members see a simple "My Ikimina" view; officers see management tools.
- Accessible: WCAG 2.1 AA contrast, 44px touch targets, no colour-only meaning.
- Resilient: skeleton loaders, retry with backoff, clear offline message (full offline is V2).

## 18.2 Design tokens (from original brief)
Primary Emerald `#046A38`; Accent Amber `#D9A74A` (alerts, pending approvals, warnings); Surface `#F8FAFC`; font Inter with system fallbacks. **Verify** amber-on-white and emerald-on-white text meet AA contrast; adjust shades if not. Define tokens once (Tailwind theme + CSS variables) and support a future dark mode.

## 18.3 Screens (MVP)
**Public:** landing + pricing, login, register, OTP verify, forgot/reset password.
**Member:** My Ikimina home (balances, loan, fines, next meeting), transactions, statement download, request loan, loan detail + schedule, notifications, profile/language.
**Officer:** group dashboard, members (list/invite/roles), buckets, contribution entry + fast grid, loan inbox (needs my approval), loan detail with approval timeline, disburse, repayments, fines + rules, expenses, meetings (list/detail/live session), reports, audit log, settings/bylaws, billing.
**Platform admin:** groups, subscriptions, manual payments, metrics, support grants.

## 18.4 Critical UX flows (acceptance-tested end to end)
1. **Create group → invite members → set up bucket → record first contributions** within 10 minutes for a new user.
2. **Loan:** member requests → both required officers get SMS + inbox item → approve (re-auth) → status updates live → treasurer disburses → schedule appears → repayment recorded → balance and statement update.
3. **Meeting day:** open meeting → mark attendance → fast-entry grid → close → report PDF.
4. **Trial expiry:** reminder SMS → pay via MoMo prompt → instantly active; or lapse → read-only with pay banner → pay → restored.

## 18.5 Empty states and onboarding
A guided checklist ("Add members · Create savings bucket · Set loan rules · Schedule first meeting") on the dashboard until complete. A **bulk import** (CSV) for members and opening balances ships in V1.1 but the screens leave room for it.

---

# 19. Non-functional requirements

| Area | Requirement |
|---|---|
| Performance | p95 API latency < 400 ms for reads, < 800 ms for writes at 500 concurrent groups; dashboards < 1.5 s; ledger queries use indexed `group_id` paths |
| Availability | 99.5% monthly target at MVP; graceful degradation when SMS/MoMo providers are down (queue, never lose) |
| Scalability | Stateless app instances; vertical headroom first; read replica later; partitioning of `ledger_lines`/`audit_logs` by group or time when row counts justify |
| Data integrity | ACID transactions; DB constraints as the final guard; nightly ledger reconciliation |
| Concurrency | Optimistic locking (`@Version`) + row locks on ledger accounts and loans; idempotency keys |
| Localisation | `en`, `rw` for UI, errors, SMS, PDFs |
| Accessibility | WCAG 2.1 AA |
| Browser support | Last 2 versions of Chrome, Firefox, Safari, Edge; Android Chrome priority |
| Observability | Structured JSON logs with `requestId`/`groupId`; metrics; traces; alerts (Section 21.4) |
| Backup/RPO/RTO | RPO ≤ 15 min (WAL archiving/PITR); RTO ≤ 4 h; quarterly restore drill |
| Data retention | Financial records retained ≥ 7 years unless law/legal advice says otherwise **[VERIFY]**; soft-delete/archival only |

---

# 20. Testing strategy

## 20.1 Pyramid
- **Unit:** `Money`, `LoanScheduleCalculator`, `FineCalculator`, `LoanStateMachine`, permission matrix, plan gates. Aim for near-total coverage of financial logic; **mutation testing** (PIT) on `ledger`, `loans`, `fines`, `billing`.
- **Integration (Testcontainers/Postgres):** repositories, migrations, RLS policies, ledger constraints/triggers, outbox, webhook idempotency, schedulers with a fake clock.
- **API/contract:** every endpoint with `@WebMvcTest`/`MockMvc` or REST-assured; OpenAPI contract checks.
- **Security tests:** tenant isolation harness (5.6), authz matrix per role per endpoint, replayed webhooks, concurrency/double-disburse, OTP abuse limits, JWT tampering, refresh-token reuse.
- **Architecture tests (ArchUnit):** module boundaries; only `LedgerService` writes ledger tables; no Lombok; no `double`/`float` in money packages.
- **E2E (Playwright):** the four flows in 18.4 in both languages on a mobile viewport.
- **Performance:** k6/Gatling scripts for contribution entry, dashboard, report generation.
- **Migration tests:** apply all migrations on an empty DB and on a prior-version snapshot; assert no destructive statements (grep-based CI check for `DROP TABLE|TRUNCATE|DELETE FROM` in migration files).

## 20.2 Time handling
Inject a `Clock` everywhere. No `LocalDate.now()` in business code. Tests use a fixed `Africa/Kigali` clock to cover month boundaries and overdue edges.

## 20.3 Definition of Done (every task)
Code + unit tests + integration tests + audit log + i18n keys + OpenAPI updated + permission checks + tenant isolation test + docs updated + CI green.

---

# 21. Infrastructure, CI/CD, observability, backup

## 21.1 Environments
`local` (docker-compose: Postgres, optional Redis, MailHog, fake providers) → `staging` (prod-like, sandbox providers) → `production`.

## 21.2 Hosting [OPEN]
Product owner to choose a provider balancing cost, latency to Rwanda, and data-location requirements **[VERIFY]**. Start simple: one app container + managed Postgres + object storage + a CDN for the SPA. Infrastructure as code (Terraform or provider-native) once stable.

## 21.3 CI/CD (GitHub Actions)
On every PR: build, unit + integration tests, ArchUnit, migration safety check (no destructive SQL), dependency + secret scan, OpenAPI diff, frontend lint/type-check/tests, Playwright smoke. On merge to main: build image, deploy to staging automatically, **manual approval** for production. Database migrations run as a separate, backward-compatible step **before** the app rollout (expand → migrate → contract across releases).

## 21.4 Observability and alerts
Health checks (`/actuator/health`), metrics (Micrometer → Prometheus/Grafana or hosted equivalent), error tracking (Sentry or equivalent), uptime monitor. Alert on: ledger reconciliation mismatch (page), audit hash-chain break (page), webhook signature failures spike, scheduler job failure/not-run, SMS failure rate, payment pending-too-long, error rate, DB connection saturation, disk, backup failure.

## 21.5 Backup and recovery
Automated daily full + continuous WAL archiving (point-in-time recovery); encrypted; stored in a separate account/region where possible; **mandatory pre-migration snapshot**; documented runbook; restore drill each quarter with a recorded result. Never rely on a backup that hasn't been restored once.

## 21.6 Operational runbooks (create in `/docs/runbooks`)
Deploy & rollback · restore from backup · reconcile a ledger mismatch · handle a failed/duplicated payment callback · suspend/reactivate a group manually · rotate secrets · respond to a security incident · onboard a group with historical data.

---

# 22. Seed and demo data

Provide a `dev`/`staging`-only seeder (never runs in production) creating:
- Platform admin, 3 groups (Trial, Basic-Active, Advanced-PastDue) with different configurations.
- Group "Twisungane Ikimina" (Advanced): 24 members (President, Treasurer, Secretary, Auditor, 20 members), 2 buckets (Ubwizigame monthly 5,000; Ingoboka per-meeting 500), 6 months of contributions, 3 loan products, loans in each state (SUBMITTED, PARTIALLY_COUNTERSIGNED, APPROVED, DISBURSED, OVERDUE, SETTLED, REJECTED), fines (open/paid/waived), 4 meetings (one open), invoices and payments in several statuses.
- A second group with overlapping phone numbers/members to exercise multi-group membership and tenant isolation.
- A script that prints demo credentials (dev only).

---

# 23. Implementation phases with acceptance criteria

Each phase ends with a demo, a test report, and the person's approval.

### Phase 0 — Foundations (≈1 week)
Repo scaffold, module structure, Flyway, Testcontainers, CI, `CLAUDE.md`, `Money`, `Clock`, error model (RFC 7807), i18n skeleton, ArchUnit rules, docker-compose, OpenAPI.
**Accept:** CI green; empty app boots; migration safety check works; ArchUnit enforces rules.

### Phase 1 — Identity, groups, multi-tenancy
Registration/OTP/login/refresh, groups, memberships, roles, invitations, settings, `GroupAccessGuard`, RLS, audit log (with hash chain), tenant isolation harness.
**Accept:** the 5.6 isolation tests pass for every endpoint; role matrix tests pass; audit chain verifier works; OTP/login rate limits demonstrated.

### Phase 2 — Ledger and savings
`LedgerService`, accounts, journals, balances, reconciliation job, buckets, obligations, contributions, statements (JSON), member balances.
**Accept:** all ledger tests (7.4) pass; 50-way concurrent contributions test passes; reversal flow works; balances reconcile.

### Phase 3 — Loans
Products, request/eligibility, maker-checker approvals, state machine, disbursement, schedule calculator, repayments with allocation, overdue job.
**Accept:** golden schedule tests; every illegal transition rejected; self-approval blocked; double-disburse blocked under concurrency; overdue job idempotent with a fake clock.

### Phase 4 — Fines and expenses
Rules, automatic fines (idempotent), manual fines, payment/waiver, expenses with threshold approval.
**Accept:** rerunning the nightly scan creates zero duplicates; caps respected; journals balanced.

### Phase 5 — Meetings
Meeting lifecycle, attendance, fast-entry grid endpoints, resolutions, minutes, closing, meeting report PDF.
**Accept:** E2E "meeting day" flow passes; closed meeting entries are immutable; attendance triggers fines.

### Phase 6 — Notifications
Templates (EN/RW), outbox worker, SMS provider interface + fake, in-app notifications, SMS counters.
**Accept:** every template has EN+RW keys (CI check); retries/dead-letter tested; provider outage doesn't lose messages.

### Phase 7 — Subscriptions and billing
Plans, trial, invoices, MoMo payment via provider interface + fake, webhooks, reconciliation, grace/suspension/reactivation, `PlanGate`, platform admin console.
**Accept:** full lifecycle test with fake clock (trial→paid; trial→grace→suspended→paid→active); replayed/forged webhook rejected; suspended group is read-only but data intact.

### Phase 8 — Reports, dashboards, regulatory export
All reports (PDF/Excel), dashboards, "Group Records Export", report hashing.
**Accept:** report totals equal ledger sums for seeded data; point-in-time `asOf` correct; large group report under performance budget.

### Phase 9 — Frontend completion, hardening, launch readiness
All screens, i18n review, accessibility pass, performance budget, security review against 16.10, load test, backup/restore drill, runbooks, privacy/terms pages, production deploy, monitoring and alerts live.
**Accept:** launch checklist (Section 24.3) fully ticked.

> The frontend is built alongside each backend phase (vertical slices), not left to the end; Phase 9 is polish and hardening.

### V1.1 / V2 / V3 — see Section 3.

---

# 24. Claude Code prompts

## 24.1 Kickoff prompt (paste once, at the start)
```
Read IKIMINA_SaaS_Master_Specification.md fully, then Ikimina_Project_Documentation.md
(the original, for context only; the master spec wins on any conflict).

Rules: follow Section 0 Hard Rules strictly (no Lombok, BigDecimal only, no destructive
migrations, append-only ledger, group_id on every tenant table, audit logging).
Do not assume anything tagged [OPEN] or [VERIFY] — list them and ask me.

Step 1: create CLAUDE.md summarising the Hard Rules, the tech stack, the module layout,
and the current phase. Step 2: propose the Phase 0 plan (files, migrations, tests) and
STOP for my approval. Do not write application code until I approve.
```

## 24.2 Per-phase prompt template
```
We are starting Phase N of IKIMINA_SaaS_Master_Specification.md.
1. Re-read the sections referenced by this phase and the Hard Rules.
2. Produce a plan: modules, classes, migrations (additive only), endpoints, tests,
   i18n keys, audit events, permissions. List risks and any [OPEN]/[VERIFY] items.
3. Wait for my approval.
4. Implement in small commits, tests first for financial logic.
5. Run the full test suite and report results against the phase's acceptance criteria,
   item by item (pass/fail with evidence). Do not claim done if any item is unverified.
```

## 24.3 Feature verification prompts
Use after each phase:
- **Tenant isolation:** "Write and run tests proving a member/officer of Group A cannot read or modify any Group B resource through every endpoint added in this phase. List the endpoints covered."
- **Ledger integrity:** "Run the ledger reconciliation and the property-based and concurrency tests. Show that every journal balances and balances match ledger_lines."
- **Authorization:** "Generate a role × endpoint matrix from the code and compare it to Section 5.5. Report any mismatch."
- **Money safety:** "Search the codebase for float/double in money paths, Lombok imports, destructive SQL in migrations, and non-ledger writes to ledger tables. Report findings."
- **Idempotency:** "Replay every money-moving request and every webhook twice; show that exactly one effect occurs."
- **Security review:** "Walk Section 16.10 and show, for each threat, the code and test that mitigates it."

### Launch checklist
- [ ] All phase acceptance criteria met with evidence
- [ ] Legal advice obtained on: data protection, terms/privacy, payment regulation, tontine/group record requirements
- [ ] Final Kinyarwanda copy reviewed by a native speaker
- [ ] MoMo sandbox → production credentials, callbacks verified per provider docs
- [ ] SMS provider contract, sender ID approved, cost model validated
- [ ] Backup restore drill performed and documented
- [ ] Monitoring/alerts firing to a real phone
- [ ] Pen-test or independent security review of auth, tenancy, payments
- [ ] Pilot with 3–5 real groups; fixes shipped before public launch
- [ ] Support process (WhatsApp/phone), onboarding guide, and training material ready

---

# 25. Open questions for the product owner

1. **Share-out model:** how do your target groups distribute savings, interest and fines at cycle end? (Blocks V1.1.)
2. **Approval substitution:** when the borrower is the President or Treasurer, is the Secretary the right substitute? Any other roles (e.g., a committee)?
3. **Interest policy:** flat or reducing balance by default? Typical monthly rates? Cash-basis vs when-due recognition?
4. **Group size reality:** typical and maximum members — to confirm the Basic (30) / Advanced (100) limits.
5. **SMS provider:** which Rwanda-capable provider, and who pays for SMS (bundled vs pay-as-you-go)?
6. **MoMo account:** do you already have an MTN MoMo developer/merchant account for collecting subscriptions? Sandbox access?
7. **Hosting and data location:** budget and preferred provider.
8. **Legal:** name of the Rwandan lawyer/advisor for payment-regulation, data-protection and terms review.
9. **Regulatory export:** can you obtain the current official requirements/templates from the sector administration and MINECOFIN?
10. **National ID:** do you need to collect it, or is phone + name enough for MVP?
11. **Brand:** product name, logo, domain, and who writes the final Kinyarwanda copy?
12. **Pilot groups:** which 3–5 real ibimina will test the MVP, and can you get their actual bylaws to validate the settings model?

---

# 26. Changes from the original document

| Original | Now | Why |
|---|---|---|
| Microservices + Kafka + Redis cluster | Modular monolith + transactional outbox, Redis optional | Operational cost and failure modes unjustified at launch; boundaries preserved for later extraction |
| Spring Boot 3.2.3, Java 17, PostgreSQL 12+ | Latest stable 3.x, Java 21, PostgreSQL 16+ | Older lines are out of support **[VERIFY at start]** |
| Lombok (`@RequiredArgsConstructor`) | No Lombok | Project convention; explicit code |
| `DROP TABLE … CASCADE` "cleanup" | Forward-only additive Flyway migrations | Never destroy financial data |
| `savings_entries` balance-style table | Immutable double-entry ledger + projections | Auditability; corrections without rewriting history |
| `loan_signatures` (`/sign`) | `loan_approvals` (`/approve`, `/reject`) with role snapshot, IP, re-auth, no self-approval | Real maker-checker |
| No tenant model | `group_id` everywhere + RLS + guard + isolation tests | SaaS data isolation |
| BOLA fix via `#userId == principal.id` | Layered authorization (membership → permission → ownership → RLS) | `principal.id` checks alone don't cover cross-group access |
| `permitAll` + "HMAC + IP whitelist" for MoMo | Provider-documented verification **[VERIFY]**, dedupe, reconciliation | Don't assume a header the provider may not send |
| Direct "sync with MINECOFIN portal" and a "6-step structure" | "Group Records Export"; integration only after verified requirements | Unverified claim; avoid promising what may not exist |
| 4 implementation phases | 10 phases with measurable acceptance criteria | Executable by Claude Code in controlled steps |
| No billing/subscription | First-class subscription module with trial/grace/suspend/reactivate | Core to the business model |
| No meetings/notifications/reports | First-class modules | Core ibimina workflow and value |
| `NUMERIC(19,4)` | `NUMERIC(19,2)` with whole-RWF posting rule via `Money` | RWF has no practical minor unit; one rounding point |

---

*End of specification.*
