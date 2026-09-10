-- Consent records (Law 058/2021).
--
-- Consent must be specific, informed, and demonstrable by the controller. That
-- means one row per purpose per decision, carrying the version of the notice
-- the member actually saw - so a later policy change cannot silently
-- re-interpret an old agreement.
--
-- Append-only: a withdrawal is a new row with granted = false, never an update
-- or a delete, so the history of what was agreed and when survives.

CREATE TABLE IF NOT EXISTS consent_records (
    id             bigserial PRIMARY KEY,
    user_id        bigint       NOT NULL,
    purpose        varchar(40)  NOT NULL,
    policy_version varchar(20)  NOT NULL,
    granted        boolean      NOT NULL,
    recorded_at    timestamp(6) NOT NULL,
    ip_address     varchar(45),
    user_agent     varchar(500)
);

CREATE INDEX IF NOT EXISTS ix_consent_user_purpose ON consent_records (user_id, purpose);
CREATE INDEX IF NOT EXISTS ix_consent_recorded     ON consent_records (recorded_at);

DO $$
BEGIN
    BEGIN
        ALTER TABLE consent_records
            ADD CONSTRAINT consent_purpose_valid
            CHECK (purpose IN ('SERVICE_OPERATION', 'SMS_NOTIFICATIONS', 'GROUP_VISIBILITY', 'ANALYTICS'));
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'consent_purpose_valid already present';
    END;

    BEGIN
        ALTER TABLE consent_records
            ADD CONSTRAINT fk_consent_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'fk_consent_user already present';
    END;
END $$;

-- Erasure is anonymisation, not deletion: a member's right to erasure and the
-- group's obligation to keep financial records both apply, so personal fields
-- are overwritten while ledger rows stay intact and still balance.
--
-- Recorded here so the state is queryable for a compliance report rather than
-- inferred from placeholder values.
ALTER TABLE users ADD COLUMN IF NOT EXISTS erased_at timestamp(6);

COMMENT ON COLUMN users.erased_at IS
    'When the member exercised their right to erasure. Personal fields are '
    'anonymised from that point; financial history is retained.';