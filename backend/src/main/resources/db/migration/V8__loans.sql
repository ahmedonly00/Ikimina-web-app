-- Phase 3: loans (spec 6.4, 9).
--
-- Additions to the spec's DDL:
--   * public ids on products, loans, repayments and disbursements (spec 6: no sequential ids in URLs);
--   * loan_products.allow_concurrent_loans: whether a member with an open loan may borrow again from
--     this product (spec 9.2 "no existing blocking loan (configurable)");
--   * loan_product_change_requests: changing a product's money terms needs a second officer
--     (owner decision, Phase 3), like bucket terms and financial bylaws;
--   * loans keep a copy of the product's money terms at request time, so a later product change never
--     alters a loan the borrower already asked for, and the schedule is computed from what was agreed;
--   * loans.cancelled_at / settled_at / rejected_at for the history the screens show;
--   * loan_repayment_allocations: which repayment paid how much of which installment, so a reversal
--     undoes exactly what it paid (owner decision, Phase 3: repayments are reversible two-step);
--   * loan_repayments.reversed_at, set when the ledger reversal of its journal is posted;
--   * idempotency_key + request_hash on disbursements and repayments: a retried request is
--     recognised before any rule that the first attempt has since made false ("already disbursed",
--     "more than is owed"), and returns the original (H7).

CREATE TABLE loan_products (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id               UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id                BIGINT        NOT NULL REFERENCES groups (id),
    name                    VARCHAR(120)  NOT NULL,
    interest_method         VARCHAR(20)   NOT NULL CHECK (interest_method IN ('FLAT', 'REDUCING_BALANCE')),
    interest_rate_percent   NUMERIC(7,4)  NOT NULL CHECK (interest_rate_percent >= 0 AND interest_rate_percent <= 100),
    interest_period         VARCHAR(10)   NOT NULL CHECK (interest_period IN ('MONTH', 'LOAN_TERM')),
    min_amount              NUMERIC(19,2) CHECK (min_amount > 0),
    max_amount              NUMERIC(19,2) CHECK (max_amount > 0),
    max_multiple_of_savings NUMERIC(7,2)  CHECK (max_multiple_of_savings > 0),
    min_term_months         INT           NOT NULL CHECK (min_term_months >= 1),
    max_term_months         INT           NOT NULL CHECK (max_term_months <= 120),
    repayment_frequency     VARCHAR(20)   NOT NULL CHECK (repayment_frequency IN ('MONTHLY', 'WEEKLY', 'AT_MATURITY')),
    grace_days              INT           NOT NULL DEFAULT 0 CHECK (grace_days >= 0 AND grace_days <= 365),
    dual_approval_threshold NUMERIC(19,2) CHECK (dual_approval_threshold > 0),
    allocation_order        JSONB         NOT NULL DEFAULT '["FINES","INTEREST","PRINCIPAL"]',
    allow_concurrent_loans  BOOLEAN       NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                 BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (group_id, name),
    UNIQUE (id, group_id),
    CHECK (max_term_months >= min_term_months),
    CHECK (min_amount IS NULL OR max_amount IS NULL OR max_amount >= min_amount)
);

CREATE TABLE loan_product_change_requests (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id               BIGINT      NOT NULL REFERENCES groups (id),
    product_id             BIGINT      NOT NULL,
    base_version           BIGINT      NOT NULL,
    proposed_terms         JSONB       NOT NULL,
    status                 VARCHAR(15) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'SUPERSEDED')),
    proposed_by_membership BIGINT      NOT NULL REFERENCES group_memberships (id),
    decided_by_membership  BIGINT      REFERENCES group_memberships (id),
    decision_reason        TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at             TIMESTAMPTZ,
    version                BIGINT      NOT NULL DEFAULT 0,
    FOREIGN KEY (product_id, group_id) REFERENCES loan_products (id, group_id)
);
CREATE UNIQUE INDEX uq_loan_product_change_pending ON loan_product_change_requests (product_id) WHERE status = 'PENDING';

