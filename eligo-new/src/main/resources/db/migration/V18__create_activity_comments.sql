CREATE TABLE activity_comments (
    id BIGINT NOT NULL COMMENT '评论主键',
    activity_id BIGINT NOT NULL COMMENT '活动主键',
    author_user_id BIGINT NOT NULL COMMENT '作者用户主键',
    parent_comment_id BIGINT NULL COMMENT '顶层父评论主键',
    status TINYINT NOT NULL COMMENT '状态：1有效，2已删除',
    content VARCHAR(500) NULL COMMENT '有效评论正文',
    idempotency_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '作者范围幂等键',
    request_fingerprint CHAR(64) COLLATE ascii_bin NOT NULL COMMENT '规范请求指纹',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    deleted_at DATETIME(3) NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_comment_idempotency(author_user_id, idempotency_key),
    KEY idx_activity_comment_page(activity_id, created_at, id),
    KEY idx_activity_comment_author(author_user_id, status, id),
    CONSTRAINT fk_activity_comment_activity
        FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_comment_author
        FOREIGN KEY(author_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_comment_parent
        FOREIGN KEY(parent_comment_id) REFERENCES activity_comments(id) ON DELETE RESTRICT,
    CONSTRAINT chk_activity_comment_status CHECK(status IN (1, 2)),
    CONSTRAINT chk_activity_comment_state CHECK(
        (status=1 AND content IS NOT NULL AND deleted_at IS NULL)
        OR (status=2 AND content IS NULL AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动公开评论';
