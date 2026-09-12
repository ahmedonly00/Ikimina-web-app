-- Controlled ways into a savings group.
--
-- Registration was public and took savingsGroupId on trust, so anyone who
-- guessed a group id could insert themselves into that group's member roster.
-- A savings group holds money and its membership is the basis for every
-- balance, so joining one has to be authorised by somebody already inside it.
--
-- Three routes in, and no others:
--   1. a group administrator adds the member directly;
--   2. the person presents the group's invite code;
--   3. the person asks, and an administrator approves.
--
-- In none of these does an unauthenticated caller choose the group by id.

-- 1. Invite code.
--
-- Nullable: existing groups have none until an admin generates one, and a null
-- code must never match an absent or empty submitted code. Unique so a code
-- identifies exactly one group - the group is resolved FROM the code, rather
-- than the code being checked against a group the caller named.
ALTER TABLE savings_groups ADD COLUMN IF NOT EXISTS join_code varchar(32);

DO $$
BEGIN
    BEGIN
        ALTER TABLE savings_groups ADD CONSTRAINT uq_savings_groups_join_code UNIQUE (join_code);
    EXCEPTION
        WHEN duplicate_table THEN NULL;
        WHEN duplicate_object THEN NULL;
    END;
END $$;

-- 3. Requests awaiting a decision.
CREATE TABLE IF NOT EXISTS membership_requests (
    id               bigserial PRIMARY KEY,
    user_id          bigint       NOT NULL,
    savings_group_id bigint       NOT NULL,
    status           varchar(20)  NOT NULL,
    requested_at     timestamp(6) NOT NULL,
    decided_at       timestamp(6),
    decided_by       bigint,
    decision_note    varchar(500)
);

DO $$
BEGIN
    BEGIN
        ALTER TABLE membership_requests
            ADD CONSTRAINT fk_membership_request_user
            FOREIGN KEY (user_id) REFERENCES users (id);
    EXCEPTION
        WHEN duplicate_table THEN NULL;
        WHEN duplicate_object THEN NULL;
    END;

    BEGIN
        ALTER TABLE membership_requests
            ADD CONSTRAINT fk_membership_request_group
            FOREIGN KEY (savings_group_id) REFERENCES savings_groups (id);
    EXCEPTION
        WHEN duplicate_table THEN NULL;
        WHEN duplicate_object THEN NULL;
    END;

    BEGIN
        ALTER TABLE membership_requests
            ADD CONSTRAINT membership_request_status_valid
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));
    EXCEPTION
        WHEN duplicate_table THEN NULL;
        WHEN duplicate_object THEN NULL;
    END;
END $$;

-- One live request per person per group. A partial unique index rather than a
-- plain one, so a rejected request does not block the person from asking again
-- later, while a second PENDING row cannot be created by double-submitting.
CREATE UNIQUE INDEX IF NOT EXISTS uq_membership_request_pending
    ON membership_requests (user_id, savings_group_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_membership_request_group_status
    ON membership_requests (savings_group_id, status);