CREATE TABLE loans (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id               BIGINT        NOT NULL REFERENCES groups (id),
    product_id             BIGINT        NOT NULL,
    borrower_membership_id BIGINT        NOT NULL REFERENCES group_memberships (id),
    purpose                TEXT,
    principal_amount       NUMERIC(19,2) NOT NULL CHECK (principal_amount > 0),
    term_months            INT           NOT NULL CHECK (term_months >= 1),
    -- the product's money terms when the loan was requested
    interest_method        VARCHAR(20)   NOT NULL CHECK (interest_method IN ('FLAT', 'REDUCING_BALANCE')),
    interest_rate_percent  NUMERIC(7,4)  NOT NULL CHECK (interest_rate_percent >= 0),
    interest_period        VARCHAR(10)   NOT NULL CHECK (interest_period IN ('MONTH', 'LOAN_TERM')),
    repayment_frequency    VARCHAR(20)   NOT NULL CHECK (repayment_frequency IN ('MONTHLY', 'WEEKLY', 'AT_MATURITY')),
    grace_days             INT           NOT NULL CHECK (grace_days >= 0),
    allocation_order       JSONB         NOT NULL,
    status                 VARCHAR(30)   NOT NULL CHECK (status IN ('SUBMITTED', 'PARTIALLY_COUNTERSIGNED', 'APPROVED',
                               'DISBURSED', 'OVERDUE', 'SETTLED', 'REJECTED', 'CANCELLED', 'WRITTEN_OFF')),
    requested_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    approved_at            TIMESTAMPTZ,
    disbursed_at           TIMESTAMPTZ,
    matures_on             DATE,
    settled_at             TIMESTAMPTZ,
    rejected_at            TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    required_approvals     INT           NOT NULL CHECK (required_approvals IN (1, 2)),
    rejection_reason       TEXT,
    version                BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (id, group_id),
    FOREIGN KEY (product_id, group_id) REFERENCES loan_products (id, group_id)
);
CREATE INDEX idx_loans_group_status ON loans (group_id, status);
CREATE INDEX idx_loans_borrower ON loans (group_id, borrower_membership_id);

ALTER TABLE ledger_accounts ADD CONSTRAINT fk_la_loan FOREIGN KEY (loan_id) REFERENCES loans (id);
ALTER TABLE ledger_journals ADD CONSTRAINT fk_lj_loan FOREIGN KEY (loan_id) REFERENCES loans (id);

-- Append-only (spec 9.3): a mistaken approval is handled by cancelling and re-submitting, never by editing.
CREATE TABLE loan_approvals (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id               BIGINT      NOT NULL REFERENCES groups (id),
    loan_id                BIGINT      NOT NULL,
    approver_membership_id BIGINT      NOT NULL REFERENCES group_memberships (id),
    approver_role          VARCHAR(20) NOT NULL CHECK (approver_role IN ('PRESIDENT', 'TREASURER', 'SECRETARY')),
    decision               VARCHAR(10) NOT NULL CHECK (decision IN ('APPROVE', 'REJECT')),
    comment                TEXT,
    ip_address             INET,
    user_agent             TEXT,
    decided_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (loan_id, approver_membership_id),
    FOREIGN KEY (loan_id, group_id) REFERENCES loans (id, group_id)
);
CREATE TRIGGER loan_approvals_append_only BEFORE UPDATE OR DELETE ON loan_approvals
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER loan_approvals_no_truncate BEFORE TRUNCATE ON loan_approvals
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE TABLE loan_installments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id       BIGINT        NOT NULL REFERENCES groups (id),
    loan_id        BIGINT        NOT NULL,
    installment_no INT           NOT NULL CHECK (installment_no >= 1),
    due_date       DATE          NOT NULL,
    principal_due  NUMERIC(19,2) NOT NULL CHECK (principal_due >= 0),
    interest_due   NUMERIC(19,2) NOT NULL CHECK (interest_due >= 0),
    principal_paid NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (principal_paid >= 0),
    interest_paid  NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (interest_paid >= 0),
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE')),
    version        BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (loan_id, installment_no),
    UNIQUE (id, group_id),
    FOREIGN KEY (loan_id, group_id) REFERENCES loans (id, group_id),
    CHECK (principal_paid <= principal_due),
    CHECK (interest_paid <= interest_due)
);
CREATE INDEX idx_installments_due ON loan_installments (group_id, status, due_date);

