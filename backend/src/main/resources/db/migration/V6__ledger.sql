-- Phase 2: the double-entry, append-only ledger (spec 6.2, 7; Hard Rules H4, H5, H7).
--
-- Additions to the spec's DDL, all in the same spirit:
--   * journals get a public_id (spec 6: sequential ids never in URLs) and a request_hash, so a
--     retried request is recognised and a reused key with a different request is refused (H7);
--   * (id, group_id) is unique on journals and accounts, and lines/balances reference those
--     pairs, so a line can never point at another group's journal or account - not even by a bug;
--   * a journal can be reversed only once;
--   * reversal requests: a reversal moves money, so one officer asks and another confirms (H9).
--
-- Only LedgerService writes these tables (spec 7.1; enforced by tests). The application role
-- may INSERT journals and lines but never UPDATE or DELETE them; triggers enforce the same.

CREATE TABLE ledger_accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id      BIGINT      NOT NULL REFERENCES groups (id),
    account_type  VARCHAR(40) NOT NULL,
    membership_id BIGINT      REFERENCES group_memberships (id),
    bucket_id     BIGINT,     -- FK added with savings_buckets (V7)
    loan_id       BIGINT,     -- FK added with loans (Phase 3)
    normal_side   VARCHAR(6)  NOT NULL CHECK (normal_side IN ('DEBIT', 'CREDIT')),
    currency      CHAR(3)     NOT NULL DEFAULT 'RWF' CHECK (currency = 'RWF'),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, group_id)
);
CREATE UNIQUE INDEX uq_ledger_account_dims ON ledger_accounts
    (group_id, account_type, COALESCE(membership_id, 0), COALESCE(bucket_id, 0), COALESCE(loan_id, 0));
CREATE INDEX idx_ledger_accounts_membership ON ledger_accounts (group_id, membership_id);

CREATE TABLE ledger_journals (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id            BIGINT       NOT NULL REFERENCES groups (id),
    journal_type        VARCHAR(40)  NOT NULL,
    idempotency_key     VARCHAR(100) NOT NULL,
    request_hash        VARCHAR(64)  NOT NULL,
    business_date       DATE         NOT NULL,
    description         TEXT,
    source              VARCHAR(30)  NOT NULL CHECK (source IN ('MANUAL', 'MEETING', 'MOMO', 'SYSTEM')),
    external_ref        VARCHAR(100),
    meeting_id          BIGINT,
    loan_id             BIGINT,
    member_id           BIGINT       REFERENCES group_memberships (id),
    reverses_journal_id BIGINT       REFERENCES ledger_journals (id),
    created_by          BIGINT       NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (group_id, idempotency_key),
    UNIQUE (id, group_id)
);
-- A journal is reversed at most once.
CREATE UNIQUE INDEX uq_ledger_journals_reverses ON ledger_journals (reverses_journal_id) WHERE reverses_journal_id IS NOT NULL;
CREATE INDEX idx_ledger_journals_group_date ON ledger_journals (group_id, business_date, id);

CREATE TABLE ledger_lines (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_id BIGINT        NOT NULL,
    group_id   BIGINT        NOT NULL REFERENCES groups (id),
    account_id BIGINT        NOT NULL,
    direction  VARCHAR(6)    NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount     NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    FOREIGN KEY (journal_id, group_id) REFERENCES ledger_journals (id, group_id),
    FOREIGN KEY (account_id, group_id) REFERENCES ledger_accounts (id, group_id)
);
CREATE INDEX idx_lines_account ON ledger_lines (account_id);
CREATE INDEX idx_lines_journal ON ledger_lines (journal_id);

