-- Phase 3c: savings withdrawals (spec 8.3, 7.2; owner decisions, Phase 3c).
--
-- A member asks; the President or the Treasurer (never the member) approves or rejects; the
-- Treasurer records the payout, which posts the ledger journal (debit the member's savings, credit
-- group cash) and a savings_transactions row of type WITHDRAWAL. The request keeps the bylaw notice
-- in force when it was made as an earliest payout date. The payout's Idempotency-Key and request
-- hash are kept so a retry is recognised before any rule the first payout has since made false.

CREATE TABLE savings_withdrawals (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id            BIGINT        NOT NULL REFERENCES groups (id),
    membership_id       BIGINT        NOT NULL,
    bucket_id           BIGINT        NOT NULL,
    amount              NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    reason              TEXT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED', 'APPROVED', 'PAID', 'REJECTED', 'CANCELLED')),
    requested_on        DATE          NOT NULL,
    earliest_payout_on  DATE          NOT NULL,
    requested_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_by          BIGINT,
    decided_role        VARCHAR(20)   CHECK (decided_role IN ('PRESIDENT', 'TREASURER')),
    decided_at          TIMESTAMPTZ,
    decision_reason     TEXT,
    transaction_id      BIGINT        UNIQUE,
    paid_by             BIGINT        REFERENCES users (id),
    paid_at             TIMESTAMPTZ,
    pay_idempotency_key VARCHAR(80),
    pay_request_hash    VARCHAR(64),
    reversed_at         TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (group_id, pay_idempotency_key),
    FOREIGN KEY (membership_id, group_id) REFERENCES group_memberships (id, group_id),
    FOREIGN KEY (decided_by, group_id) REFERENCES group_memberships (id, group_id),
    FOREIGN KEY (bucket_id, group_id) REFERENCES savings_buckets (id, group_id),
    FOREIGN KEY (transaction_id, group_id) REFERENCES savings_transactions (id, group_id),
    CHECK (earliest_payout_on >= requested_on),
    CHECK ((status = 'PAID') = (transaction_id IS NOT NULL))
);
CREATE INDEX idx_withdrawals_member ON savings_withdrawals (group_id, membership_id, status);
CREATE INDEX idx_withdrawals_status ON savings_withdrawals (group_id, status, requested_at);

GRANT SELECT, INSERT, UPDATE ON savings_withdrawals TO ${appRole};

ALTER TABLE savings_withdrawals ENABLE ROW LEVEL SECURITY;
ALTER TABLE savings_withdrawals FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON savings_withdrawals TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON savings_withdrawals TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());