CREATE TABLE loan_disbursements (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id      BIGINT        NOT NULL REFERENCES groups (id),
    loan_id       BIGINT        NOT NULL UNIQUE,   -- one disbursement per loan (spec 9.4)
    amount        NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    method        VARCHAR(20)   NOT NULL CHECK (method IN ('CASH', 'MOMO_MANUAL', 'BANK')),
    external_ref  VARCHAR(100),
    journal_id    BIGINT        NOT NULL UNIQUE,
    business_date DATE          NOT NULL,
    disbursed_by  BIGINT        NOT NULL REFERENCES users (id),
    disbursed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    idempotency_key VARCHAR(80) NOT NULL,
    request_hash  VARCHAR(64)   NOT NULL,
    UNIQUE (group_id, idempotency_key),
    FOREIGN KEY (loan_id, group_id) REFERENCES loans (id, group_id),
    FOREIGN KEY (journal_id, group_id) REFERENCES ledger_journals (id, group_id)
);
CREATE TRIGGER loan_disbursements_append_only BEFORE UPDATE OR DELETE ON loan_disbursements
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER loan_disbursements_no_truncate BEFORE TRUNCATE ON loan_disbursements
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE TABLE loan_repayments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id       BIGINT        NOT NULL REFERENCES groups (id),
    loan_id        BIGINT        NOT NULL,
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    fines_part     NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (fines_part >= 0),
    interest_part  NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (interest_part >= 0),
    principal_part NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (principal_part >= 0),
    journal_id     BIGINT        NOT NULL UNIQUE,
    payment_method VARCHAR(20)   NOT NULL CHECK (payment_method IN ('CASH', 'MOMO_MANUAL', 'MOMO_API', 'BANK')),
    external_ref   VARCHAR(100),
    meeting_id     BIGINT,
    business_date  DATE          NOT NULL,
    recorded_by    BIGINT        NOT NULL REFERENCES users (id),
    recorded_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reversed_at    TIMESTAMPTZ,
    idempotency_key VARCHAR(80)  NOT NULL,
    request_hash   VARCHAR(64)   NOT NULL,
    UNIQUE (id, group_id),
    UNIQUE (group_id, idempotency_key),
    FOREIGN KEY (loan_id, group_id) REFERENCES loans (id, group_id),
    FOREIGN KEY (journal_id, group_id) REFERENCES ledger_journals (id, group_id),
    CHECK (fines_part + interest_part + principal_part = amount)
);
CREATE INDEX idx_repayments_loan ON loan_repayments (group_id, loan_id, business_date, id);
-- The same MoMo/bank reference cannot pay two repayments in one group (while the first stands), as for contributions.
CREATE UNIQUE INDEX uq_repayment_external_ref ON loan_repayments (group_id, external_ref)
    WHERE external_ref IS NOT NULL AND reversed_at IS NULL;

CREATE TABLE loan_repayment_allocations (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id         BIGINT        NOT NULL REFERENCES groups (id),
    repayment_id     BIGINT        NOT NULL,
    installment_id   BIGINT        NOT NULL,
    interest_amount  NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (interest_amount >= 0),
    principal_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (principal_amount >= 0),
    reversed_at      TIMESTAMPTZ,
    FOREIGN KEY (repayment_id, group_id) REFERENCES loan_repayments (id, group_id),
    FOREIGN KEY (installment_id, group_id) REFERENCES loan_installments (id, group_id),
    CHECK (interest_amount + principal_amount > 0)
);
CREATE INDEX idx_repayment_allocations_repayment ON loan_repayment_allocations (repayment_id);

GRANT SELECT, INSERT, UPDATE ON loan_products TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON loan_product_change_requests TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON loans TO ${appRole};
GRANT SELECT, INSERT ON loan_approvals TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON loan_installments TO ${appRole};
GRANT SELECT, INSERT ON loan_disbursements TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON loan_repayments TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON loan_repayment_allocations TO ${appRole};

ALTER TABLE loan_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_products FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loan_products TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loan_products TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE loan_product_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_product_change_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loan_product_change_requests TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loan_product_change_requests TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE loans ENABLE ROW LEVEL SECURITY;
ALTER TABLE loans FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loans TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loans TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE loan_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_approvals FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loan_approvals TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loan_approvals TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE loan_installments ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_installments FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loan_installments TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loan_installments TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE loan_disbursements ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_disbursements FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loan_disbursements TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loan_disbursements TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE loan_repayments ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_repayments FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loan_repayments TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loan_repayments TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE loan_repayment_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_repayment_allocations FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON loan_repayment_allocations TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON loan_repayment_allocations TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());
