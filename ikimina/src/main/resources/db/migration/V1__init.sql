-- Baseline schema.
--
-- Generated from the JPA entities (Hibernate ddl-auto=create against
-- PostgreSQL 18, then pg_dump --schema-only) so that ddl-auto=validate agrees
-- with it exactly. The migrations previously started at V2, leaving Hibernate
-- to invent the schema at runtime via ddl-auto=update - which lets it silently
-- alter production tables.
--
-- Safe on both a fresh and an existing database:
--   fresh    - Flyway applies this, then V2..V6
--   existing - Flyway baselines at version 1 and skips this file; V2/V3 were
--              already applied historically and V4..V6 carry the schema forward.
-- Every statement is still guarded, so re-running is harmless.

CREATE TABLE IF NOT EXISTS audit_logs (
    created_at timestamp(6) without time zone NOT NULL,
    entity_id bigint NOT NULL,
    id bigint NOT NULL,
    performed_by bigint NOT NULL,
    action character varying(255) NOT NULL,
    description text,
    entity_type character varying(255) NOT NULL,
    ip_address character varying(255),
    module character varying(255) NOT NULL,
    new_value text,
    old_value text,
    performed_by_name character varying(255) NOT NULL,
    performed_by_role character varying(255) NOT NULL,
    user_agent text
);
CREATE SEQUENCE IF NOT EXISTS audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE audit_logs_id_seq OWNED BY audit_logs.id;
CREATE TABLE IF NOT EXISTS fines (
    amount numeric(19,2) NOT NULL,
    date date NOT NULL,
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    reason character varying(255) NOT NULL
);
CREATE SEQUENCE IF NOT EXISTS fines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE fines_id_seq OWNED BY fines.id;
CREATE TABLE IF NOT EXISTS group_members (
    group_id bigint NOT NULL,
    user_id bigint NOT NULL
);
CREATE TABLE IF NOT EXISTS group_subscriptions (
    auto_renew boolean NOT NULL,
    end_date date NOT NULL,
    grace_period_end date,
    last_payment_date date,
    start_date date NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    group_id bigint NOT NULL,
    id bigint NOT NULL,
    manually_activated_by bigint,
    plan_id bigint NOT NULL,
    suspended_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    manual_activation_reason character varying(255),
    status character varying(255) NOT NULL,
    suspended_reason character varying(255),
    CONSTRAINT group_subscriptions_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'GRACE_PERIOD'::character varying, 'SUSPENDED'::character varying, 'CANCELLED'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS group_subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE group_subscriptions_id_seq OWNED BY group_subscriptions.id;
CREATE TABLE IF NOT EXISTS ledger_entries (
    amount numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    occurred_on date NOT NULL,
    created_by bigint NOT NULL,
    group_id bigint NOT NULL,
    id bigint NOT NULL,
    member_id bigint,
    recorded_at timestamp(6) without time zone NOT NULL,
    reversal_of bigint,
    source_id bigint,
    direction character varying(10) NOT NULL,
    entry_type character varying(40) NOT NULL,
    source_type character varying(40),
    idempotency_key character varying(120) NOT NULL,
    description character varying(500),
    CONSTRAINT ledger_entries_direction_check CHECK (((direction)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[]))),
    CONSTRAINT ledger_entries_entry_type_check CHECK (((entry_type)::text = ANY ((ARRAY['SAVINGS_CONTRIBUTION'::character varying, 'FINE_CHARGED'::character varying, 'FINE_PAID'::character varying, 'LOAN_DISBURSED'::character varying, 'LOAN_REPAID'::character varying, 'INTEREST_ACCRUED'::character varying, 'PAYOUT'::character varying, 'SUBSCRIPTION_PAYMENT'::character varying, 'ADJUSTMENT'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS ledger_entries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE ledger_entries_id_seq OWNED BY ledger_entries.id;
CREATE TABLE IF NOT EXISTS loans (
    amount numeric(19,2) NOT NULL,
    due_date date NOT NULL,
    interest_rate numeric(9,4) NOT NULL,
    request_date date NOT NULL,
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT loans_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'PAID'::character varying, 'OVERDUE'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS loans_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE loans_id_seq OWNED BY loans.id;
CREATE TABLE IF NOT EXISTS member_payouts (
    created_at date NOT NULL,
    paid_at date,
    payout_amount numeric(19,2) NOT NULL,
    total_ingoboka numeric(19,2) NOT NULL,
    total_ubwizigame numeric(19,2) NOT NULL,
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    savings_cycle_id bigint NOT NULL,
    version bigint NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT member_payouts_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'FAILED'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS member_payouts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE member_payouts_id_seq OWNED BY member_payouts.id;
CREATE TABLE IF NOT EXISTS payment_transactions (
    amount numeric(19,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    group_id bigint NOT NULL,
    id bigint NOT NULL,
    payment_date timestamp(6) without time zone NOT NULL,
    processed_at timestamp(6) without time zone,
    processed_by bigint,
    subscription_id bigint NOT NULL,
    currency character varying(255) NOT NULL,
    external_reference character varying(255),
    failure_reason character varying(255),
    notes character varying(255),
    payment_method character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    transaction_id character varying(255) NOT NULL,
    CONSTRAINT payment_transactions_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['MOBILE_MONEY'::character varying, 'BANK_TRANSFER'::character varying, 'CASH'::character varying, 'MANUAL_OVERRIDE'::character varying])::text[]))),
    CONSTRAINT payment_transactions_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS payment_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE payment_transactions_id_seq OWNED BY payment_transactions.id;
CREATE TABLE IF NOT EXISTS reports (
    from_date date NOT NULL,
    generated_on date NOT NULL,
    to_date date NOT NULL,
    total_fines numeric(19,2) NOT NULL,
    total_savings numeric(19,2) NOT NULL,
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    period character varying(255) NOT NULL,
    CONSTRAINT reports_period_check CHECK (((period)::text = ANY ((ARRAY['WEEKLY'::character varying, 'MONTHLY'::character varying, 'YEARLY'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE reports_id_seq OWNED BY reports.id;
CREATE TABLE IF NOT EXISTS savings (
    amount numeric(19,2) NOT NULL,
    date date NOT NULL,
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT savings_type_check CHECK (((type)::text = ANY ((ARRAY['INGOBOKA'::character varying, 'UBWIZIGAME'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS savings_cycles (
    created_at date NOT NULL,
    distributed_at date,
    end_date date NOT NULL,
    start_date date NOT NULL,
    total_ingoboka_collected numeric(19,2),
    total_ubwizigame_collected numeric(19,2),
    total_ubwizigame_distributed numeric(19,2),
    id bigint NOT NULL,
    savings_group_id bigint NOT NULL,
    version bigint NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT savings_cycles_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'DISTRIBUTED'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS savings_cycles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE savings_cycles_id_seq OWNED BY savings_cycles.id;
CREATE TABLE IF NOT EXISTS savings_groups (
    is_active boolean NOT NULL,
    is_suspended boolean NOT NULL,
    admin_id bigint,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    suspended_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    description character varying(1000),
    name character varying(255) NOT NULL,
    suspension_reason character varying(255)
);
CREATE SEQUENCE IF NOT EXISTS savings_groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE savings_groups_id_seq OWNED BY savings_groups.id;
CREATE SEQUENCE IF NOT EXISTS savings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE savings_id_seq OWNED BY savings.id;
CREATE TABLE IF NOT EXISTS subscription_plans (
    billing_cycle_days integer NOT NULL,
    grace_period_days integer NOT NULL,
    is_active boolean NOT NULL,
    max_members integer,
    monthly_price numeric(19,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    currency character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL
);
CREATE SEQUENCE IF NOT EXISTS subscription_plans_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE subscription_plans_id_seq OWNED BY subscription_plans.id;
CREATE TABLE IF NOT EXISTS users (
    is_active boolean,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone,
    password character varying(120) NOT NULL,
    email character varying(255) NOT NULL,
    first_name character varying(255) NOT NULL,
    full_name character varying(255),
    last_name character varying(255) NOT NULL,
    member_number character varying(255) NOT NULL,
    phone_number character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['ROLE_SUPER_ADMIN'::character varying, 'ROLE_GROUP_ADMIN'::character varying, 'ROLE_USER'::character varying])::text[])))
);
CREATE SEQUENCE IF NOT EXISTS users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE users_id_seq OWNED BY users.id;
CREATE INDEX IF NOT EXISTS ix_ledger_group_date ON ledger_entries USING btree (group_id, occurred_on);
CREATE INDEX IF NOT EXISTS ix_ledger_member_date ON ledger_entries USING btree (member_id, occurred_on);
CREATE INDEX IF NOT EXISTS ix_ledger_source ON ledger_entries USING btree (source_type, source_id);

-- Identity defaults (idempotent).
ALTER TABLE ONLY audit_logs ALTER COLUMN id SET DEFAULT nextval('audit_logs_id_seq'::regclass);
ALTER TABLE ONLY fines ALTER COLUMN id SET DEFAULT nextval('fines_id_seq'::regclass);
ALTER TABLE ONLY group_subscriptions ALTER COLUMN id SET DEFAULT nextval('group_subscriptions_id_seq'::regclass);
ALTER TABLE ONLY ledger_entries ALTER COLUMN id SET DEFAULT nextval('ledger_entries_id_seq'::regclass);
ALTER TABLE ONLY loans ALTER COLUMN id SET DEFAULT nextval('loans_id_seq'::regclass);
ALTER TABLE ONLY member_payouts ALTER COLUMN id SET DEFAULT nextval('member_payouts_id_seq'::regclass);
ALTER TABLE ONLY payment_transactions ALTER COLUMN id SET DEFAULT nextval('payment_transactions_id_seq'::regclass);
ALTER TABLE ONLY reports ALTER COLUMN id SET DEFAULT nextval('reports_id_seq'::regclass);
ALTER TABLE ONLY savings ALTER COLUMN id SET DEFAULT nextval('savings_id_seq'::regclass);
ALTER TABLE ONLY savings_cycles ALTER COLUMN id SET DEFAULT nextval('savings_cycles_id_seq'::regclass);
ALTER TABLE ONLY savings_groups ALTER COLUMN id SET DEFAULT nextval('savings_groups_id_seq'::regclass);
ALTER TABLE ONLY subscription_plans ALTER COLUMN id SET DEFAULT nextval('subscription_plans_id_seq'::regclass);
ALTER TABLE ONLY users ALTER COLUMN id SET DEFAULT nextval('users_id_seq'::regclass);

-- Constraints. PostgreSQL has no ADD CONSTRAINT ... IF NOT EXISTS, so
-- duplicates are swallowed rather than failing the migration.
DO $$
DECLARE
    stmt text;
    stmts text[] := ARRAY[
        'ALTER TABLE ONLY audit_logs ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY fines ADD CONSTRAINT fines_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY group_members ADD CONSTRAINT group_members_pkey PRIMARY KEY (group_id, user_id)',
        'ALTER TABLE ONLY group_subscriptions ADD CONSTRAINT group_subscriptions_group_id_key UNIQUE (group_id)',
        'ALTER TABLE ONLY group_subscriptions ADD CONSTRAINT group_subscriptions_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY ledger_entries ADD CONSTRAINT ledger_entries_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY loans ADD CONSTRAINT loans_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY member_payouts ADD CONSTRAINT member_payouts_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY payment_transactions ADD CONSTRAINT payment_transactions_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY payment_transactions ADD CONSTRAINT payment_transactions_transaction_id_key UNIQUE (transaction_id)',
        'ALTER TABLE ONLY reports ADD CONSTRAINT reports_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY savings_cycles ADD CONSTRAINT savings_cycles_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY savings_groups ADD CONSTRAINT savings_groups_name_key UNIQUE (name)',
        'ALTER TABLE ONLY savings_groups ADD CONSTRAINT savings_groups_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY savings ADD CONSTRAINT savings_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY subscription_plans ADD CONSTRAINT subscription_plans_name_key UNIQUE (name)',
        'ALTER TABLE ONLY subscription_plans ADD CONSTRAINT subscription_plans_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY ledger_entries ADD CONSTRAINT uk_ledger_idempotency_key UNIQUE (idempotency_key)',
        'ALTER TABLE ONLY users ADD CONSTRAINT users_email_key UNIQUE (email)',
        'ALTER TABLE ONLY users ADD CONSTRAINT users_member_number_key UNIQUE (member_number)',
        'ALTER TABLE ONLY users ADD CONSTRAINT users_phone_number_key UNIQUE (phone_number)',
        'ALTER TABLE ONLY users ADD CONSTRAINT users_pkey PRIMARY KEY (id)',
        'ALTER TABLE ONLY users ADD CONSTRAINT users_username_key UNIQUE (username)',
        'ALTER TABLE ONLY reports ADD CONSTRAINT fk2o32rer9hfweeylg7x8ut8rj2 FOREIGN KEY (user_id) REFERENCES users(id)',
        'ALTER TABLE ONLY savings_groups ADD CONSTRAINT fk2qeq41xwnmd8y4xq12v01fari FOREIGN KEY (admin_id) REFERENCES users(id)',
        'ALTER TABLE ONLY member_payouts ADD CONSTRAINT fk415xpwnp23tk47x5gt3rguj29 FOREIGN KEY (member_id) REFERENCES users(id)',
        'ALTER TABLE ONLY loans ADD CONSTRAINT fk6xxlcjc0rqtn5nq28vjnx5t9d FOREIGN KEY (user_id) REFERENCES users(id)',
        'ALTER TABLE ONLY group_members ADD CONSTRAINT fk84bbxpaaw9jxx869ly45j5pgd FOREIGN KEY (group_id) REFERENCES savings_groups(id)',
        'ALTER TABLE ONLY fines ADD CONSTRAINT fk96m5dw0wvbckhnnwfeynue15y FOREIGN KEY (user_id) REFERENCES users(id)',
        'ALTER TABLE ONLY payment_transactions ADD CONSTRAINT fkb358ywtvueyo3y7u90pft9woe FOREIGN KEY (group_id) REFERENCES savings_groups(id)',
        'ALTER TABLE ONLY savings ADD CONSTRAINT fkbj5iiqpw46u7bbkb1gdpl0d25 FOREIGN KEY (user_id) REFERENCES users(id)',
        'ALTER TABLE ONLY group_subscriptions ADD CONSTRAINT fkj14xehjc02aactp2asy888y9u FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)',
        'ALTER TABLE ONLY group_members ADD CONSTRAINT fknr9qg33qt2ovmv29g4vc3gtdx FOREIGN KEY (user_id) REFERENCES users(id)',
        'ALTER TABLE ONLY payment_transactions ADD CONSTRAINT fkotb9h97y7y39wrf5guw173wfk FOREIGN KEY (subscription_id) REFERENCES group_subscriptions(id)',
        'ALTER TABLE ONLY member_payouts ADD CONSTRAINT fkq0bcqjc1yxjvrknx7a28x6wy7 FOREIGN KEY (savings_cycle_id) REFERENCES savings_cycles(id)',
        'ALTER TABLE ONLY savings_cycles ADD CONSTRAINT fkr77n1l3l3inp1opn8b4vx7tcl FOREIGN KEY (savings_group_id) REFERENCES savings_groups(id)',
        'ALTER TABLE ONLY group_subscriptions ADD CONSTRAINT fksacjk2i7mveksent4pp2k4mrt FOREIGN KEY (group_id) REFERENCES savings_groups(id)'
    ];
BEGIN
    FOREACH stmt IN ARRAY stmts LOOP
        BEGIN
            EXECUTE stmt;
        EXCEPTION
            -- A primary key that already exists raises
            -- invalid_table_definition rather than duplicate_object, so both
            -- are tolerated here.
            WHEN duplicate_table
              OR duplicate_object
              OR duplicate_column
              OR invalid_table_definition THEN
                RAISE NOTICE 'constraint already present, skipping: %', stmt;
        END;
    END LOOP;
END $$;
