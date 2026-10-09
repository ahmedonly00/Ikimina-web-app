-- Phase 1: identity (spec 6.1, 16.1). Platform-level tables: no group_id, no RLS.

CREATE TABLE users (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    -- E.164. Rwanda numbers are normalised to +2507XXXXXXXX by the application.
    phone                  VARCHAR(20)  NOT NULL UNIQUE CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    phone_verified_at      TIMESTAMPTZ,
    email                  VARCHAR(255) UNIQUE,
    password_hash          VARCHAR(255) NOT NULL,                  -- Argon2id
    full_name              VARCHAR(200) NOT NULL,
    locale                 VARCHAR(5)   NOT NULL DEFAULT 'rw' CHECK (locale IN ('en', 'rw')),
    platform_role          VARCHAR(30)  NOT NULL DEFAULT 'USER' CHECK (platform_role IN ('USER', 'PLATFORM_ADMIN')),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    failed_logins          INT          NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    -- Access tokens issued before this instant are rejected (password reset, forced sign-out).
    credentials_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Spec 16.8: explicit acceptance of terms/privacy at registration, with the version accepted.
    terms_accepted_at      TIMESTAMPTZ  NOT NULL,
    terms_version          VARCHAR(40)  NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0
);

GRANT SELECT, INSERT, UPDATE ON users TO ${appRole};

-- Rotating refresh tokens with reuse detection: presenting a token that was already
-- rotated or revoked revokes its whole family (spec 16.1). Only a SHA-256 hash is stored.
CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    family_id  UUID         NOT NULL,
    auth_time  TIMESTAMPTZ  NOT NULL,        -- when the user last proved their password in this family
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

GRANT SELECT, INSERT, UPDATE ON refresh_tokens TO ${appRole};

-- One-time codes sent by SMS (spec 16.1: 6 digits, 5-minute expiry, at most 5 attempts).
-- A registration's account is only created once its code is verified; until then the
-- pending details (name, locale, Argon2 password hash) wait in payload. The code itself
-- is stored only as a keyed hash.
CREATE TABLE otp_challenges (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone         VARCHAR(20) NOT NULL,
    purpose       VARCHAR(20) NOT NULL CHECK (purpose IN ('REGISTRATION', 'PASSWORD_RESET')),
    code_hash     VARCHAR(64) NOT NULL,
    payload       JSONB,
    attempts      INT         NOT NULL DEFAULT 0,
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_challenges_lookup ON otp_challenges (phone, purpose, id DESC);
CREATE INDEX idx_otp_challenges_expiry ON otp_challenges (expires_at);

-- DELETE: expired challenges (which may hold a pending password hash) are purged.
GRANT SELECT, INSERT, UPDATE, DELETE ON otp_challenges TO ${appRole};

-- Fixed-window rate-limit counters (spec 16.5). Shared by every instance, no Redis needed.
-- bucket_key is a SHA-256 of limit name + subject, so no phone number or IP is stored.
CREATE TABLE rate_limit_counters (
    bucket_key   VARCHAR(64) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    hits         INT         NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bucket_key, window_start)
);
CREATE INDEX idx_rate_limit_counters_expiry ON rate_limit_counters (expires_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON rate_limit_counters TO ${appRole};