-- Derived projection for fast reads, updated in the posting transaction and always
-- reconcilable from ledger_lines (spec 7.1 #2). Signed by the account's normal side.
CREATE TABLE ledger_balances (
    account_id      BIGINT        PRIMARY KEY,
    group_id        BIGINT        NOT NULL REFERENCES groups (id),
    balance         NUMERIC(19,2) NOT NULL DEFAULT 0,
    last_journal_id BIGINT,
    version         BIGINT        NOT NULL DEFAULT 0,
    FOREIGN KEY (account_id, group_id) REFERENCES ledger_accounts (id, group_id)
);

-- Immutability (H5).
CREATE TRIGGER trg_journals_immutable BEFORE UPDATE OR DELETE ON ledger_journals
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_journals_no_truncate BEFORE TRUNCATE ON ledger_journals
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_lines_immutable BEFORE UPDATE OR DELETE ON ledger_lines
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_lines_no_truncate BEFORE TRUNCATE ON ledger_lines
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

-- Safety net behind the service's own check (spec 7.3): at commit, every journal touched in the
-- transaction has at least two lines and its debits equal its credits.
CREATE FUNCTION ledger_assert_balanced() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    target   BIGINT;
    debits   NUMERIC;
    credits  NUMERIC;
    lines    INT;
BEGIN
    -- Two statements, not a CASE: PL/pgSQL resolves every field a CASE names, and journals have no journal_id.
    IF TG_TABLE_NAME = 'ledger_journals' THEN
        target := NEW.id;
    ELSE
        target := NEW.journal_id;
    END IF;
    SELECT COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0),
           COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0),
           count(*)
    INTO debits, credits, lines
    FROM ledger_lines WHERE journal_id = target;
    IF lines < 2 OR debits <> credits THEN
        RAISE EXCEPTION 'ledger journal % is unbalanced: % debit / % credit over % line(s)', target, debits, credits, lines
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_journal_balanced AFTER INSERT ON ledger_journals
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_assert_balanced();
CREATE CONSTRAINT TRIGGER trg_lines_balanced AFTER INSERT ON ledger_lines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_assert_balanced();

-- Two-step reversals (owner decision, Phase 2; H9): requested by one officer, decided by another.
CREATE TABLE ledger_reversal_requests (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id               BIGINT      NOT NULL REFERENCES groups (id),
    journal_id             BIGINT      NOT NULL,
    reason                 TEXT        NOT NULL,
    status                 VARCHAR(15) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by           BIGINT      NOT NULL REFERENCES group_memberships (id),
    decided_by             BIGINT      REFERENCES group_memberships (id),
    decision_reason        TEXT,
    reversal_journal_id    BIGINT      REFERENCES ledger_journals (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at             TIMESTAMPTZ,
    version                BIGINT      NOT NULL DEFAULT 0,
    FOREIGN KEY (journal_id, group_id) REFERENCES ledger_journals (id, group_id)
);
-- At most one open request per journal.
CREATE UNIQUE INDEX uq_reversal_request_pending ON ledger_reversal_requests (journal_id) WHERE status = 'PENDING';

-- Privileges: append-only where it matters.
GRANT SELECT, INSERT ON ledger_accounts TO ${appRole};
GRANT SELECT, INSERT ON ledger_journals TO ${appRole};
GRANT SELECT, INSERT ON ledger_lines TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON ledger_balances TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON ledger_reversal_requests TO ${appRole};

-- Row-level security (spec 5.3), as in V5.
ALTER TABLE ledger_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_accounts FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON ledger_accounts TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON ledger_accounts TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE ledger_journals ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_journals FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON ledger_journals TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON ledger_journals TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE ledger_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_lines FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON ledger_lines TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON ledger_lines TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE ledger_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_balances FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON ledger_balances TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON ledger_balances TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE ledger_reversal_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_reversal_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON ledger_reversal_requests TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON ledger_reversal_requests TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

-- Scheduled jobs (reconciliation, obligation generation) work group by group, each inside that
-- group's tenant scope. To know which groups exist they need the ids, and only the ids.
CREATE FUNCTION active_group_ids() RETURNS SETOF BIGINT
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ikimina, pg_temp
AS $$ SELECT id FROM groups WHERE status = 'ACTIVE' ORDER BY id $$;

REVOKE ALL ON FUNCTION active_group_ids() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION active_group_ids() TO ${appRole};
