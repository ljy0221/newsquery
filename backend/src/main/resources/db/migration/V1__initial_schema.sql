-- Phase 5.1: Initial PostgreSQL Schema
-- Users table
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Saved queries table
CREATE TABLE IF NOT EXISTS saved_queries (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    nql TEXT NOT NULL,
    name VARCHAR(255),
    description TEXT,
    is_favorite BOOLEAN NOT NULL DEFAULT false,
    execution_count INTEGER NOT NULL DEFAULT 0,
    avg_response_time_ms DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_executed_at TIMESTAMP,
    CONSTRAINT fk_saved_queries_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_saved_queries_user_id ON saved_queries(user_id);

-- Query history table
CREATE TABLE IF NOT EXISTS query_history (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    nql TEXT NOT NULL,
    response_time_ms DOUBLE PRECISION NOT NULL,
    total_hits BIGINT NOT NULL,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_query_history_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_query_history_user_id ON query_history(user_id);
CREATE INDEX IF NOT EXISTS idx_query_history_executed_at ON query_history(executed_at);

-- Keyword subscriptions table
CREATE TABLE IF NOT EXISTS keyword_subscriptions (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_keyword_subscriptions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uk_keyword_subscriptions UNIQUE (user_id, keyword)
);

CREATE INDEX IF NOT EXISTS idx_keyword_subscriptions_user_id ON keyword_subscriptions(user_id);

-- User notification preferences table
CREATE TABLE IF NOT EXISTS user_notification_preferences (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    push_enabled BOOLEAN NOT NULL DEFAULT true,
    console_enabled BOOLEAN NOT NULL DEFAULT true,
    quiet_hours_start VARCHAR(5),
    quiet_hours_end VARCHAR(5),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_notification_preferences_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Notification rule configurations table (Phase 7)
CREATE TABLE IF NOT EXISTS notification_rule_configs (
    id VARCHAR(255) PRIMARY KEY,
    rule_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    condition_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
