-- Append-only ledger.
--
-- Balances are derived by summing these rows rather than stored, so any balance
-- can be explained by the entries that produced it. Rows are never updated or
-- deleted: a mistake is corrected by appending a reversing entry that points at
-- the original via reversal_of.
--
-- On a fresh database V1 already created this table, so everything here is
-- guarded. On an existing database this is where the table arrives.

CREATE TABLE IF NOT EXISTS ledger_entries (
    id              bigserial PRIMARY KEY,
    entry_type      varchar(40)     NOT NULL,
    direction       varchar(10)     NOT NULL,
    amount          numeric(19,2)   NOT NULL,
    currency        varchar(3)      NOT NULL,
    group_id        bigint          NOT NULL,
    member_id       bigint,
    occurred_on     date            NOT NULL,
    recorded_at     timestamp(6)    NOT NULL,
    idempotency_key varchar(120)    NOT NULL,
    source_type     varchar(40),
    source_id       bigint,
    reversal_of     bigint,
    created_by      bigint          NOT NULL,
    description     varchar(500)
);

-- The idempotency guarantee is this constraint, not the application check: two
-- concurrent retries can both pass a pre-check, and the loser must fail here.
CREATE UNIQUE INDEX IF NOT EXISTS uk_ledger_idempotency_key
    ON ledger_entries (idempotency_key);

CREATE INDEX IF NOT EXISTS ix_ledger_group_date  ON ledger_entries (group_id, occurred_on);
CREATE INDEX IF NOT EXISTS ix_ledger_member_date ON ledger_entries (member_id, occurred_on);
CREATE INDEX IF NOT EXISTS ix_ledger_source      ON ledger_entries (source_type, source_id);

-- An entry may only be reversed once.
CREATE UNIQUE INDEX IF NOT EXISTS uk_ledger_reversal_of
    ON ledger_entries (reversal_of) WHERE reversal_of IS NOT NULL;

DO $$
BEGIN
    BEGIN
        -- Direction carries the sign, so the amount itself is always positive.
        ALTER TABLE ledger_entries
            ADD CONSTRAINT ledger_amount_positive CHECK (amount > 0);
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'ledger_amount_positive already present';
    END;

    BEGIN
        ALTER TABLE ledger_entries
            ADD CONSTRAINT ledger_direction_valid CHECK (direction IN ('CREDIT', 'DEBIT'));
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'ledger_direction_valid already present';
    END;

    BEGIN
        ALTER TABLE ledger_entries
            ADD CONSTRAINT fk_ledger_reversal_of
            FOREIGN KEY (reversal_of) REFERENCES ledger_entries (id);
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'fk_ledger_reversal_of already present';
    END;

    BEGIN
        ALTER TABLE ledger_entries
            ADD CONSTRAINT fk_ledger_group FOREIGN KEY (group_id) REFERENCES savings_groups (id);
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'fk_ledger_group already present';
    END;

    BEGIN
        ALTER TABLE ledger_entries
            ADD CONSTRAINT fk_ledger_member FOREIGN KEY (member_id) REFERENCES users (id);
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'fk_ledger_member already present';
    END;
END $$;

-- Append-only enforced in the database, not just in the service: a stray UPDATE
-- or DELETE from a console or a future code path must not be able to rewrite
-- financial history.
CREATE OR REPLACE FUNCTION ledger_entries_are_immutable()
RETURNS trigger AS $ledger_immutable$
BEGIN
    RAISE EXCEPTION
        'ledger_entries is append-only; correct entry % by appending a reversal',
        COALESCE(OLD.id, NEW.id);
END;
$ledger_immutable$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ledger_no_update ON ledger_entries;
CREATE TRIGGER trg_ledger_no_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_are_immutable();

DROP TRIGGER IF EXISTS trg_ledger_no_delete ON ledger_entries;
CREATE TRIGGER trg_ledger_no_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_are_immutable();