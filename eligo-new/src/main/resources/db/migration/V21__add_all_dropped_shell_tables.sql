-- V21: Empty shell tables for all dropped-but-still-referenced entities
-- These return empty data; actual business logic is disabled or deferred.

CREATE TABLE IF NOT EXISTS agreements (
    id BIGINT NOT NULL PRIMARY KEY,
    agreement_type TINYINT NULL,
    version_code VARCHAR(64) NULL,
    title VARCHAR(255) NULL,
    content LONGTEXT NULL,
    content_hash BINARY(32) NULL,
    status TINYINT NULL,
    requires_reconsent TINYINT NULL,
    scheduled_at DATETIME(3) NULL,
    effective_at DATETIME(3) NULL,
    retired_at DATETIME(3) NULL,
    created_by_admin_id BIGINT NULL,
    version INT NULL,
    created_at DATETIME(3) NULL,
    updated_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agreements (shell)';

CREATE TABLE IF NOT EXISTS agreement_consents (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    agreement_id BIGINT NULL,
    agreed_at DATETIME(3) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agreement_consents (shell)';

CREATE TABLE IF NOT EXISTS user_data_requests (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    request_type TINYINT NULL,
    status TINYINT NULL,
    requested_at DATETIME(3) NULL,
    execute_after DATETIME(3) NULL,
    result_file_id BIGINT NULL,
    result_expires_at DATETIME(3) NULL,
    retry_count INT NULL,
    failure_code VARCHAR(64) NULL,
    last_error_summary VARCHAR(512) NULL,
    processed_at DATETIME(3) NULL,
    version INT NULL,
    created_at DATETIME(3) NULL,
    updated_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user_data_requests (shell)';

CREATE TABLE IF NOT EXISTS user_data_request_events (
    id BIGINT NOT NULL PRIMARY KEY,
    request_id BIGINT NULL,
    event_type VARCHAR(64) NULL,
    from_status TINYINT NULL,
    to_status TINYINT NULL,
    actor_type TINYINT NULL,
    actor_id BIGINT NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user_data_request_events (shell)';

CREATE TABLE IF NOT EXISTS account_security_events (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    device_id BIGINT NULL,
    session_id BIGINT NULL,
    event_type VARCHAR(64) NULL,
    severity TINYINT NULL,
    region_code VARCHAR(32) NULL,
    notified_at DATETIME(3) NULL,
    occurred_at DATETIME(3) NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='account_security_events (shell)';

CREATE TABLE IF NOT EXISTS user_profile_change_logs (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    field_code VARCHAR(64) NULL,
    source_type TINYINT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user_profile_change_logs (shell)';

CREATE TABLE IF NOT EXISTS interest_tags (
    id BIGINT NOT NULL PRIMARY KEY,
    tag_code VARCHAR(64) NULL,
    tag_name VARCHAR(64) NULL,
    sort_order INT NULL,
    status TINYINT NULL,
    created_at DATETIME(3) NULL,
    updated_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='interest_tags (shell)';

CREATE TABLE IF NOT EXISTS user_interest_tags (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    interest_tag_id BIGINT NULL,
    selected_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user_interest_tags (shell)';

CREATE TABLE IF NOT EXISTS organization_follows (
    id BIGINT NOT NULL PRIMARY KEY,
    follower_user_id BIGINT NULL,
    organization_id BIGINT NULL,
    followed_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='organization_follows (shell)';

CREATE TABLE IF NOT EXISTS post_status_events (
    id BIGINT NOT NULL PRIMARY KEY,
    post_id BIGINT NULL,
    from_status TINYINT NULL,
    to_status TINYINT NULL,
    actor_type TINYINT NULL,
    actor_user_id BIGINT NULL,
    reason_code VARCHAR(64) NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='post_status_events (shell)';

CREATE TABLE IF NOT EXISTS activity_lifecycle_events (
    id BIGINT NOT NULL PRIMARY KEY,
    activity_id BIGINT NULL,
    from_status TINYINT NULL,
    to_status TINYINT NULL,
    operator_user_id BIGINT NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='activity_lifecycle_events (shell)';

CREATE TABLE IF NOT EXISTS activity_create_idempotency_tombstones (
    id BIGINT NOT NULL PRIMARY KEY,
    operator_user_id BIGINT NULL,
    activity_id BIGINT NULL,
    request_fingerprint CHAR(64) NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='activity_create_idempotency_tombstones (shell)';

CREATE TABLE IF NOT EXISTS user_devices (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    installation_id_hash BINARY(32) NULL,
    device_name VARCHAR(128) NULL,
    platform_code VARCHAR(32) NULL,
    os_version VARCHAR(64) NULL,
    app_version VARCHAR(64) NULL,
    region_code VARCHAR(32) NULL,
    status TINYINT NULL,
    first_seen_at DATETIME(3) NULL,
    last_seen_at DATETIME(3) NULL,
    status_changed_at DATETIME(3) NULL,
    created_at DATETIME(3) NULL,
    updated_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user_devices (shell)';

CREATE TABLE IF NOT EXISTS user_login_sessions (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    device_id BIGINT NULL,
    session_key VARCHAR(128) NULL,
    refresh_token_hash BINARY(32) NULL,
    refresh_token_version INT NULL,
    status TINYINT NULL,
    expires_at DATETIME(3) NULL,
    last_used_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    version INT NULL,
    created_at DATETIME(3) NULL,
    updated_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user_login_sessions (shell)';

CREATE TABLE IF NOT EXISTS organization_members (
    id BIGINT NOT NULL PRIMARY KEY,
    organization_id BIGINT NULL,
    user_id BIGINT NULL,
    role_code TINYINT NULL,
    status TINYINT NULL,
    enabled_at DATETIME(3) NULL,
    disabled_at DATETIME(3) NULL,
    version INT NULL,
    created_at DATETIME(3) NULL,
    updated_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='organization_members (shell)';

-- post_recommendation_index_jobs also dropped, shell it
CREATE TABLE IF NOT EXISTS post_recommendation_index_jobs (
    id BIGINT NOT NULL PRIMARY KEY,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='post_recommendation_index_jobs (shell)';
