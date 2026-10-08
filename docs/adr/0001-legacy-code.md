# ADR 0001 — Build the spec's backend fresh; freeze the legacy application

- **Status:** Accepted (product owner approved the Phase 0 plan, 2026-10-08)
- **Context documents:** `IKIMINA_SaaS_Master_Specification.md` (the spec), `CLAUDE.md`

## Context

The repository already holds a working application that predates the spec:
`ikimina/` (Spring Boot backend, migrations V1–V9) and `frontend-ikimina/` (React, JavaScript).
It conflicts with the spec at the foundations:

| Area | Legacy | Spec |
|---|---|---|
| Lombok (H2) | used in 33 classes | banned |
| Ledger (H5) | single-entry `ledger_entries`, globally unique idempotency key | double-entry journals + lines + balances, key unique per group |
| Identity / tenancy (H4) | email/username users, global `ROLE_*`, no RLS | phone identity, per-group roles, `group_id` + RLS + 404 guard |
| Audit (H8) | written in a separate transaction, no hash chain | same transaction, per-group hash chain |
| Migrations (H3) | V4 drops/renames a column, V5 changes column types | forward-only, additive |
| Time | 60 direct `now()` calls | injected `Clock` |
| Tests | H2 smoke test | Testcontainers PostgreSQL, ArchUnit |

The owner approved the plan built on the assumption that no deployed database holds real group
data that must be preserved. That assumption is **to be confirmed explicitly** by the owner; if it
is wrong, revisit this ADR before Phase 2 (the ledger).

## Decision

1. Build the spec's backend from scratch in **`backend/`** (Maven, Java 21, Spring Boot 4.1.x,
   base package **`rw.ikimina`**) with its **own Flyway history starting at V1**, on a **new database**.
2. The legacy `ikimina/` and `frontend-ikimina/` are **frozen**: no new features, kept building in CI
   until the new application replaces them, then removed in a dedicated change.
3. Reuse ideas, not code wholesale. Carried over and adapted: Dockerfile hardening, request-id filter,
   JSON logging with masking, CI structure, env-var-only configuration. Re-implemented to the spec:
   `Money` (now a whole-RWF value type), payments idempotency, consent/export (later phases).
4. The new frontend (TypeScript, TanStack Query, Zod, i18next) starts in Phase 1, alongside its first screens.

## Consequences

- No legacy data migration is needed. If that changes (a group with real records appears before
  launch), import goes through the V1.1 bulk-import path (spec 3.2), never by carrying old tables over.
- Two applications live in the repo for a while; CI keeps both green, and `CLAUDE.md` makes clear which one is current.
- The legacy migrations' destructive statements (V4, V5) stay where they are; they are frozen history
  and are outside the new migration-safety check's scope (`backend/` only).

## Related decisions taken at the same approval

- **Spring Boot 4.1.x** instead of the spec's "latest 3.x" (spec 4.2 [VERIFY]); the owner accepted the recommendation.
- **Migration safety is strict, no override:** bans DROP TABLE/COLUMN/SCHEMA/DATABASE, TRUNCATE,
  DELETE FROM, RENAME, in-place column type changes, and anything that disables the append-only
  triggers or row-level security (DROP TRIGGER/FUNCTION/POLICY, DISABLE TRIGGER, DISABLE/NO FORCE RLS, BYPASSRLS).
- **Two database roles:** `ikimina_owner` runs migrations and owns every object; `ikimina_app` is what the
  application connects as, owns nothing, and receives privileges table by table in the migrations.
