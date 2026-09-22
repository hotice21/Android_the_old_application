ALTER TABLE activities
    ADD COLUMN create_idempotency_scope VARCHAR(96) NULL COMMENT '创建幂等作用域'
        AFTER operator_user_id,
    ADD COLUMN create_idempotency_key VARCHAR(128) NULL COMMENT '创建幂等键'
        AFTER create_idempotency_scope,
    ADD COLUMN create_idempotency_fingerprint CHAR(43) NULL COMMENT '创建请求指纹'
        AFTER create_idempotency_key,
    ADD UNIQUE KEY uk_activity_create_idempotency(
        create_idempotency_scope,
        create_idempotency_key
    );
