-- Create roles table
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create user_roles join table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Create savings_groups table
CREATE TABLE IF NOT EXISTS savings_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    admin_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_admin FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Create group_members table for many-to-many relationship
CREATE TABLE IF NOT EXISTS group_members (
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES savings_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Add role column to users table
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS role VARCHAR(50) DEFAULT 'USER';

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_group_members_user ON group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);

-- Insert default roles
INSERT INTO roles (name, description) VALUES 
    ('ROLE_SUPER_ADMIN', 'System administrator with full access'),
    ('ROLE_GROUP_ADMIN', 'Group administrator with management privileges'),
    ('ROLE_USER', 'Regular user with basic access')
ON CONFLICT (name) DO NOTHING;

-- The seed super-admin INSERT that used to live here has been removed. It:
--   * embedded a hardcoded bcrypt hash in source control;
--   * wrote role = 'SUPER_ADMIN', which violates the users_role_check
--     constraint added in V3 (the values are ROLE_-prefixed);
--   * omitted member_number and phone_number, which are NOT NULL, so it could
--     never run against the baseline schema in V1.
--
-- DataInitializer creates the super admin on first boot from
-- IKIMINA_SUPERADMIN_EMAIL / IKIMINA_SUPERADMIN_PASSWORD instead, so no
-- credential needs to live in a migration at all.
--
-- NOTE: editing an applied migration changes its Flyway checksum. A database
-- that already ran the old V2 needs `flyway repair` once before the next
-- migrate, or Flyway will refuse to start with a checksum mismatch.
