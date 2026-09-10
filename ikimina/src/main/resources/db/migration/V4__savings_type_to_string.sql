-- Savings.type had no @Enumerated mapping, so JPA persisted it as an ORDINAL
-- integer (INGOBOKA=0, UBWIZIGAME=1). Any reordering of the enum would have
-- silently reinterpreted every historical savings row. Convert the column to
-- the enum name.
--
-- Idempotent and safe to run against a database where the column is already
-- textual (e.g. a fresh Hibernate-generated schema).

DO $$
DECLARE
    col_type text;
BEGIN
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_name = 'savings' AND column_name = 'type';

    IF col_type IS NULL THEN
        RAISE NOTICE 'savings.type not present; nothing to convert';
        RETURN;
    END IF;

    IF col_type IN ('smallint', 'integer', 'bigint') THEN
        RAISE NOTICE 'Converting savings.type from % to varchar', col_type;

        ALTER TABLE savings ADD COLUMN type_str VARCHAR(20);

        UPDATE savings SET type_str = CASE type
            WHEN 0 THEN 'INGOBOKA'
            WHEN 1 THEN 'UBWIZIGAME'
        END;

        -- Refuse to continue if any row failed to map, rather than silently
        -- writing NULL over financial classification data.
        IF EXISTS (SELECT 1 FROM savings WHERE type IS NOT NULL AND type_str IS NULL) THEN
            RAISE EXCEPTION 'Unmapped savings.type ordinal found; aborting migration';
        END IF;

        ALTER TABLE savings DROP COLUMN type;
        ALTER TABLE savings RENAME COLUMN type_str TO type;
    ELSE
        RAISE NOTICE 'savings.type is already %; skipping conversion', col_type;
    END IF;
END $$;

ALTER TABLE savings ALTER COLUMN type SET NOT NULL;

ALTER TABLE savings DROP CONSTRAINT IF EXISTS savings_type_check;
ALTER TABLE savings
    ADD CONSTRAINT savings_type_check CHECK (type IN ('INGOBOKA', 'UBWIZIGAME'));
