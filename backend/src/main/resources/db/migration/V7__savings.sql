-- Phase 2: savings buckets, contribution obligations and contributions (spec 6.3, 8).
--
-- Additions to the spec's DDL:
--   * public ids on buckets, obligations and transactions (spec 6: no sequential ids in URLs);
--   * savings_transactions.reversed_at, set when the ledger reversal of its journal is posted;
--   * contribution_allocations: which contribution paid how much of which obligation, so a
--     reversal undoes exactly what it paid and an overpayment can carry to the next period
--     (owner decision, Phase 2);
--   * bucket_change_requests: changing a bucket's money terms needs a second officer
--     (owner decision, Phase 2), like financial bylaws.

CREATE TABLE savings_buckets (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id               BIGINT        NOT NULL REFERENCES groups (id),
    name                   VARCHAR(200)  NOT NULL,
    description            TEXT,
    bucket_type            VARCHAR(30)   NOT NULL CHECK (bucket_type IN ('SAVINGS', 'SOCIAL_FUND', 'SHARES')),
    is_mandatory           BOOLEAN       NOT NULL DEFAULT FALSE,
    minimum_contribution   NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (minimum_contribution >= 0),
    contribution_frequency VARCHAR(20)   NOT NULL
                           CHECK (contribution_frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'PER_MEETING', 'ADHOC')),
    cycle_type             VARCHAR(20)   NOT NULL CHECK (cycle_type IN ('FIXED_TERM', 'ROLLING')),
    start_date             DATE          NOT NULL,
    end_date               DATE,
    withdrawable           BOOLEAN       NOT NULL DEFAULT FALSE,
    late_penalty_rule      JSONB,        -- applied by the fines engine (Phase 4)
    status                 VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (group_id, name),
    UNIQUE (id, group_id),
    CHECK (end_date IS NULL OR end_date >= start_date),
    CHECK (cycle_type = 'ROLLING' OR end_date IS NOT NULL)
);

ALTER TABLE ledger_accounts ADD CONSTRAINT fk_la_bucket FOREIGN KEY (bucket_id) REFERENCES savings_buckets (id);

CREATE TABLE bucket_change_requests (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id               BIGINT      NOT NULL REFERENCES groups (id),
    bucket_id              BIGINT      NOT NULL,
    base_version           BIGINT      NOT NULL,
    proposed_terms         JSONB       NOT NULL,
    status                 VARCHAR(15) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'SUPERSEDED')),
    proposed_by_membership BIGINT      NOT NULL REFERENCES group_memberships (id),
    decided_by_membership  BIGINT      REFERENCES group_memberships (id),
    decision_reason        TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at             TIMESTAMPTZ,
    version                BIGINT      NOT NULL DEFAULT 0,
    FOREIGN KEY (bucket_id, group_id) REFERENCES savings_buckets (id, group_id)
);
CREATE UNIQUE INDEX uq_bucket_change_pending ON bucket_change_requests (bucket_id) WHERE status = 'PENDING';

-- What each member owes for a period (spec 8.1). Generated nightly; the unique key makes that idempotent.
CREATE TABLE contribution_obligations (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id      BIGINT        NOT NULL REFERENCES groups (id),
    bucket_id     BIGINT        NOT NULL,
    membership_id BIGINT        NOT NULL REFERENCES group_memberships (id),
    period_start  DATE          NOT NULL,
    due_date      DATE          NOT NULL,
    amount_due    NUMERIC(19,2) NOT NULL CHECK (amount_due > 0),
    amount_paid   NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    status        VARCHAR(20)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PARTIAL', 'PAID', 'OVERDUE', 'WAIVED')),
    version       BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (bucket_id, membership_id, period_start),
    UNIQUE (id, group_id),
    FOREIGN KEY (bucket_id, group_id) REFERENCES savings_buckets (id, group_id),
    CHECK (amount_paid <= amount_due),
    CHECK (due_date >= period_start)
);
CREATE INDEX idx_obligations_member ON contribution_obligations (group_id, membership_id, bucket_id, period_start);
CREATE INDEX idx_obligations_status ON contribution_obligations (group_id, status, due_date);

-- The business record of a contribution, linked to its ledger journal (spec 6.3).
CREATE TABLE savings_transactions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id       BIGINT        NOT NULL REFERENCES groups (id),
    bucket_id      BIGINT        NOT NULL,
    membership_id  BIGINT        NOT NULL REFERENCES group_memberships (id),
    txn_type       VARCHAR(20)   NOT NULL CHECK (txn_type IN ('CONTRIBUTION', 'WITHDRAWAL', 'SHARE_OUT')),
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    journal_id     BIGINT        NOT NULL UNIQUE,
    obligation_id  BIGINT        REFERENCES contribution_obligations (id),
    meeting_id     BIGINT,
    payment_method VARCHAR(20)   NOT NULL CHECK (payment_method IN ('CASH', 'MOMO_MANUAL', 'MOMO_API', 'BANK')),
    external_ref   VARCHAR(100),
    business_date  DATE          NOT NULL,
    recorded_by    BIGINT        NOT NULL REFERENCES users (id),
    recorded_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reversed_at    TIMESTAMPTZ,
    UNIQUE (id, group_id),
    FOREIGN KEY (bucket_id, group_id) REFERENCES savings_buckets (id, group_id),
    FOREIGN KEY (journal_id, group_id) REFERENCES ledger_journals (id, group_id)
);
-- Spec 8.2: the same MoMo/bank reference cannot be recorded twice in one group (while the first stands).
CREATE UNIQUE INDEX uq_savings_external_ref ON savings_transactions (group_id, external_ref)
    WHERE external_ref IS NOT NULL AND reversed_at IS NULL;
CREATE INDEX idx_savings_txn_member ON savings_transactions (group_id, membership_id, business_date, id);

CREATE TABLE contribution_allocations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id       BIGINT        NOT NULL REFERENCES groups (id),
    transaction_id BIGINT        NOT NULL,
    obligation_id  BIGINT        NOT NULL,
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reversed_at    TIMESTAMPTZ,
    FOREIGN KEY (transaction_id, group_id) REFERENCES savings_transactions (id, group_id),
    FOREIGN KEY (obligation_id, group_id) REFERENCES contribution_obligations (id, group_id)
);
CREATE INDEX idx_allocations_transaction ON contribution_allocations (transaction_id);
CREATE INDEX idx_allocations_obligation ON contribution_allocations (obligation_id);

GRANT SELECT, INSERT, UPDATE ON savings_buckets TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON bucket_change_requests TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON contribution_obligations TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON savings_transactions TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON contribution_allocations TO ${appRole};

ALTER TABLE savings_buckets ENABLE ROW LEVEL SECURITY;
ALTER TABLE savings_buckets FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON savings_buckets TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON savings_buckets TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE bucket_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE bucket_change_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON bucket_change_requests TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON bucket_change_requests TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE contribution_obligations ENABLE ROW LEVEL SECURITY;
ALTER TABLE contribution_obligations FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON contribution_obligations TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON contribution_obligations TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE savings_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE savings_transactions FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON savings_transactions TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON savings_transactions TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE contribution_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE contribution_allocations FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON contribution_allocations TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON contribution_allocations TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());
