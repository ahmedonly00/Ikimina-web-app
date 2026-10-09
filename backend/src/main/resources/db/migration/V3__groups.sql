-- Phase 1: groups, memberships, invitations, office transfers, settings (spec 2.2, 6.1).
-- Every table here is tenant-owned: group_id NOT NULL (Hard Rule H4); RLS arrives in V5.
-- Rows are never deleted: people leave, invitations are revoked, requests are decided.

CREATE TABLE groups (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    name                VARCHAR(200) NOT NULL,
    registration_number VARCHAR(100),
    phone               VARCHAR(20),
    email               VARCHAR(255),
    province            VARCHAR(80),
    district            VARCHAR(80),
    sector              VARCHAR(80),
    cell                VARCHAR(80),
    village             VARCHAR(80),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_by          BIGINT       NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_groups_created_by ON groups (created_by);

GRANT SELECT, UPDATE ON groups TO ${appRole};   -- INSERT only through create_group() (V5)

-- The group's bylaws in structured form, validated by the application against schema_version.
CREATE TABLE group_settings (
    group_id       BIGINT      PRIMARY KEY REFERENCES groups (id),
    settings       JSONB       NOT NULL,
    schema_version INT         NOT NULL DEFAULT 1,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0
);

GRANT SELECT, INSERT, UPDATE ON group_settings TO ${appRole};

CREATE TABLE group_memberships (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id      BIGINT      NOT NULL REFERENCES groups (id),
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    member_number VARCHAR(30) NOT NULL,
    role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
                  CHECK (role IN ('PRESIDENT', 'TREASURER', 'SECRETARY', 'AUDITOR', 'MEMBER')),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'LEFT', 'REMOVED')),
    joined_at     TIMESTAMPTZ,
    left_at       TIMESTAMPTZ,
    left_reason   TEXT,
    version       BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (group_id, user_id),
    UNIQUE (group_id, member_number)
);
-- One active holder per officer role per group (spec 2.2).
CREATE UNIQUE INDEX uq_active_officer ON group_memberships (group_id, role)
    WHERE status = 'ACTIVE' AND role IN ('PRESIDENT', 'TREASURER', 'SECRETARY');
CREATE INDEX idx_memberships_user ON group_memberships (user_id);

GRANT SELECT, INSERT, UPDATE ON group_memberships TO ${appRole};

CREATE TABLE group_invitations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id    BIGINT       NOT NULL REFERENCES groups (id),
    phone       VARCHAR(20)  NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'AUDITOR')),
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ,
    accepted_by BIGINT       REFERENCES users (id),
    revoked_at  TIMESTAMPTZ,
    revoked_by  BIGINT       REFERENCES users (id),
    created_by  BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_invitations_group_phone ON group_invitations (group_id, phone);

GRANT SELECT, INSERT, UPDATE ON group_invitations TO ${appRole};

-- Transfer of office is two-step (spec 2.2, 17.2): the holder offers, the recipient accepts.
CREATE TABLE office_transfers (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id           BIGINT      NOT NULL REFERENCES groups (id),
    role               VARCHAR(20) NOT NULL CHECK (role IN ('PRESIDENT', 'TREASURER', 'SECRETARY')),
    from_membership_id BIGINT      NOT NULL REFERENCES group_memberships (id),
    to_membership_id   BIGINT      NOT NULL REFERENCES group_memberships (id),
    status             VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at         TIMESTAMPTZ,
    version            BIGINT      NOT NULL DEFAULT 0,
    CHECK (from_membership_id <> to_membership_id)
);
CREATE UNIQUE INDEX uq_pending_office_transfer ON office_transfers (group_id, role) WHERE status = 'PENDING';

GRANT SELECT, INSERT, UPDATE ON office_transfers TO ${appRole};

-- Financial bylaw changes need a second officer (spec 5.5, H9): one proposes, another confirms.
CREATE TABLE settings_change_requests (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    group_id               BIGINT      NOT NULL REFERENCES groups (id),
    base_version           BIGINT      NOT NULL,     -- group_settings.version the proposal was made against
    proposed_settings      JSONB       NOT NULL,
    schema_version         INT         NOT NULL,
    status                 VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'SUPERSEDED')),
    proposed_by_membership BIGINT      NOT NULL REFERENCES group_memberships (id),
    decided_by_membership  BIGINT      REFERENCES group_memberships (id),
    decision_reason        TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at             TIMESTAMPTZ,
    version                BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_settings_changes_group ON settings_change_requests (group_id, status);

GRANT SELECT, INSERT, UPDATE ON settings_change_requests TO ${appRole};
