-- Phase 0 foundation.
--
-- Rules for every migration in this directory (Hard Rule H3, spec 6 and 20.1):
--   * Forward-only and additive. No DROP TABLE/COLUMN/SCHEMA, TRUNCATE, DELETE FROM,
--     RENAME, or in-place column type changes. MigrationSafetyTest enforces this,
--     and CI rejects any edit or removal of a migration that already exists.
--   * The schema is owned by ikimina_owner (who runs Flyway). The application
--     connects as ${appRole}, which owns nothing: grant it exactly the privileges
--     each table needs, table by table.

-- Shared guard for append-only tables. Later phases attach it to the ledger
-- (journals, lines) and audit_logs:
--   CREATE TRIGGER ... BEFORE UPDATE OR DELETE ON <t> FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
--   CREATE TRIGGER ... BEFORE TRUNCATE ON <t> FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
CREATE FUNCTION forbid_mutation() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'rows in %.% are immutable: % rejected', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

COMMENT ON FUNCTION forbid_mutation() IS
    'Trigger function for append-only tables (ledger, audit log). Corrections are new rows, never edits.';

-- ShedLock: prevents a scheduled job running on two instances at once (spec 4.2).
-- Column types follow the ShedLock documentation for PostgreSQL; lock times are
-- written from the database clock (usingDbTime), so they are UTC by construction.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON shedlock TO ${appRole};
