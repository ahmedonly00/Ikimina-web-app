-- Convert every monetary column from double precision to NUMERIC.
--
-- Binary floating point cannot represent decimal currency exactly. Accumulating
-- contributions across a cycle made group totals drift from the sum of member
-- balances, and in a savings group books that do not balance destroy trust.
--
-- Money  -> NUMERIC(19,2)
-- Rates  -> NUMERIC(9,4)   (interest_rate is a rate, not an amount)
--
-- Idempotent: each column is only altered when it is still a floating type, so
-- this is safe on a fresh schema where the columns are already NUMERIC.

DO $$
DECLARE
    r RECORD;
    money_cols CONSTANT text[][] := ARRAY[
        ['fines',                'amount',                       '19', '2'],
        ['loans',                'amount',                       '19', '2'],
        ['loans',                'interest_rate',                 '9', '4'],
        ['member_payouts',       'total_ubwizigame',             '19', '2'],
        ['member_payouts',       'total_ingoboka',               '19', '2'],
        ['member_payouts',       'payout_amount',                '19', '2'],
        ['payment_transactions', 'amount',                       '19', '2'],
        ['reports',              'total_savings',                '19', '2'],
        ['reports',              'total_fines',                  '19', '2'],
        ['savings',              'amount',                       '19', '2'],
        ['savings_cycles',       'total_ubwizigame_collected',   '19', '2'],
        ['savings_cycles',       'total_ingoboka_collected',     '19', '2'],
        ['savings_cycles',       'total_ubwizigame_distributed', '19', '2'],
        ['subscription_plans',   'monthly_price',                '19', '2']
    ];
    i int;
    tbl text; col text; prec text; scl text;
    current_type text;
BEGIN
    FOR i IN 1 .. array_length(money_cols, 1) LOOP
        tbl  := money_cols[i][1];
        col  := money_cols[i][2];
        prec := money_cols[i][3];
        scl  := money_cols[i][4];

        SELECT data_type INTO current_type
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = tbl AND column_name = col;

        IF current_type IS NULL THEN
            RAISE NOTICE 'skip %.% (absent)', tbl, col;
            CONTINUE;
        END IF;

        IF current_type IN ('double precision', 'real') THEN
            RAISE NOTICE 'converting %.% from % to numeric(%,%)', tbl, col, current_type, prec, scl;
            EXECUTE format(
                'ALTER TABLE %I ALTER COLUMN %I TYPE numeric(%s,%s) USING ROUND(%I::numeric, %s)',
                tbl, col, prec, scl, col, scl
            );
        ELSIF current_type = 'numeric' THEN
            -- Already numeric; make sure the precision/scale match the entities
            -- so Hibernate's schema validation agrees.
            EXECUTE format(
                'ALTER TABLE %I ALTER COLUMN %I TYPE numeric(%s,%s)',
                tbl, col, prec, scl
            );
        ELSE
            RAISE EXCEPTION 'unexpected type % on %.%', current_type, tbl, col;
        END IF;
    END LOOP;
END $$;

-- Amounts are never negative. A negative contribution or payout is a data
-- error, not a legitimate correction - corrections belong in the ledger.
ALTER TABLE savings  DROP CONSTRAINT IF EXISTS savings_amount_non_negative;
ALTER TABLE savings  ADD CONSTRAINT savings_amount_non_negative CHECK (amount >= 0);

ALTER TABLE fines    DROP CONSTRAINT IF EXISTS fines_amount_non_negative;
ALTER TABLE fines    ADD CONSTRAINT fines_amount_non_negative CHECK (amount >= 0);

ALTER TABLE loans    DROP CONSTRAINT IF EXISTS loans_amount_non_negative;
ALTER TABLE loans    ADD CONSTRAINT loans_amount_non_negative CHECK (amount >= 0);

ALTER TABLE loans    DROP CONSTRAINT IF EXISTS loans_interest_rate_non_negative;
ALTER TABLE loans    ADD CONSTRAINT loans_interest_rate_non_negative CHECK (interest_rate >= 0);

ALTER TABLE member_payouts DROP CONSTRAINT IF EXISTS member_payouts_amounts_non_negative;
ALTER TABLE member_payouts ADD CONSTRAINT member_payouts_amounts_non_negative
    CHECK (total_ubwizigame >= 0 AND total_ingoboka >= 0 AND payout_amount >= 0);

-- Optimistic-lock columns for the two tables where concurrent admin actions
-- would otherwise lose an update.
ALTER TABLE savings_cycles  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE member_payouts  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
