CREATE TABLE activity_create_idempotency_tombstones (
    create_idempotency_scope VARCHAR(96) NOT NULL COMMENT '创建幂等作用域',
    create_idempotency_key VARCHAR(128) NOT NULL COMMENT '创建幂等键',
    create_idempotency_fingerprint CHAR(43) NOT NULL COMMENT '创建请求指纹',
    activity_id BIGINT NOT NULL COMMENT '已删除活动编号',
    deleted_at DATETIME(3) NOT NULL COMMENT '草稿删除时间',
    expires_at DATETIME(3) NOT NULL COMMENT '幂等窗口失效时间',
    PRIMARY KEY(create_idempotency_scope, create_idempotency_key),
    KEY idx_activity_create_tombstone_expires(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='已删除活动草稿创建幂等凭证';

ALTER TABLE activities
    ADD KEY idx_activity_due_end(status, ends_at, id);
