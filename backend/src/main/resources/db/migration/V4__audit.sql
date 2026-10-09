-- Phase 1: append-only, hash-chained audit log (spec 6.9, 16.6, Hard Rule H8).
--
-- Each chain - one per group, plus chain 0 for platform events (group_id NULL) - links
-- every row to the previous one: row_hash = SHA-256(prev_hash || audit_canonical(row)).
-- The hash is computed here, in a trigger, so the writer and the nightly verifier use
-- one canonical form (timestamps, IP addresses and JSON normalised by PostgreSQL itself).

CREATE TABLE audit_logs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id      BIGINT      REFERENCES groups (id),   -- NULL for platform-level events
    actor_user_id BIGINT      REFERENCES users (id),
    actor_role    VARCHAR(30),
    action        VARCHAR(80) NOT NULL,
    entity_type   VARCHAR(60),
    entity_id     VARCHAR(60),
    before_state  JSONB,
    after_state   JSONB,
    reason        TEXT,
    ip_address    INET,
    user_agent    TEXT,
    request_id    VARCHAR(64),
    -- Position in the chain, assigned under the chain lock. Ids are not usable for this: they are
    -- drawn before the lock is taken, so under concurrency id order and chain order differ.
    chain_seq     BIGINT      NOT NULL,
    prev_hash     CHAR(64)    NOT NULL,
    row_hash      CHAR(64)    NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_audit_logs_chain_seq ON audit_logs (COALESCE(group_id, 0), chain_seq);
CREATE INDEX idx_audit_logs_group ON audit_logs (group_id, id);
CREATE INDEX idx_audit_logs_group_created ON audit_logs (group_id, created_at);

-- The current end of each chain. Locked FOR UPDATE by every insert into that chain, so
-- concurrent writers queue up instead of forking the chain.
CREATE TABLE audit_chain_heads (
    chain_key  BIGINT      PRIMARY KEY,                 -- group id, or 0 for the platform chain
    last_hash  CHAR(64)    NOT NULL,
    last_seq   BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION audit_canonical(a audit_logs) RETURNS text
    LANGUAGE sql STABLE
AS $$
    SELECT jsonb_build_object(
        'group_id',      a.group_id,
        'actor_user_id', a.actor_user_id,
        'actor_role',    a.actor_role,
        'action',        a.action,
        'entity_type',   a.entity_type,
        'entity_id',     a.entity_id,
        'before_state',  a.before_state,
        'after_state',   a.after_state,
        'reason',        a.reason,
        'ip_address',    host(a.ip_address),
        'user_agent',    a.user_agent,
        'request_id',    a.request_id,
        'chain_seq',     a.chain_seq,
        'created_at',    to_char(a.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
    )::text
$$;

COMMENT ON FUNCTION audit_canonical(audit_logs) IS
    'Canonical text of an audit row for hashing. Changing it would break every existing chain: add a new version instead.';

CREATE FUNCTION audit_chain_link() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    chain    BIGINT := COALESCE(NEW.group_id, 0);
    previous CHAR(64);
    next_seq BIGINT;
BEGIN
    INSERT INTO audit_chain_heads (chain_key, last_hash)
    VALUES (chain, repeat('0', 64))
    ON CONFLICT (chain_key) DO NOTHING;

    SELECT last_hash, last_seq + 1 INTO previous, next_seq FROM audit_chain_heads WHERE chain_key = chain FOR UPDATE;

    -- Whatever the caller supplied for these columns is ignored.
    NEW.chain_seq := next_seq;
    NEW.prev_hash := previous;
    NEW.row_hash := encode(sha256(convert_to(previous || audit_canonical(NEW), 'UTF8')), 'hex');

    UPDATE audit_chain_heads SET last_hash = NEW.row_hash, last_seq = next_seq, updated_at = now() WHERE chain_key = chain;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_audit_chain_link BEFORE INSERT ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_chain_link();
CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_audit_no_truncate BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

-- The application may write and read audit rows, never change them. Belt and braces:
-- without UPDATE/DELETE privileges the trigger above is never even reached.
GRANT SELECT, INSERT ON audit_logs TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON audit_chain_heads TO ${appRole};

-- Observability for scheduled jobs (spec 6.9).
CREATE TABLE scheduler_runs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name         VARCHAR(80) NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    status           VARCHAR(15),
    groups_processed INT,
    error            TEXT
);
CREATE INDEX idx_scheduler_runs_job ON scheduler_runs (job_name, started_at DESC);

GRANT SELECT, INSERT, UPDATE ON scheduler_runs TO ${appRole};
