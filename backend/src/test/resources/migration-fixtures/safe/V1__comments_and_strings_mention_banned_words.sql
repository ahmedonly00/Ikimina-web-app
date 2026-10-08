-- This migration must never DROP TABLE or TRUNCATE anything; corrections never DELETE FROM the ledger.
/* Block comment: ALTER TABLE x RENAME TO y would be banned,
   and so would DROP COLUMN. */
COMMENT ON TABLE shedlock IS 'Never DELETE FROM or TRUNCATE this; don''t RENAME it either.';
CREATE TABLE example_rows (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    renamed_at  TIMESTAMPTZ,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    truncated   BOOLEAN NOT NULL DEFAULT FALSE
);
