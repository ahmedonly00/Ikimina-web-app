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

-- Create a default super admin user (password: admin123 - should be changed after first login)
-- Make sure to update the password with an encoded version in production
INSERT INTO users (username, email, password, first_name, last_name, role)
SELECT 'superadmin', 'admin@ikimina.com', 'REDACTED-BCRYPT-HASH', 'System', 'Admin', 'SUPER_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'superadmin');

-- Assign super admin role to the super admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id 
FROM users u, roles r 
WHERE u.username = 'superadmin' AND r.name = 'ROLE_SUPER_ADMIN'
ON CONFLICT DO NOTHING;
