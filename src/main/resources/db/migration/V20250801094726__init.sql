-- Parent table: Users (common part)
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY
);

-- Subtype: Pending users (not yet activated)
CREATE TABLE pending_users (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id) ON DELETE RESTRICT,
    activation_token UUID NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Subtype: Active users
CREATE TABLE active_users (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id) ON DELETE RESTRICT,
    activated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Subtype: Deleted users (no personal information retained)
CREATE TABLE deleted_users (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id) ON DELETE RESTRICT,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User profile information (separate table)
CREATE TABLE user_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id) ON DELETE RESTRICT,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Admin users (linked to active_users)
CREATE TABLE admin_users (
    user_id BIGINT PRIMARY KEY REFERENCES active_users(user_id) ON DELETE RESTRICT
);

-- User email addresses (multiple emails per user allowed)
CREATE TABLE user_emails (
    email VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Primary email addresses (separate table)
CREATE TABLE user_primary_emails (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id) ON DELETE RESTRICT,
    email VARCHAR(255) NOT NULL REFERENCES user_emails(email) ON DELETE RESTRICT
);

-- Event: User self-deletion
CREATE TABLE user_deletion_events (
    deletion_id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    -- Snapshot of user information at deletion time (JSON format for flexibility)
    user_info_at_deletion JSONB NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Event: User BAN (deletion by admin)
CREATE TABLE user_ban_events (
    ban_id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    admin_user_id BIGINT REFERENCES users(user_id), -- Admin who executed the BAN
    -- Snapshot of user information at BAN time (JSON format for flexibility)
    user_info_at_ban JSONB NOT NULL,
    ban_reason TEXT,
    banned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index settings
CREATE INDEX idx_pending_users_activation_token ON pending_users(activation_token);
CREATE INDEX idx_pending_users_expires_at ON pending_users(expires_at);

CREATE INDEX idx_active_users_activated_at ON active_users(activated_at);

CREATE INDEX idx_deleted_users_deleted_at ON deleted_users(deleted_at);

CREATE INDEX idx_user_profiles_username ON user_profiles(username);
CREATE INDEX idx_user_profiles_display_name ON user_profiles(display_name);

CREATE INDEX idx_user_emails_user_id ON user_emails(user_id);

CREATE INDEX idx_user_primary_emails_email ON user_primary_emails(email);

CREATE INDEX idx_user_deletion_events_user_id ON user_deletion_events(user_id);
CREATE INDEX idx_user_deletion_events_deleted_at ON user_deletion_events(deleted_at);
CREATE INDEX idx_user_deletion_events_user_info ON user_deletion_events USING GIN(user_info_at_deletion);

CREATE INDEX idx_user_ban_events_user_id ON user_ban_events(user_id);
CREATE INDEX idx_user_ban_events_banned_at ON user_ban_events(banned_at);
CREATE INDEX idx_user_ban_events_admin_user_id ON user_ban_events(admin_user_id);
CREATE INDEX idx_user_ban_events_user_info ON user_ban_events USING GIN(user_info_at_ban);