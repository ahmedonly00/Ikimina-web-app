-- Mobile-money payment ingestion (record-keeping only).
--
-- The platform never holds member funds: money moves between a member and the
-- group's own provider account, and these rows record what the provider said
-- happened. Nothing here creates a balance the platform owes.
--
-- inbound_payment_events is the evidence table. When a member says "I paid and
-- it is not showing", this is what proves what the provider told us and when.

CREATE TABLE IF NOT EXISTS inbound_payment_events (
    id                bigserial PRIMARY KEY,
    provider          varchar(40)   NOT NULL,
    provider_event_id varchar(120)  NOT NULL,
    provider_reference varchar(120),
    amount            numeric(19,2),
    currency          varchar(3),
    payer_msisdn      varchar(32),
    group_id          bigint,
    member_id         bigint,
    occurred_at       timestamp(6),
    received_at       timestamp(6)  NOT NULL,
    raw_payload       text,
    processing_status varchar(20)   NOT NULL,
    processing_error  varchar(1000),
    ledger_entry_id   bigint,
    processed_at      timestamp(6)
);

-- The provider's event id is the idempotency key for the whole pipeline.
-- Providers retry aggressively; without this a retry credits a member twice.
CREATE UNIQUE INDEX IF NOT EXISTS uk_inbound_payment_provider_event
    ON inbound_payment_events (provider, provider_event_id);

CREATE INDEX IF NOT EXISTS ix_inbound_payment_status
    ON inbound_payment_events (processing_status);
CREATE INDEX IF NOT EXISTS ix_inbound_payment_received
    ON inbound_payment_events (received_at);
CREATE INDEX IF NOT EXISTS ix_inbound_payment_reference
    ON inbound_payment_events (provider_reference);

DO $$
BEGIN
    BEGIN
        ALTER TABLE inbound_payment_events
            ADD CONSTRAINT inbound_payment_status_valid
            CHECK (processing_status IN ('RECEIVED', 'APPLIED', 'FAILED', 'IGNORED'));
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'inbound_payment_status_valid already present';
    END;

    BEGIN
        -- A notification may carry no amount (a status-only callback), but a
        -- negative one is nonsense.
        ALTER TABLE inbound_payment_events
            ADD CONSTRAINT inbound_payment_amount_non_negative CHECK (amount IS NULL OR amount >= 0);
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'inbound_payment_amount_non_negative already present';
    END;

    BEGIN
        ALTER TABLE inbound_payment_events
            ADD CONSTRAINT fk_inbound_payment_ledger
            FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entries (id);
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'fk_inbound_payment_ledger already present';
    END;
END $$;

-- Normalised subscriber number, so a provider notification can be matched to a
-- member on an index. Providers report +250788123456, 250788123456 or
-- 0788123456 for the same person; the last nine digits are the comparable part.
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_normalised varchar(16);

UPDATE users
   SET phone_normalised = RIGHT(REGEXP_REPLACE(phone_number, '[^0-9]', '', 'g'), 9)
 WHERE phone_normalised IS NULL
   AND phone_number IS NOT NULL;

-- Deliberately NOT unique: two members legitimately share a handset in some
-- groups. Ingestion refuses to guess when a number matches more than one
-- member, rather than the database refusing the data.
CREATE INDEX IF NOT EXISTS ix_users_phone_normalised ON users (phone_normalised);